package com.recre.app.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Una recaudación de la cola que el backend rechazó, lista para el aviso. */
data class RecaudacionErrorUi(
    val id: String,
    val motivo: String,
    val fecha: Instant,
)

/**
 * Vigila la cola de recaudaciones de la empresa activa y expone el ÚLTIMO
 * rechazo del backend (estado='error') que el técnico aún no ha reconocido,
 * para que la raíz de la app ([com.recre.app.RecreApp]) muestre un popup
 * central explicando qué pasó y por qué.
 *
 * Por qué aquí y no en [ShellViewModel]: ese se instancia por pestaña; este es
 * único a nivel de NavHost, así que el "ya lo he leído" no se duplica entre
 * pestañas. El reconocimiento es **en memoria**: si el proceso se reinicia y la
 * recaudación sigue atascada (la subida fallida se reintenta y vuelve a fallar),
 * el aviso reaparece a propósito —es un recordatorio hasta que se resuelva (C)—.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ErroresSubidaViewModel
@Inject
constructor(
    sessionRepository: SessionRepository,
    recaudacionRepository: RecaudacionRepository,
) : ViewModel() {

    /** ids de errores ya mostrados/descartados en esta sesión de proceso. */
    private val reconocidos = MutableStateFlow<Set<String>>(emptySet())

    private val empresaIdFlow =
        flow {
            sessionRepository.state.collect { state ->
                emit((state as? SessionState.Active)?.empresa?.id)
            }
        }

    private val erroresFlow =
        empresaIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else recaudacionRepository.observarErrores(id)
        }

    val errorActual: StateFlow<RecaudacionErrorUi?> =
        combine(erroresFlow, reconocidos) { errores, vistos ->
            errores
                .firstOrNull { it.id !in vistos }
                ?.let { e ->
                    RecaudacionErrorUi(
                        id = e.id,
                        motivo = e.ultimoError?.takeIf { it.isNotBlank() }
                            ?: "No se pudo subir por un error desconocido.",
                        fecha = e.fecha,
                    )
                }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /** El técnico ha leído el aviso: no se vuelve a mostrar este error en la sesión. */
    fun descartar(id: String) {
        reconocidos.update { it + id }
    }
}
