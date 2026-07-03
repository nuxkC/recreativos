package com.recre.app.feature.locales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.auth.ROLES_GESTION
import com.recre.app.core.auth.rolCumple
import com.recre.app.core.data.remote.AgendaRemoteDataSource
import com.recre.app.core.data.repository.AlertasRepository
import com.recre.app.core.data.repository.EstadoAgenda
import com.recre.app.core.data.repository.InventoryRepository
import com.recre.app.core.data.repository.LocalResumen
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.RealtimeManager
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.sync.SyncStatus
import com.recre.app.core.util.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Claves de los filtros de agenda (chips). */
const val FILTRO_PENDIENTES = "pendientes"
const val FILTRO_AL_DIA = "al_dia"

/** Un local del home con su estado de agenda (P3c). */
data class LocalAgendaItem(
    val local: LocalResumen,
    val estado: EstadoAgenda,
)

/** Orden de presentación: lo más urgente primero. */
private fun ordenEstado(estado: EstadoAgenda): Int = when (estado) {
    EstadoAgenda.ATRASADO -> 0
    EstadoAgenda.TOCA_HOY -> 1
    EstadoAgenda.PENDIENTE -> 2
    EstadoAgenda.AL_DIA -> 3
    EstadoAgenda.SIN_PLANIFICAR -> 4
}

/**
 * Estado de la pantalla de "Locales" (home efectiva tras T-52).
 *
 * - `locales` viene siempre de la cache local (Room): la lista se ve aun
 *   sin red.
 * - `cargandoSync` muestra el indicador de pull-to-refresh mientras hay
 *   un Work de sync en marcha.
 * - `query` es la búsqueda client-side (matchea contra nombre y direccion).
 * - `empresaNombre` y `multipleEmpresas` alimentan el TopAppBar y el
 *   menú overflow.
 * - `syncStale` (T-59): banner rojo "lleva > 48 h sin sincronizar".
 * - `pendientes` (T-57): contador de recaudaciones offline en cola.
 * - `alertasPendientes` (T-64): contador de alertas in-app sin leer.
 *   Se refresca on-resume y al pulsar "Sincronizar".
 * - `tieneRolGestion` (T-66..T-69): indica si el usuario es gestor+ y
 *   por tanto debe ver la entrada "Gestión" en el menú overflow.
 * - `agenda` / `agendaDisponible` / `filtro` (Planificación P3c): estado de
 *   recaudación por local (vista `v_agenda_operario`). Solo se muestra el héroe,
 *   los chips de estado y los filtros cuando la agenda se cargó (online); offline
 *   el home cae al comportamiento de lista plana.
 */
data class LocalesUiState(
    val empresaNombre: String = "",
    val multipleEmpresas: Boolean = false,
    val locales: List<LocalResumen> = emptyList(),
    val query: String = "",
    val cargandoSync: Boolean = false,
    val ultimaSync: Instant? = null,
    val syncStale: Boolean = false,
    val pendientes: Int = 0,
    val alertasPendientes: Int = 0,
    val tieneRolGestion: Boolean = false,
    val agenda: Map<String, EstadoAgenda> = emptyMap(),
    val agendaDisponible: Boolean = false,
    val filtro: Set<String> = emptySet(),
) {
    /** Nº de locales pendientes (toca hoy o atrasado) del operario → héroe. */
    val localesPendientes: Int
        get() = agenda.values.count { it.esPendiente }

    /** Nº de locales al día del operario (para el contador del chip). */
    val localesAlDia: Int
        get() = agenda.values.count { it == EstadoAgenda.AL_DIA }

    private val localesPorTexto: List<LocalResumen>
        get() = if (query.isBlank()) {
            locales
        } else {
            val needle = query.trim().lowercase()
            locales.filter { local ->
                local.nombre.lowercase().contains(needle) ||
                    local.calle?.lowercase()?.contains(needle) == true
            }
        }

    @Deprecated("Usa itemsVisibles", ReplaceWith("itemsVisibles"))
    val localesFiltrados: List<LocalResumen>
        get() = localesPorTexto

    /**
     * Lista a pintar: filtro de texto + filtro de chips + orden por urgencia.
     * Cada local lleva su estado (SIN_PLANIFICAR si la agenda no lo cubre o no
     * está disponible).
     *
     * Online, el filtro por defecto es "Pendientes" (ver [filtroFlow]): solo
     * sale lo que toca esta semana (pendiente/toca_hoy/atrasado); las que no
     * tocan ("al día") se ven activando su chip o limpiando el filtro. Offline
     * (sin agenda) no se oculta nada: lista plana de todos los locales.
     */
    val itemsVisibles: List<LocalAgendaItem>
        get() {
            val conEstado = localesPorTexto.map { local ->
                LocalAgendaItem(local, agenda[local.id] ?: EstadoAgenda.SIN_PLANIFICAR)
            }
            val porFiltro = if (!agendaDisponible || filtro.isEmpty()) {
                conEstado
            } else {
                conEstado.filter { item ->
                    (FILTRO_PENDIENTES in filtro && item.estado.esPendiente) ||
                        (FILTRO_AL_DIA in filtro && item.estado == EstadoAgenda.AL_DIA)
                }
            }
            return porFiltro.sortedWith(
                compareBy({ ordenEstado(it.estado) }, { it.local.nombre.lowercase() }),
            )
        }
}

