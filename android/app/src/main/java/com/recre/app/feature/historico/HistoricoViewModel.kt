package com.recre.app.feature.historico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.RecaudacionHistorica
import com.recre.app.core.data.repository.RecaudacionHistoricaRepository
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state de "Mis recaudaciones" (T-63).
 *
 * El listado se carga en el `init` y se refresca con
 * pull-to-refresh. No hay paginación: el repositorio limita a 200
 * filas que cubre con holgura el histórico personal de un técnico.
 *
 * El filtro de texto vive en el ViewModel (no en URL como en la web)
 * y filtra client-side por número de serie / nombre de local
 * (case-insensitive).
 */
data class HistoricoUiState(
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
) : ViewModel() {

    private val _state = MutableStateFlow(HistoricoUiState())
    val state: StateFlow<HistoricoUiState> = _state.asStateFlow()

    init {
        cargar()
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
            when (val result = repository.listarMias()) {
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
}
