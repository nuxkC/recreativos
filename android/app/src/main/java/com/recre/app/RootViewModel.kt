package com.recre.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel raíz: expone el [SessionState] para que el NavHost decida el
 * destino inicial y reaccione a cambios de sesión / empresa activa.
 *
 * No contiene lógica propia: solo proxifica el `SessionRepository` con un
 * scope ligado al lifecycle de la activity.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    sessionRepository: SessionRepository,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionRepository.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.Loading,
        )
}