/** Snapshot interno de los flujos de sync, para no superar el límite de combine de 5 args. */
private data class SyncSnapshot(
    val status: SyncStatus,
    val ultimaSync: Instant?,
    val stale: Boolean,
    val pendientes: Int,
)

/** Snapshot de los 5 flujos base (sin agenda ni filtro), para anidar combines. */
private data class BaseSnapshot(
    val sessionState: SessionState,
    val locales: List<LocalResumen>,
    val query: String,
    val sync: SyncSnapshot,
    val alertasPendientes: Int,
)

/** Resultado de cargar la agenda: si está disponible (online) y los estados. */
private data class AgendaCarga(
    val disponible: Boolean,
    val estados: Map<String, EstadoAgenda>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalesViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val syncManager: SyncManager,
    private val sessionRepository: SessionRepository,
    private val recaudacionRepository: RecaudacionRepository,
    private val alertasRepository: AlertasRepository,
    private val agendaRemoteDataSource: AgendaRemoteDataSource,
    private val realtimeManager: RealtimeManager,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val alertasPendientesFlow = MutableStateFlow(0)
    // Por defecto solo "Pendientes": el home arranca como cola de trabajo de la
    // semana (lo que toca), no como inventario completo. "Al día" se ve con su
    // chip o limpiando el filtro; offline se ignora (lista plana, ver itemsVisibles).
    private val filtroFlow = MutableStateFlow<Set<String>>(setOf(FILTRO_PENDIENTES))

    private val empresaIdFlow = flow {
        sessionRepository.state.collect { state ->
            emit((state as? SessionState.Active)?.empresa?.id)
        }
    }

    private val syncStatusFlow = empresaIdFlow.flatMapLatest { id ->
        if (id == null) flowOf<SyncStatus>(SyncStatus.Idle) else syncManager.observarEstado(id)
    }

    private val ultimaSyncFlow = empresaIdFlow.flatMapLatest { id ->
        if (id == null) flowOf<Instant?>(null) else syncManager.observarUltimaSync(id)
    }

    private val syncStaleFlow = empresaIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(false) else syncManager.observarSyncStale(id)
    }

    private val pendientesFlow = empresaIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(0) else recaudacionRepository.observarContadorPendientes(id)
    }

    /**
     * Agrupamos los 4 flujos de sync en un snapshot para no superar el
     * límite de 5 args del overload tipado de `combine`.
     */
    private val syncSnapshotFlow = combine(
        syncStatusFlow,
        ultimaSyncFlow,
        syncStaleFlow,
        pendientesFlow,
    ) { status, ultima, stale, pendientes ->
        SyncSnapshot(status, ultima, stale, pendientes)
    }

    /**
     * Agenda (P3c): se trae online de `v_agenda_operario`. Se recalcula al
     * cambiar de empresa y cuando cambia el estado de sync (tras un sync, los
     * locales/recaudaciones pueden haber cambiado). Si falla la red →
     * `disponible=false` y el home cae a la lista plana.
     */
    private val agendaFlow: Flow<AgendaCarga> =
        combine(empresaIdFlow, syncStatusFlow) { id, _ -> id }
            .flatMapLatest { id ->
                flow {
                    if (id == null) {
                        emit(AgendaCarga(disponible = false, estados = emptyMap()))
                    } else {
                        val estados = runCatching { agendaRemoteDataSource.obtenerEstados(id) }.getOrNull()
                        emit(
                            if (estados == null) AgendaCarga(false, emptyMap())
                            else AgendaCarga(true, estados),
                        )
                    }
                }
            }

    private val baseFlow: Flow<BaseSnapshot> = combine(
        sessionRepository.state,
        inventoryRepository.observarLocalesResumen(),
        queryFlow,
        syncSnapshotFlow,
        alertasPendientesFlow,
    ) { sessionState, locales, query, sync, alertasPendientes ->
        BaseSnapshot(sessionState, locales, query, sync, alertasPendientes)
    }

    val state: StateFlow<LocalesUiState> = combine(
        baseFlow,
        agendaFlow,
        filtroFlow,
    ) { base, agenda, filtro ->
        val active = base.sessionState as? SessionState.Active
        LocalesUiState(
            empresaNombre = active?.empresa?.nombre.orEmpty(),
            multipleEmpresas = (active?.membresias?.size ?: 0) > 1,
            locales = base.locales,
            query = base.query,
            cargandoSync = base.sync.status is SyncStatus.Running,
            ultimaSync = base.sync.ultimaSync,
            syncStale = base.sync.stale,
            pendientes = base.sync.pendientes,
            alertasPendientes = base.alertasPendientes,
            tieneRolGestion = rolCumple(active?.membresia?.rol, ROLES_GESTION),
            agenda = agenda.estados,
            agendaDisponible = agenda.disponible,
            filtro = filtro,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LocalesUiState(),
        )

    init {
        // Conteo inicial de alertas pendientes; se refresca también en
        // `onResume` ([refrescarAlertas]) y en vivo vía realtime (abajo).
        refrescarAlertas()
        // Realtime: el badge de alertas reacciona a cambios server-side.
        viewModelScope.launch {
            realtimeManager.revision.drop(1).collect { refrescarAlertas() }
        }
    }

    fun onQueryChange(value: String) {
        queryFlow.update { value }
    }

    /** Activa/desactiva un chip de filtro de agenda (pendientes / al día). */
    fun onFiltroToggle(key: String, activo: Boolean) {
        filtroFlow.update { actual -> if (activo) actual + key else actual - key }
    }

    /** Quita todos los filtros (vuelve a "todos"). */
    fun onFiltroClear() {
        filtroFlow.update { emptySet() }
    }

    /**
     * Pull-to-refresh y "Sincronizar ahora": dispara una sincronización
     * forzada que reemplaza cualquier sync en curso (REPLACE) y también
     * recuenta las alertas pendientes (T-64). La agenda se recalcula sola al
     * cambiar el estado de sync.
     */
    fun refrescar() {
        viewModelScope.launch {
            val active = sessionRepository.state.value as? SessionState.Active ?: return@launch
            syncManager.forzarSincronizacion(active.empresa.id)
        }
        refrescarAlertas()
    }

    /**
     * Pide al backend el conteo de alertas pendientes para la empresa
     * activa. Llamada por la pantalla en `onResume` para refrescar el
     * badge del menú overflow al volver de las pantallas de Alertas /
     * Histórico / etc.
     */
    fun refrescarAlertas() {
        viewModelScope.launch {
            when (val result = alertasRepository.contarPendientes()) {
                is DomainResult.Success ->
                    alertasPendientesFlow.update { result.value.toInt() }

                is DomainResult.Failure -> {
                    // Best-effort: si falla la red, dejamos el último
                    // conteo conocido. El badge no es crítico.
                }
            }
        }
    }

    /** Limpia la empresa activa para volver a la pantalla de selección. */
    fun cambiarEmpresa() {
        viewModelScope.launch { sessionRepository.limpiarEmpresaActiva() }
    }

    fun cerrarSesion() {
        viewModelScope.launch { sessionRepository.cerrarSesion() }
    }
}
