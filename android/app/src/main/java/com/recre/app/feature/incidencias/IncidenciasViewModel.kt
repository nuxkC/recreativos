package com.recre.app.feature.incidencias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.entity.EstadoAveriaPendiente
import com.recre.app.core.data.local.entity.EstadoRecaudacionPendiente
import com.recre.app.core.data.repository.AveriaRepository
import com.recre.app.core.data.repository.InventoryRepository
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.AveriaUploadManager
import com.recre.app.core.sync.RecaudacionUploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Una recaudación de la cola que NO se pudo subir (estado 'error'/'fallida'). */
data class RecaudacionIncidenciaUi(
    val id: String,
    /** Para "Rehacer": reabrir la recaudación de esta instalación y recontar. */
    val instalacionId: String,
    val etiqueta: String,
    val importe: String,
    val fecha: Instant,
    val motivo: String,
    val intentos: Int,
    /** `true` si es terminal ('fallida'): reintentar el mismo payload no la arregla. */
    val terminal: Boolean,
)

/** Una avería de la cola que NO se pudo subir (estado 'error'/'fallida'). */
data class AveriaIncidenciaUi(
    val id: String,
    /** Para "Rehacer": reabrir el reporte de avería de esta máquina. */
    val maquinaId: String,
    val etiqueta: String,
    val categoria: String,
    val fecha: Instant,
    val motivo: String,
    val intentos: Int,
    val terminal: Boolean,
)

data class IncidenciasUiState(
    val recaudaciones: List<RecaudacionIncidenciaUi> = emptyList(),
    val averias: List<AveriaIncidenciaUi> = emptyList(),
    /** Pendientes/subiéndose que NO están bloqueadas: se subirán solas (informativo). */
    val enColaCount: Int = 0,
) {
    val sinBloqueadas: Boolean get() = recaudaciones.isEmpty() && averias.isEmpty()
    val vacio: Boolean get() = sinBloqueadas && enColaCount == 0
}

