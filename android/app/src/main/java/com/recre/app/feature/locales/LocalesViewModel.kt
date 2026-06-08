package com.recre.app.feature.locales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.auth.ROLES_GESTION
import com.recre.app.core.auth.rolCumple
import com.recre.app.core.data.repository.AlertasRepository
import com.recre.app.core.data.repository.InventoryRepository
import com.recre.app.core.data.repository.LocalResumen
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.sync.SyncStatus
import com.recre.app.core.util.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) {
    val localesFiltrados: List<LocalResumen>
        get() = if (query.isBlank()) {
            locales
        } else {
            val needle = query.trim().lowercase()
            locales.filter { local ->
                local.nombre.lowercase().contains(needle) ||
                    local.direccion?.lowercase()?.contains(needle) == true
            }
        }
}

/** Snapshot interno de los flujos de sync, para no superar el límite de combine de 5 args. */
private data class SyncSnapshot(
    val status: SyncStatus,
    val ultimaSync: Instant?,
    val stale: Boolean,
    val pendientes: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalesViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val syncManager: SyncManager,
    private val sessionRepository: SessionRepository,
    private val recaudacionRepository: RecaudacionRepository,
    private val alertasRepository: AlertasRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val alertasPendientesFlow = MutableStateFlow(0)

    private val empresaIdFlow = kotlinx.coroutines.flow.flow {
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

    val state: StateFlow<LocalesUiState> = combine(
        sessionRepository.state,
        inventoryRepository.observarLocalesResumen(),
        queryFlow,
        syncSnapshotFlow,
        alertasPendientesFlow,
    ) { sessionState, locales, query, sync, alertasPendientes ->
        val active = sessionState as? SessionState.Active
        LocalesUiState(
            empresaNombre = active?.empresa?.nombre.orEmpty(),
            multipleEmpresas = (active?.membresias?.size ?: 0) > 1,
            locales = locales,
            query = query,
            cargandoSync = sync.status is SyncStatus.Running,
            ultimaSync = sync.ultimaSync,
            syncStale = sync.stale,
            pendientes = sync.pendientes,
            alertasPendientes = alertasPendientes,
            tieneRolGestion = rolCumple(active?.membresia?.rol, ROLES_GESTION),
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LocalesUiState(),
        )

    init {
        // Conteo inicial de alertas pendientes. La pantalla pedirá un
        // refresco adicional en `onResume` con [refrescarAlertas] para
        // que el badge se mantenga al día sin tener que polling ni
        // realtime.
        refrescarAlertas()
    }

    fun onQueryChange(value: String) {
        queryFlow.update { value }
    }

    /**
     * Pull-to-refresh y "Sincronizar ahora": dispara una sincronización
     * forzada que reemplaza cualquier sync en curso (REPLACE) y también
     * recuenta las alertas pendientes (T-64).
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
