package com.recre.app.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.AlertasRepository
import com.recre.app.core.data.repository.AveriaRepository
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.sync.SyncStatus
import com.recre.app.core.util.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del centro de alertas / top bar global de pulgar (T-234 · T-236).
 *
 * El badge de la campana agrega TODO lo que requiere atención del técnico:
 *  - [alertasBackend]: alertas in-app del servidor. Incluye los **descuadres**
 *    (que llegan como alertas de tipo conflicto), además de licencias por
 *    caducar, locales sin recaudar, recaudaciones anuladas, etc.
 *  - [pendientesSync]: elementos creados offline aún sin subir (recaudaciones +
 *    averías) = la "sync pendiente".
 *
 * [totalAlertas] (la suma) es lo que pinta el badge de la campana; además
 * [pendientesSync] alimenta el aviso de "sin sincronizar" del centro de alertas.
 */
data class ShellUiState(
    val alertasBackend: Int = 0,
    val pendientesSync: Int = 0,
    val sincronizando: Boolean = false,
) {
    val totalAlertas: Int
        get() = alertasBackend + pendientesSync
}

/**
 * ViewModel del centro de alertas + top bar global. Combina, para la empresa
 * activa, las alertas in-app del backend con los contadores locales de
 * pendientes de subir (recaudaciones + averías) y el estado de sync, sin polling
 * ni realtime: los contadores locales son `Flow` de Room y las alertas se
 * recuentan al volver el foco a la pantalla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShellViewModel
@Inject
constructor(
    private val sessionRepository: SessionRepository,
    private val syncManager: SyncManager,
    private val alertasRepository: AlertasRepository,
    private val recaudacionRepository: RecaudacionRepository,
    private val averiaRepository: AveriaRepository,
) : ViewModel() {

    private val alertasBackendFlow = MutableStateFlow(0)

    private val empresaIdFlow =
        flow {
            sessionRepository.state.collect { state ->
                emit((state as? SessionState.Active)?.empresa?.id)
            }
        }

    private val sincronizandoFlow =
        empresaIdFlow.flatMapLatest { id ->
            if (id == null) {
                flowOf(false)
            } else {
                syncManager.observarEstado(id).map { it is SyncStatus.Running }
            }
        }

    private val pendientesSyncFlow =
        empresaIdFlow.flatMapLatest { id ->
            if (id == null) {
                flowOf(0)
            } else {
                combine(
                    recaudacionRepository.observarContadorPendientes(id),
                    averiaRepository.observarContadorPendientes(id),
                ) { recaudaciones, averias -> recaudaciones + averias }
            }
        }

    val state: StateFlow<ShellUiState> =
        combine(
            alertasBackendFlow,
            pendientesSyncFlow,
            sincronizandoFlow,
        ) { backend, pendientes, sincronizando ->
            ShellUiState(
                alertasBackend = backend,
                pendientesSync = pendientes,
                sincronizando = sincronizando,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ShellUiState(),
            )

    init {
        refrescarAlertas()
    }

    /**
     * Recuenta las alertas in-app del backend (best-effort: si falla la red
     * dejamos el último conteo; el badge no es crítico). La pantalla lo llama en
     * `onResume`. Los pendientes de sync no hace falta recontarlos: son `Flow`.
     */
    fun refrescarAlertas() {
        viewModelScope.launch {
            when (val result = alertasRepository.contarPendientes()) {
                is DomainResult.Success -> alertasBackendFlow.update { result.value.toInt() }
                is DomainResult.Failure -> Unit
            }
        }
    }

    /** Botón ↻ del top bar: fuerza una sync (REPLACE) y recuenta alertas. */
    fun forzarSync() {
        viewModelScope.launch {
            val active = sessionRepository.state.value as? SessionState.Active ?: return@launch
            syncManager.forzarSincronizacion(active.empresa.id)
        }
        refrescarAlertas()
    }
}
