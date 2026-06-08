package com.recre.app.feature.gestion.licencias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.LicenciaDao
import com.recre.app.core.data.local.entity.LicenciaEntity
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.data.repository.LicenciasGestorRepository
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

/**
 * Lista de licencias para el CRUD gestor (T-66).
 *
 * Lee directamente de la cache Room (T-51): el listado es read-only desde
 * el punto de vista del ViewModel y las mutaciones (alta/edit/delete)
 * viven en [LicenciaFormViewModel] y en [eliminar].
 *
 * Multi-tenant: filtra por `empresa_id` activa leído de la sesión.
 * El filtro de búsqueda se aplica client-side (cientos de filas cabe
 * sin paginación, igual que en la web).
 */
data class LicenciasGestorUiState(
    val cargando: Boolean = true,
    val licencias: List<LicenciaEntity> = emptyList(),
    val busqueda: String = "",
    val borrando: String? = null,
    val errorCode: String? = null,
    val online: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LicenciasGestorViewModel @Inject constructor(
    private val licenciaDao: LicenciaDao,
    private val sessionRepository: SessionRepository,
    private val repository: LicenciasGestorRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LicenciasGestorUiState())
    val state: StateFlow<LicenciasGestorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            empresaIdFlow().flatMapLatest { empresaId ->
                if (empresaId == null) flowOf(emptyList())
                else licenciaDao.observarPorEmpresa(empresaId)
            }.collectLatest { licencias ->
                _state.update { it.copy(cargando = false, licencias = licencias) }
            }
        }
        viewModelScope.launch {
            connectivityRepository.online.collectLatest { online ->
                _state.update { it.copy(online = online) }
            }
        }
    }

    fun onBusquedaChange(q: String) {
        _state.update { it.copy(busqueda = q) }
    }

    fun consumirError() {
        _state.update { it.copy(errorCode = null) }
    }

    fun eliminar(id: String) {
        if (!_state.value.online) {
            _state.update { it.copy(errorCode = "network") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(borrando = id) }
            val result = repository.eliminar(id)
            _state.update {
                when (result) {
                    is GestionResult.Success<*> -> it.copy(borrando = null)
                    is GestionResult.Failure -> it.copy(borrando = null, errorCode = result.code)
                }
            }
        }
    }

    private fun empresaIdFlow() =
        sessionRepository.state.map { (it as? SessionState.Active)?.empresa?.id }
}
