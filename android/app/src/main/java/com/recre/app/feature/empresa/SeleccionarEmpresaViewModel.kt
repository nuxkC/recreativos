package com.recre.app.feature.empresa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.session.Membresia
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla "Seleccionar empresa".
 */
data class SeleccionarEmpresaUiState(
    val seleccionando: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class SeleccionarEmpresaViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeleccionarEmpresaUiState())
    val uiState: StateFlow<SeleccionarEmpresaUiState> = _uiState.asStateFlow()

    /**
     * Lista de membresías observadas desde la sesión. Nunca debería estar
     * vacía cuando esta pantalla está visible (el NavHost solo navega
     * aquí cuando hay [SessionState.NeedsEmpresaSelection] o [SessionState.Active]).
     */
    val membresias: StateFlow<List<Membresia>> = sessionRepository.state
        .map { state ->
            when (state) {
                is SessionState.NeedsEmpresaSelection -> state.membresias
                is SessionState.Active -> state.membresias
                else -> emptyList()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Auto-selecciona si solo hay una membresía. Llamar desde un
     * `LaunchedEffect` cuando la pantalla se abre.
     */
    fun autoseleccionarSiSoloUna() {
        viewModelScope.launch {
            val list = membresias.value
            if (list.size == 1) {
                seleccionar(list[0].empresa.id)
            }
        }
    }

    fun seleccionar(empresaId: String) {
        if (_uiState.value.seleccionando != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(seleccionando = empresaId, errorMessage = null) }
            val ok = sessionRepository.seleccionarEmpresa(empresaId)
            if (!ok) {
                _uiState.update {
                    it.copy(seleccionando = null, errorMessage = ERROR_SELECCION)
                }
            }
            // En caso de éxito, SessionState pasa a Active y el NavHost
            // navegará automáticamente; no hace falta limpiar el estado.
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun cerrarSesion() {
        viewModelScope.launch { sessionRepository.cerrarSesion() }
    }

    companion object {
        const val ERROR_SELECCION = "empresa_seleccion_error"
    }
}
