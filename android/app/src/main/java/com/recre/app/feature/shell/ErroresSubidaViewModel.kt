package com.recre.app.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.AveriaRepository
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Estado del aviso ligero raíz de incidencias sin resolver. */
data class IncidenciasAvisoUiState(
    val count: Int = 0,
    val visible: Boolean = false,
)

/**
 * Aviso ligero raíz (T-260, degradado del antiguo panel modal T-63): vigila la cola
 * de la empresa activa y, si hay incidencias BLOQUEADAS (recaudaciones o averías en
 * estado 'error'/'fallida'), expone un recuento para que la raíz de la app
 * ([com.recre.app.RecreApp]) muestre un aviso breve que ENLAZA al Centro de
 * Incidencias. Ya NO lista ni actúa sobre las filas: eso vive en
 * [com.recre.app.feature.incidencias.IncidenciasViewModel].
 *
 * El reconocimiento ("ya lo he visto") es en memoria, por ids: si el proceso se
 * reinicia y siguen bloqueadas, reaparece —es un recordatorio hasta resolverlas—. Tras
 * cerrarlo solo vuelve a aparecer si entra una incidencia NUEVA.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ErroresSubidaViewModel
@Inject
constructor(
    sessionRepository: SessionRepository,
    recaudacionRepository: RecaudacionRepository,
    averiaRepository: AveriaRepository,
) : ViewModel() {

    /** ids ya vistos/cerrados en esta sesión de proceso: no reabrir salvo nueva. */
    private val reconocidos = MutableStateFlow<Set<String>>(emptySet())

    /** Última foto de ids bloqueados, para reconocerlos al cerrar. */
    private var idsActuales: Set<String> = emptySet()

    private val empresaIdFlow: Flow<String?> =
        sessionRepository.state
            .map { (it as? SessionState.Active)?.empresa?.id }
            .distinctUntilChanged()

    private val idsBloqueadasFlow: Flow<Set<String>> =
        empresaIdFlow.flatMapLatest { id ->
            if (id == null) {
                flowOf(emptySet())
            } else {
                combine(
                    recaudacionRepository.observarBloqueadas(id),
                    averiaRepository.observarBloqueadas(id),
                ) { recaud, averias ->
                    (recaud.map { "r-${it.id}" } + averias.map { "a-${it.id}" }).toSet()
                }
            }
        }

    init {
        idsBloqueadasFlow.onEach { idsActuales = it }.launchIn(viewModelScope)
    }

    val state: StateFlow<IncidenciasAvisoUiState> =
        combine(idsBloqueadasFlow, reconocidos) { ids, vistos ->
            IncidenciasAvisoUiState(
                count = ids.size,
                visible = ids.any { it !in vistos },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = IncidenciasAvisoUiState(),
        )

    /** Cierra el aviso: reconoce las visibles ahora (no reabrir hasta una nueva). */
    fun cerrar() {
        reconocidos.update { vistos -> vistos + idsActuales }
    }
}
