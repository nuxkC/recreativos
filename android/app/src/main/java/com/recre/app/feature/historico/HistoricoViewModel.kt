package com.recre.app.feature.historico

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.RecaudacionHistorica
import com.recre.app.core.data.repository.RecaudacionHistoricaRepository
import com.recre.app.core.sync.RealtimeManager
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Contexto del histórico, derivado de los argumentos de navegación:
 *  - [Global]: el tab inferior. Muestra todo lo que el usuario ve por su
 *    rol (RBAC vía la vista), no solo "las mías".
 *  - [Local] / [Maquina]: drill-down desde la ficha de un local o una
 *    máquina; acota la query server-side por ese id.
 */
sealed interface HistoricoContexto {
    data object Global : HistoricoContexto

    data class Local(val localId: String) : HistoricoContexto

    data class Maquina(val maquinaId: String) : HistoricoContexto
}

/**
 * UI state del Histórico (T-63 + Histórico v2, §6.5).
 *
 * El listado carga en el `init` según el [contexto]. El filtrado por
 * texto se calcula in-memory porque el tope de 200 filas cabe de sobra.
 */
data class HistoricoUiState(
    val contexto: HistoricoContexto = HistoricoContexto.Global,
    val cargando: Boolean = false,
    val recaudaciones: List<RecaudacionHistorica> = emptyList(),
    val query: String = "",
    val error: HistoricoErrorCode? = null,
) {
    /**
     * Lista filtrada por la query actual. Se calcula in-memory porque
     * 200 filas cabe perfectamente.
     */
    val filtradas: List<RecaudacionHistorica>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return recaudaciones
            val lower = q.lowercase()
            return recaudaciones.filter {
                it.localNombre.lowercase().contains(lower) ||
                    it.maquinaSerie.lowercase().contains(lower) ||
                    (it.licenciaNumero?.lowercase()?.contains(lower) == true)
            }
        }
}

/** Códigos que la pantalla traduce a strings. */
enum class HistoricoErrorCode { Network, Auth, Unknown }

@HiltViewModel
class HistoricoViewModel @Inject constructor(
    private val repository: RecaudacionHistoricaRepository,
    savedStateHandle: SavedStateHandle,
    private val realtimeManager: RealtimeManager,
) : ViewModel() {

    // El contexto sale de los args de ruta: el tab global no lleva
    // ninguno; el drill-down lleva localId o maquinaId.
    private val contexto: HistoricoContexto = run {
        val localId: String? = savedStateHandle[ARG_LOCAL_ID]
        val maquinaId: String? = savedStateHandle[ARG_MAQUINA_ID]
        when {
            localId != null -> HistoricoContexto.Local(localId)
            maquinaId != null -> HistoricoContexto.Maquina(maquinaId)
            else -> HistoricoContexto.Global
        }
    }

    private val _state = MutableStateFlow(HistoricoUiState(contexto = contexto))
    val state: StateFlow<HistoricoUiState> = _state.asStateFlow()

    init {
        cargar()
        // Realtime: refresca el histórico ante cualquier cambio server-side.
        viewModelScope.launch {
            realtimeManager.revision.drop(1).collect { cargar() }
        }
    }

    fun refrescar() = cargar()

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun limpiarError() {
        _state.update { it.copy(error = null) }
    }

    private fun cargar() {
        viewModelScope.launch {
            _state.update { it.copy(cargando = true, error = null) }
            val result = when (val ctx = contexto) {
                is HistoricoContexto.Local -> repository.listarPorLocal(ctx.localId)
                is HistoricoContexto.Maquina -> repository.listarPorMaquina(ctx.maquinaId)
                HistoricoContexto.Global -> repository.listarVisibles()
            }
            when (result) {
                is DomainResult.Success ->
                    _state.update {
                        it.copy(cargando = false, recaudaciones = result.value)
                    }
                is DomainResult.Failure ->
                    _state.update {
                        it.copy(cargando = false, error = mapError(result.error))
                    }
            }
        }
    }

    private fun mapError(error: DomainError): HistoricoErrorCode = when (error) {
        is DomainError.Network -> HistoricoErrorCode.Network
        is DomainError.Auth -> HistoricoErrorCode.Auth
        else -> HistoricoErrorCode.Unknown
    }

    companion object {
        const val ARG_LOCAL_ID = "localId"
        const val ARG_MAQUINA_ID = "maquinaId"
    }
}
