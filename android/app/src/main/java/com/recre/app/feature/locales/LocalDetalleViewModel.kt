package com.recre.app.feature.locales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.InventoryRepository
import com.recre.app.core.data.repository.LocalDetalle
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de detalle de un local.
 *
 * - `detalle` es `null` mientras se carga; tras el primer collect del
 *   Flow puede pasar a `null` permanentemente si el local no existe en
 *   la cache (la pantalla muestra entonces "no encontrado").
 * - `cargandoSync` controla el indicador de pull-to-refresh.
 * - `cargado` distingue "todavía no he visto la cache" de "ya la vi y no
 *   está", para no mostrar empty state durante el primer frame.
 * - `syncStale` (T-59): si lleva > 48 h sin sync exitoso, se muestra un
 *   banner que bloquea la opción de recaudar.
 */
data class LocalDetalleUiState(
    val cargado: Boolean = false,
    val detalle: LocalDetalle? = null,
    val cargandoSync: Boolean = false,
    val syncStale: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalDetalleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inventoryRepository: InventoryRepository,
    private val syncManager: SyncManager,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val localId: String = checkNotNull(savedStateHandle[ARG_LOCAL_ID]) {
        "Falta argumento '$ARG_LOCAL_ID' al abrir LocalDetalleScreen"
    }

    private val empresaIdFlow = kotlinx.coroutines.flow.flow {
        sessionRepository.state.collect { state ->
            emit((state as? SessionState.Active)?.empresa?.id)
        }
    }

    private val syncStatusFlow = empresaIdFlow.flatMapLatest { id ->
        if (id == null) flowOf<SyncStatus>(SyncStatus.Idle) else syncManager.observarEstado(id)
    }

    private val syncStaleFlow = empresaIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(false) else syncManager.observarSyncStale(id)
    }

    val state: StateFlow<LocalDetalleUiState> = combine(
        inventoryRepository.observarLocalDetalle(localId),
        syncStatusFlow,
        syncStaleFlow,
    ) { detalle, syncStatus, stale ->
        LocalDetalleUiState(
            cargado = true,
            detalle = detalle,
            cargandoSync = syncStatus is SyncStatus.Running,
            syncStale = stale,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LocalDetalleUiState(),
        )

    fun refrescar() {
        viewModelScope.launch {
            val active = sessionRepository.state.value as? SessionState.Active ?: return@launch
            syncManager.forzarSincronizacion(active.empresa.id)
        }
    }

    companion object {
        const val ARG_LOCAL_ID = "localId"
    }
}
