package com.recre.app.feature.gestion.locales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.local.entity.LocalEntity
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.data.repository.LocalesGestorRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.ConnectivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalesGestorUiState(
    val cargando: Boolean = true,
    val locales: List<LocalEntity> = emptyList(),
    val busqueda: String = "",
    val borrando: String? = null,
    val errorCode: String? = null,
    val online: Boolean = true,
)

/**
 * Lista del CRUD de Locales (T-68).
 *
 * El listado de locales con sus máquinas activas ya existe en
 * `LocalesScreen` (T-52). Esta pantalla es una vista de gestión
 * orientada a alta/edición/baja: cards con datos administrativos del
 * local (nombre, dirección, titular, teléfono) sin contar máquinas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalesGestorViewModel @Inject constructor(
    private val localDao: LocalDao,
    private val sessionRepository: SessionRepository,
    private val repository: LocalesGestorRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalesGestorUiState())
    val state: StateFlow<LocalesGestorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            empresaIdFlow().flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else localDao.observarPorEmpresa(id)
            }.collectLatest { rows ->
                _state.update { it.copy(cargando = false, locales = rows) }
            }
        }
        viewModelScope.launch {
            connectivityRepository.online.collectLatest { online ->
                _state.update { it.copy(online = online) }
            }
        }
    }

    fun onBusquedaChange(q: String) = _state.update { it.copy(busqueda = q) }
    fun consumirError() = _state.update { it.copy(errorCode = null) }

    fun eliminar(id: String) {
        if (!_state.value.online) {
            _state.update { it.copy(errorCode = "network") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(borrando = id) }
            val r = repository.eliminar(id)
            _state.update {
                when (r) {
                    is GestionResult.Success<*> -> it.copy(borrando = null)
                    is GestionResult.Failure -> it.copy(borrando = null, errorCode = r.code)
                }
            }
        }
    }

    private fun empresaIdFlow() =
        sessionRepository.state.map { (it as? SessionState.Active)?.empresa?.id }
}
