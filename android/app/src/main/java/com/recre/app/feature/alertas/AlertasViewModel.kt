package com.recre.app.feature.alertas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.Alerta
import com.recre.app.core.data.repository.AlertasRepository
import com.recre.app.core.sync.RealtimeManager
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state de "Alertas" (T-64).
 */
data class AlertasUiState(
    val cargando: Boolean = false,
    val alertas: List<Alerta> = emptyList(),
    val error: AlertasErrorCode? = null,
) {
    /** Nº de alertas sin leer; alimenta el contador de la pantalla (N8). */
    val sinLeer: Int get() = alertas.count { !it.leida }
}

enum class AlertasErrorCode { Network, Auth, Unknown }

@HiltViewModel
class AlertasViewModel @Inject constructor(
    private val repository: AlertasRepository,
    private val realtimeManager: RealtimeManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AlertasUiState())
    val state: StateFlow<AlertasUiState> = _state.asStateFlow()

    init {
        cargar()
        // Realtime: ante cualquier cambio server-side (alerta…) recargamos.
        viewModelScope.launch {
            realtimeManager.revision.drop(1).collect { cargar() }
        }
    }

    fun refrescar() = cargar()

    /**
     * Marca como leída en local + dispara la mutación remota. La alerta NO
     * se borra: se deja `leida=true` (atenuada) dentro de la ventana de
     * leídas recientes. Si el backend falla, revertimos a `leida=false`
     * para que el técnico no pierda la señal (N8 D.3-5).
     */
    fun marcarLeida(alertaId: String) {
        _state.update { current ->
            current.copy(
                alertas = current.alertas.map {
                    if (it.id == alertaId) it.copy(leida = true) else it
                },
            )
        }
        viewModelScope.launch {
            val result = repository.marcarLeida(alertaId)
            if (result is DomainResult.Failure) {
                _state.update { current ->
                    current.copy(
                        alertas = current.alertas.map {
                            if (it.id == alertaId) it.copy(leida = false) else it
                        },
                        error = mapError(result.error),
                    )
                }
            }
        }
    }

    fun marcarTodasLeidas() {
        val previas = _state.value.alertas
        _state.update { current ->
            current.copy(alertas = current.alertas.map { it.copy(leida = true) })
        }
        viewModelScope.launch {
            val result = repository.marcarTodasLeidas()
            if (result is DomainResult.Failure) {
                _state.update {
                    it.copy(alertas = previas, error = mapError(result.error))
                }
            }
        }
    }

    fun limpiarError() {
        _state.update { it.copy(error = null) }
    }

    private fun cargar() {
        viewModelScope.launch {
            _state.update { it.copy(cargando = true, error = null) }
            when (val result = repository.listarPendientes()) {
                is DomainResult.Success ->
                    _state.update { it.copy(cargando = false, alertas = result.value) }

                is DomainResult.Failure ->
                    _state.update {
                        it.copy(cargando = false, error = mapError(result.error))
                    }
            }
        }
    }

    private fun mapError(error: DomainError): AlertasErrorCode = when (error) {
        is DomainError.Network -> AlertasErrorCode.Network
        is DomainError.Auth -> AlertasErrorCode.Auth
        else -> AlertasErrorCode.Unknown
    }
}
