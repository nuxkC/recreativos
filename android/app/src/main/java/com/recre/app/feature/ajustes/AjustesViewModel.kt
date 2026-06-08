package com.recre.app.feature.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.AuthRepository
import com.recre.app.core.printer.PrinterDevice
import com.recre.app.core.printer.PrinterRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state de la pantalla de Ajustes (T-65).
 *
 * Agrega información de cuenta + sync + impresora + número de empresas
 * disponibles para que el usuario pueda actuar sobre cada eje desde un
 * solo sitio.
 */
data class AjustesUiState(
    val emailUsuario: String? = null,
    val empresaNombre: String = "",
    val rolEnEmpresa: String = "",
    val multipleEmpresas: Boolean = false,
    val ultimaSync: Instant? = null,
    val sincronizando: Boolean = false,
    val syncStale: Boolean = false,
    val impresoraSeleccionada: PrinterDevice? = null,
)

/**
 * ViewModel de Ajustes (T-65).
 *
 * Solo orquestación: las acciones reales viven en cada repositorio. La
 * pantalla solo navega o llama a métodos suspendidos sin lógica.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AjustesViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
    private val printerRepository: PrinterRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AjustesUiState())
    val state: StateFlow<AjustesUiState> = _state.asStateFlow()

    init {
        // Email del usuario autenticado, una sola vez al abrir.
        _state.update { it.copy(emailUsuario = authRepository.currentUserEmail()) }

        // Sesión + membresía activa.
        viewModelScope.launch {
            sessionRepository.state.collect { sessionState ->
                if (sessionState is SessionState.Active) {
                    _state.update {
                        it.copy(
                            empresaNombre = sessionState.membresia.empresa.nombre,
                            rolEnEmpresa = sessionState.membresia.rol.name,
                            multipleEmpresas = sessionState.membresias.size > 1,
                        )
                    }
                }
            }
        }

        // Estado de sync (idle/running) y última sync por empresa activa.
        val empresaIdFlow = sessionRepository.state.map { state ->
            (state as? SessionState.Active)?.empresa?.id
        }
        viewModelScope.launch {
            empresaIdFlow.flatMapLatest { id ->
                if (id == null) flowOf(SyncStatus.Idle) else syncManager.observarEstado(id)
            }.collect { status ->
                _state.update { it.copy(sincronizando = status is SyncStatus.Running) }
            }
        }
        viewModelScope.launch {
            empresaIdFlow.flatMapLatest { id ->
                if (id == null) flowOf(null) else syncManager.observarUltimaSync(id)
            }.collect { ultima ->
                _state.update { it.copy(ultimaSync = ultima) }
            }
        }
        viewModelScope.launch {
            empresaIdFlow.flatMapLatest { id ->
                if (id == null) flowOf(false) else syncManager.observarSyncStale(id)
            }.collect { stale ->
                _state.update { it.copy(syncStale = stale) }
            }
        }

        // Impresora vinculada (T-62).
        viewModelScope.launch {
            printerRepository.seleccionadaFlow.collect { device ->
                _state.update { it.copy(impresoraSeleccionada = device) }
            }
        }
    }

    fun forzarSync() {
        val empresaId = (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
            ?: return
        syncManager.forzarSincronizacion(empresaId)
    }

    fun cerrarSesion() {
        viewModelScope.launch {
            sessionRepository.cerrarSesion()
        }
    }

    fun cambiarEmpresa() {
        viewModelScope.launch {
            sessionRepository.limpiarEmpresaActiva()
        }
    }
}
