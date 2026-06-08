package com.recre.app.feature.empresa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * VM minimalista de la pantalla "sin acceso": solo expone el cierre de
 * sesión para que el usuario pueda volver al login y entrar con otra
 * cuenta o esperar a que el admin le invite.
 */
@HiltViewModel
class SinAccesoViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    fun cerrarSesion() {
        viewModelScope.launch { sessionRepository.cerrarSesion() }
    }
}
