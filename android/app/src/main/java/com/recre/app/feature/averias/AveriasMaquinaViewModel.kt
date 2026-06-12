package com.recre.app.feature.averias

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.R
import com.recre.app.core.data.repository.AveriaHistorial
import com.recre.app.core.data.repository.AveriaRepository
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.ConnectivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del historial de averías de una máquina (T-222), en gestión.
 *
 * Lectura en línea (como el libro mayor de deudas): el historial atraviesa
 * instalaciones (hoja de vida de la máquina). Permite cerrar averías abiertas.
 */
data class AveriasMaquinaUiState(
    val cargando: Boolean = true,
    val online: Boolean = true,
    val averias: List<AveriaHistorial> = emptyList(),
    /** Id de la avería que se está resolviendo (para el spinner inline). */
    val resolviendo: String? = null,
    val errorCode: String? = null,
    @StringRes val mensajeOkRes: Int? = null,
)

@HiltViewModel
class AveriasMaquinaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val averiaRepository: AveriaRepository,
    private val sessionRepository: SessionRepository,
    connectivityRepository: ConnectivityRepository,
) : ViewModel() {

    private val maquinaId: String = checkNotNull(savedStateHandle[ARG_MAQUINA_ID]) {
        "Falta argumento '$ARG_MAQUINA_ID' al abrir AveriasMaquinaScreen"
    }

    private val _state = MutableStateFlow(AveriasMaquinaUiState())
    val state: StateFlow<AveriasMaquinaUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityRepository.online.collect { online ->
                _state.update { it.copy(online = online) }
            }
        }
        cargar()
    }

    fun cargar() {
        val empresaId = empresaId() ?: run {
            _state.update { it.copy(cargando = false, errorCode = "auth") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(cargando = true, errorCode = null) }
            when (val r = averiaRepository.historial(empresaId, maquinaId)) {
                is GestionResult.Success ->
                    _state.update { it.copy(cargando = false, averias = r.value) }
                is GestionResult.Failure ->
                    _state.update { it.copy(cargando = false, errorCode = r.code) }
            }
        }
    }

    fun resolver(averiaId: String, notas: String?) {
        if (!_state.value.online) {
            _state.update { it.copy(errorCode = "network") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(resolviendo = averiaId, errorCode = null) }
            when (val r = averiaRepository.resolver(averiaId, notas)) {
                is GestionResult.Success -> {
                    _state.update {
                        it.copy(resolviendo = null, mensajeOkRes = R.string.averia_resuelta_ok)
                    }
                    cargar()
                }
                is GestionResult.Failure ->
                    _state.update { it.copy(resolviendo = null, errorCode = r.code) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorCode = null) }
    fun consumeMensaje() = _state.update { it.copy(mensajeOkRes = null) }

    private fun empresaId(): String? =
        (sessionRepository.state.value as? SessionState.Active)?.empresa?.id

    companion object {
        const val ARG_MAQUINA_ID = "maquinaId"
    }
}
