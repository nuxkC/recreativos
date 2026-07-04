package com.recre.app.feature.empresa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * VM de la pantalla de error de sesión ([com.recre.app.core.session.SessionState.LoadError]):
 * relanza la carga de membresías. No navega: si el reintento tiene éxito, el
 * SessionState cambia y navigateForState saca de aquí solo.
 */
@HiltViewModel
class ErrorSesionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _reintentando = MutableStateFlow(false)
    val reintentando: StateFlow<Boolean> = _reintentando

    fun reintentar() {
        if (_reintentando.value) return
        viewModelScope.launch {
            _reintentando.value = true
            sessionRepository.refreshMembresias()
            _reintentando.value = false
        }
    }

    fun cerrarSesion() {
        viewModelScope.launch { sessionRepository.cerrarSesion() }
    }
}
