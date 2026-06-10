package com.recre.app.feature.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.auth.ROLES_GESTION
import com.recre.app.core.auth.rolCumple
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.ConnectivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GestionUiState(
    val tieneRol: Boolean = false,
    val online: Boolean = true,
)

/**
 * ViewModel del hub de Gestión (T-66..T-69).
 *
 * - [tieneRol]: si el usuario es gestor+ pinta las 4 entradas.
 * - [online] (T-70): refleja la conectividad. El hub permite navegar
 *   incluso sin red para que el técnico vea las listas (que se sirven
 *   desde la cache Room), pero las pantallas de detalle muestran el
 *   banner "sin conexión" y deshabilitan las mutaciones.
 */
@HiltViewModel
class GestionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GestionUiState())
    val state: StateFlow<GestionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { session ->
                val rol = (session as? SessionState.Active)?.membresia?.rol
                _state.update { it.copy(tieneRol = rolCumple(rol, ROLES_GESTION)) }
            }
        }
        viewModelScope.launch {
            connectivityRepository.online.collectLatest { online ->
                _state.update { it.copy(online = online) }
            }
        }
    }
}
