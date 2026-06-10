package com.recre.app.feature.gestion.maquinas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.MaquinaDao
import com.recre.app.core.data.local.entity.MaquinaEntity
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.data.repository.MaquinasGestorRepository
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

data class MaquinasGestorUiState(
    val cargando: Boolean = true,
    val maquinas: List<MaquinaEntity> = emptyList(),
    val busqueda: String = "",
    val borrando: String? = null,
    val errorCode: String? = null,
    val online: Boolean = true,
)

/** Lista de máquinas para gestor+ (T-67). Mismo patrón que licencias. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MaquinasGestorViewModel @Inject constructor(
    private val maquinaDao: MaquinaDao,
    private val sessionRepository: SessionRepository,
    private val repository: MaquinasGestorRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MaquinasGestorUiState())
    val state: StateFlow<MaquinasGestorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            empresaIdFlow().flatMapLatest { empresaId ->
                if (empresaId == null) flowOf(emptyList())
                else maquinaDao.observarPorEmpresa(empresaId)
            }.collectLatest { rows ->
                _state.update { it.copy(cargando = false, maquinas = rows) }
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
