package com.recre.app.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.entity.EstadoRecaudacionPendiente
import com.recre.app.core.data.repository.InventoryRepository
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.RecaudacionUploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una recaudación de la cola BLOQUEADA (estado 'error'/'fallida'), para el panel. */
data class RecaudacionBloqueadaUi(
    val id: String,
    val etiqueta: String,
    val importe: String,
    val fecha: Instant,
    val motivo: String,
    val intentos: Int,
    /** `true` si es terminal ('fallida'): reintentar el mismo payload no la arregla. */
    val terminal: Boolean,
)

data class ColaSubidaUiState(
    val bloqueadas: List<RecaudacionBloqueadaUi> = emptyList(),
    val visible: Boolean = false,
)

/**
 * Panel de subidas BLOQUEADAS (T-63 · mejora C). Vigila la cola de la empresa
 * activa y expone las recaudaciones que NO se pudieron subir (estado 'error' o
 * 'fallida'), con su motivo legible y etiqueta de máquina·local, para que la raíz
 * de la app ([com.recre.app.RecreApp]) muestre un panel central donde el técnico
 * Reintente o Descarte cada una.
 *
 * Vive a nivel de NavHost (no por pestaña) para que el "ya lo he visto" no se
 * duplique. El reconocimiento es **en memoria**: si el proceso se reinicia y
 * siguen bloqueadas, el panel reaparece —es un recordatorio hasta resolverlas—.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ErroresSubidaViewModel
@Inject
constructor(
    sessionRepository: SessionRepository,
    private val recaudacionRepository: RecaudacionRepository,
    private val inventoryRepository: InventoryRepository,
    private val uploadManager: RecaudacionUploadManager,
) : ViewModel() {

    /** ids ya vistos/cerrados en esta sesión de proceso: no reabrir salvo nueva. */
    private val reconocidos = MutableStateFlow<Set<String>>(emptySet())

    private val empresaIdFlow: Flow<String?> =
        sessionRepository.state
            .map { (it as? SessionState.Active)?.empresa?.id }
            .distinctUntilChanged()

    /** Última empresa activa, para las acciones (reintentar fuerza su worker). */
    private var empresaIdActual: String? = null

    init {
        empresaIdFlow.onEach { empresaIdActual = it }.launchIn(viewModelScope)
    }

    private val bloqueadasFlow =
        empresaIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else recaudacionRepository.observarBloqueadas(id)
        }

    /** Etiqueta máquina·local por instalación, resuelta offline (best-effort). */
    private val etiquetasFlow: Flow<Map<String, String>> =
        bloqueadasFlow.flatMapLatest { filas ->
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

    val state: StateFlow<ColaSubidaUiState> =
        combine(bloqueadasFlow, etiquetasFlow, reconocidos) { filas, etiquetas, vistos ->
            ColaSubidaUiState(
                bloqueadas = filas.map { e ->
                    RecaudacionBloqueadaUi(
                        id = e.id,
                        etiqueta = etiquetas[e.instalacionId] ?: e.instalacionId,
                        importe = e.bruto,
                        fecha = e.fecha,
                        motivo = e.ultimoError?.let(::limpiarMotivo)?.takeIf { it.isNotBlank() }
                            ?: "No se pudo subir por un error desconocido.",
                        intentos = e.intentos,
                        terminal = e.estado == EstadoRecaudacionPendiente.FALLIDA,
                    )
                },
                visible = filas.any { it.id !in vistos },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ColaSubidaUiState(),
        )

    /** Cierra el panel: reconoce las visibles ahora (no reabrir hasta una nueva). */
    fun cerrar() {
        reconocidos.update { vistos -> vistos + state.value.bloqueadas.map { it.id } }
    }

    /** Reintenta: devuelve la fila a 'pendiente' y fuerza el worker de subida. */
    fun reintentar(id: String) {
        viewModelScope.launch {
            recaudacionRepository.reintentar(id)
            empresaIdActual?.let { uploadManager.forzar(it) }
        }
    }

    /** Descarta definitivamente una recaudación bloqueada de la cola. */
    fun descartar(id: String) {
        viewModelScope.launch { recaudacionRepository.descartar(id) }
    }

    /**
     * `ultimo_error` guarda ya el mensaje legible, pero filas antiguas pudieron
     * guardar el JSON crudo (`{"error":{"message":"…"}} \nURL: …`). Si lo
     * detectamos extraemos el `message`; si no, recortamos la cola técnica.
     */
    private fun limpiarMotivo(raw: String): String {
        val marca = "\"message\":\""
        val i = raw.indexOf(marca)
        if (i < 0) return raw.substringBefore("\nURL:").trim()
        val desde = i + marca.length
        val hasta = raw.indexOf('"', desde)
        return if (hasta > desde) raw.substring(desde, hasta) else raw
    }
}
