package com.recre.app.feature.gestion.instalaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.InstalacionDao
import com.recre.app.core.data.local.dao.LicenciaDao
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.local.dao.MaquinaDao
import com.recre.app.core.data.local.entity.InstalacionEntity
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.data.repository.InstalacionesGestorRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lista del CRUD de Instalaciones (T-69).
 *
 * Solo muestra **activas** porque la cache Room (T-51) solo
 * sincroniza esas — son las únicas relevantes para el flujo de
 * recaudación. Cuando el usuario cierra una desde el form, el sync
 * forzado del [InstalacionesGestorRepository] la elimina de la cache
 * local automáticamente.
 *
 * Para mostrar info legible (nombre del local, número de serie de la
 * máquina, número de licencia) se hace un join en memoria con los DAOs
 * existentes. El dataset por empresa cabe sin paginación.
 */
data class InstalacionItem(
    val id: String,
    val maquinaNumeroSerie: String,
    val maquinaModelo: String?,
    val licenciaNumero: String,
    val localNombre: String,
    val tasaSemanal: String,
    val porcentajeLocal: String,
)

data class InstalacionesGestorUiState(
    val cargando: Boolean = true,
    val instalaciones: List<InstalacionItem> = emptyList(),
    val busqueda: String = "",
    val borrando: String? = null,
    val cerrando: String? = null,
    val errorCode: String? = null,
    val online: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InstalacionesGestorViewModel @Inject constructor(
    private val instalacionDao: InstalacionDao,
    private val maquinaDao: MaquinaDao,
    private val licenciaDao: LicenciaDao,
    private val localDao: LocalDao,
    private val sessionRepository: SessionRepository,
    private val repository: InstalacionesGestorRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InstalacionesGestorUiState())
    val state: StateFlow<InstalacionesGestorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            empresaIdFlow().flatMapLatest { empresaId ->
                if (empresaId == null) {
                    flowOf<List<InstalacionItem>>(emptyList())
                } else {
                    combine(
                        instalacionDao.observarActivasPorEmpresa(empresaId),
                        maquinaDao.observarPorEmpresa(empresaId),
                        licenciaDao.observarPorEmpresa(empresaId),
                        localDao.observarPorEmpresa(empresaId),
                    ) { instalaciones, maquinas, licencias, locales ->
                        val maquinaIndex = maquinas.associateBy { it.id }
                        val licenciaIndex = licencias.associateBy { it.id }
                        val localIndex = locales.associateBy { it.id }
                        instalaciones.mapNotNull { ent: InstalacionEntity ->
                            val m = maquinaIndex[ent.maquinaId] ?: return@mapNotNull null
                            val lic = licenciaIndex[ent.licenciaId] ?: return@mapNotNull null
                            val loc = localIndex[ent.localId] ?: return@mapNotNull null
                            InstalacionItem(
                                id = ent.id,
                                maquinaNumeroSerie = m.numeroSerie,
                                maquinaModelo = m.modelo,
                                licenciaNumero = lic.numero,
                                localNombre = loc.nombre,
                                tasaSemanal = ent.tasaSemanal,
                                porcentajeLocal = ent.porcentajeLocal,
                            )
                        }
                    }
                }
            }.collectLatest { items ->
                _state.update { it.copy(cargando = false, instalaciones = items) }
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
                val reset = it.copy(borrando = null)
                when (r) {
                    is GestionResult.Success<*> -> reset
                    is GestionResult.Failure -> reset.copy(errorCode = r.code)
                }
            }
        }
    }

    fun cerrar(id: String, fechaFin: String, notas: String?) {
        if (!_state.value.online) {
            _state.update { it.copy(errorCode = "network") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(cerrando = id) }
            val r = repository.cerrar(id, fechaFin, notas)
            _state.update {
                val reset = it.copy(cerrando = null)
                when (r) {
                    is GestionResult.Success<*> -> reset
                    is GestionResult.Failure -> reset.copy(errorCode = r.code)
                }
            }
        }
    }

    private fun empresaIdFlow() =
        sessionRepository.state.map { (it as? SessionState.Active)?.empresa?.id }
}
