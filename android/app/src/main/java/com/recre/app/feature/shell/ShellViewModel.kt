package com.recre.app.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.AlertasRepository
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
 * Estado de la app shell de pulgar (T-234): los dos elementos GLOBALES del top
 * bar —el contador de la campana de alertas y si hay una sincronización en
 * curso— compartidos por las 4 pestañas (Locales · Histórico · Gestión ·
 * Ajustes). Se alimenta de los mismos repos que `LocalesViewModel` para que el
 * badge y el botón ↻ sean coherentes en toda la app, sin polling ni realtime.
 */
data class ShellUiState(
    val alertasPendientes: Int = 0,
    val sincronizando: Boolean = false,
)

/**
 * ViewModel del top bar global. La campana cuenta de momento solo las alertas
 * in-app pendientes (`AlertasRepository.contarPendientes`); T-236 lo amplía a un
 * centro de alertas que además combina averías y recaudaciones pendientes de
 * subir y enruta por tipo de alerta.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShellViewModel
@Inject
constructor(
    private val sessionRepository: SessionRepository,
    private val syncManager: SyncManager,
    private val alertasRepository: AlertasRepository,
) : ViewModel() {

    private val alertasPendientesFlow = MutableStateFlow(0)

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

    val state: StateFlow<ShellUiState> =
        combine(alertasPendientesFlow, sincronizandoFlow) { alertas, sincronizando ->
            ShellUiState(alertasPendientes = alertas, sincronizando = sincronizando)
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
     * Recuenta las alertas pendientes (best-effort: si falla la red dejamos el
     * último conteo; el badge no es crítico). La pantalla lo llama en `onResume`.
     */
    fun refrescarAlertas() {
        viewModelScope.launch {
            when (val result = alertasRepository.contarPendientes()) {
                is DomainResult.Success -> alertasPendientesFlow.update { result.value.toInt() }
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
