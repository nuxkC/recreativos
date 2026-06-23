package com.recre.app

import androidx.lifecycle.ViewModel
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel raíz: expone el [SessionState] para que el NavHost decida el
 * destino inicial y reaccione a cambios de sesión / empresa activa.
 *
 * No contiene lógica propia: solo proxifica el `SessionRepository`.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    sessionRepository: SessionRepository,
) : ViewModel() {

    /**
     * Estado de sesión para el NavHost.
     *
     * Exponemos DIRECTAMENTE el StateFlow del [SessionRepository] —un @Singleton
     * que comparte su estado `Eagerly` en un scope de proceso—. Ese flujo ya está
     * "caliente" y resuelto: tras el login queda en `Active` de forma estable y
     * sobrevive a recreaciones de la Activity (el singleton no se recrea).
     *
     * Antes lo envolvíamos en un segundo `stateIn(WhileSubscribed, initialValue =
     * Loading)`. Eso reintroducía un `Loading` espurio en cada recreación/resume
     * que volvía a disparar `navigateForState` y reseteaba la navegación a la
     * pantalla principal (síntoma: al apagar/encender la pantalla, la app "se
     * recargaba" y volvía a Locales). Leyendo el singleton directamente, en una
     * recreación el estado ya es `Active` desde el primer frame: no hay transición
     * espuria ni ventana de carrera con la restauración del back stack.
     */
    val sessionState: StateFlow<SessionState> = sessionRepository.state
}