/**
 * Centro de Incidencias del técnico (T-260): unifica en una pantalla TODO lo que
 * no llegó al servidor —recaudaciones y averías bloqueadas (estado 'error'/'fallida')—
 * con acciones honestas según el motivo, más un recuento informativo de lo que sigue
 * "en cola" (se subirá solo). Reúne la lógica que antes vivía dispersa entre el panel
 * de subidas de recaudaciones ([com.recre.app.feature.shell.ErroresSubidaViewModel])
 * y la invisibilidad total de las averías fallidas.
 *
 * Todo reactivo desde Room: la pantalla se actualiza sola al reintentar/descartar o
 * cuando el worker vacía la cola.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncidenciasViewModel
@Inject
constructor(
    sessionRepository: SessionRepository,
    private val recaudacionRepository: RecaudacionRepository,
    private val averiaRepository: AveriaRepository,
    private val inventoryRepository: InventoryRepository,
    private val recaudacionUploadManager: RecaudacionUploadManager,
    private val averiaUploadManager: AveriaUploadManager,
) : ViewModel() {

    private val empresaIdFlow: Flow<String?> =
        sessionRepository.state
            .map { (it as? SessionState.Active)?.empresa?.id }
            .distinctUntilChanged()

    /** Última empresa activa, para las acciones (reintentar fuerza su worker). */
    private var empresaIdActual: String? = null

    init {
        empresaIdFlow.onEach { empresaIdActual = it }.launchIn(viewModelScope)
    }

    private val recaudBloqueadasFlow =
        empresaIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else recaudacionRepository.observarBloqueadas(id)
        }

    private val averiaBloqueadasFlow =
        empresaIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else averiaRepository.observarBloqueadas(id)
        }

    private val recaudContadorFlow =
        empresaIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(0) else recaudacionRepository.observarContadorPendientes(id)
        }

    private val averiaContadorFlow =
        empresaIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(0) else averiaRepository.observarContadorPendientes(id)
        }

    /** Etiqueta máquina·local por instalación, resuelta offline (best-effort). */
    private val etiquetasFlow: Flow<Map<String, String>> =
        recaudBloqueadasFlow.flatMapLatest { filas ->
            val ids = filas.map { it.instalacionId }.distinct()
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    ids.map { id ->
                        inventoryRepository.observarMaquinaPorInstalacion(id).map { m ->
                            id to (m?.let { "${it.numeroSerie} · ${it.localNombre}" } ?: id)
                        }
                    },
                ) { pares -> pares.toMap() }
            }
        }

    private val recaudUiFlow: Flow<List<RecaudacionIncidenciaUi>> =
        combine(recaudBloqueadasFlow, etiquetasFlow) { filas, etiquetas ->
            filas.map { e ->
                RecaudacionIncidenciaUi(
                    id = e.id,
                    instalacionId = e.instalacionId,
                    etiqueta = etiquetas[e.instalacionId] ?: e.instalacionId,
                    importe = e.bruto,
                    fecha = e.fecha,
                    motivo = motivoLegible(e.ultimoError),
                    intentos = e.intentos,
                    terminal = e.estado == EstadoRecaudacionPendiente.FALLIDA,
                )
            }
        }

    private val averiaUiFlow: Flow<List<AveriaIncidenciaUi>> =
        averiaBloqueadasFlow.map { filas ->
            filas.map { e ->
                AveriaIncidenciaUi(
                    id = e.id,
                    maquinaId = e.maquinaId,
                    etiqueta = e.maquinaNumeroSerie,
                    categoria = e.categoria,
                    fecha = e.createdAt,
                    motivo = motivoLegible(e.ultimoError),
                    intentos = e.intentos,
                    terminal = e.estado == EstadoAveriaPendiente.FALLIDA,
                )
            }
        }

    /** En cola = no-enviadas − bloqueadas (lo que sigue pendiente/subiéndose, sin error). */
    private val enColaFlow: Flow<Int> =
        combine(
            recaudContadorFlow,
            averiaContadorFlow,
            recaudBloqueadasFlow,
            averiaBloqueadasFlow,
        ) { rc, ac, rb, ab ->
            (rc - rb.size).coerceAtLeast(0) + (ac - ab.size).coerceAtLeast(0)
        }

    val state: StateFlow<IncidenciasUiState> =
        combine(recaudUiFlow, averiaUiFlow, enColaFlow) { recaud, averias, enCola ->
            IncidenciasUiState(recaudaciones = recaud, averias = averias, enColaCount = enCola)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = IncidenciasUiState(),
        )

    /** Reintenta una recaudación de red: la devuelve a 'pendiente' y fuerza su worker. */
    fun reintentarRecaudacion(id: String) {
        viewModelScope.launch {
            recaudacionRepository.reintentar(id)
            empresaIdActual?.let { recaudacionUploadManager.forzar(it) }
        }
    }

    fun descartarRecaudacion(id: String) {
        viewModelScope.launch { recaudacionRepository.descartar(id) }
    }

    /** Reintenta una avería de red: la devuelve a 'pendiente' y reencola su worker. */
    fun reintentarAveria(id: String) {
        viewModelScope.launch {
            averiaRepository.reintentar(id)
            empresaIdActual?.let { averiaUploadManager.encolar(it) }
        }
    }

    fun descartarAveria(id: String) {
        viewModelScope.launch { averiaRepository.descartar(id) }
    }

    /**
     * `ultimo_error` suele guardar el mensaje legible, pero filas antiguas (o las
     * averías, que guardan el `code` i18n) pueden traer texto técnico o el JSON
     * crudo `{"error":{"message":"…"}}`. Extraemos el `message` si lo hay y
     * recortamos la cola técnica; si no hay nada usable, un texto por defecto.
     */
    private fun motivoLegible(raw: String?): String {
        if (raw.isNullOrBlank()) return "No se pudo subir por un error desconocido."
        val marca = "\"message\":\""
        val i = raw.indexOf(marca)
        if (i < 0) return raw.substringBefore("\nURL:").trim().ifBlank {
            "No se pudo subir por un error desconocido."
        }
        val desde = i + marca.length
        val hasta = raw.indexOf('"', desde)
        return if (hasta > desde) raw.substring(desde, hasta) else raw
    }
}
