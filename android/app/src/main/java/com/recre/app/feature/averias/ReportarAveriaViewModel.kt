package com.recre.app.feature.averias

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.MaquinaDao
import com.recre.app.core.data.repository.AveriaRepository
import com.recre.app.core.data.repository.RecambioInput
import com.recre.app.core.data.repository.ReportarAveriaInput
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.AveriaUploadManager
import com.recre.app.core.util.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un recambio en el formulario de reporte. `coste` ya normalizado o `null`. */
data class RecambioFormItem(
    val pieza: String,
    val cantidad: Int,
    val coste: String?,
)

/**
 * Estado de la pantalla "Reportar avería" (T-222). El técnico siempre puede
 * guardar: el reporte se encola offline y se sube al recuperar la red.
 */
data class ReportarAveriaUiState(
    val maquinaNumeroSerie: String = "",
    val categoria: CategoriaAveria? = null,
    val descripcion: String = "",
    val poneFueraServicio: Boolean = false,
    val notas: String = "",
    val recambios: List<RecambioFormItem> = emptyList(),
    val guardando: Boolean = false,
    val guardado: Boolean = false,
    /** Código de error i18n para el snackbar; `null` = sin error. */
    val errorCode: String? = null,
) {
    val canGuardar: Boolean get() = !guardando && categoria != null
}

@HiltViewModel
class ReportarAveriaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val averiaRepository: AveriaRepository,
    private val averiaUploadManager: AveriaUploadManager,
    private val sessionRepository: SessionRepository,
    private val maquinaDao: MaquinaDao,
) : ViewModel() {

    private val maquinaId: String = checkNotNull(savedStateHandle[ARG_MAQUINA_ID]) {
        "Falta argumento '$ARG_MAQUINA_ID' al abrir ReportarAveriaScreen"
    }

    private val _state = MutableStateFlow(ReportarAveriaUiState())
    val state: StateFlow<ReportarAveriaUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val maquina = maquinaDao.obtener(maquinaId)
            if (maquina != null) {
                _state.update { it.copy(maquinaNumeroSerie = maquina.numeroSerie) }
            }
        }
    }

    fun onCategoria(categoria: CategoriaAveria) =
        _state.update { it.copy(categoria = categoria) }

    fun onDescripcion(value: String) =
        _state.update { it.copy(descripcion = value.take(MAX_TEXTO)) }

    fun onToggleFueraServicio(value: Boolean) =
        _state.update { it.copy(poneFueraServicio = value) }

    fun onNotas(value: String) =
        _state.update { it.copy(notas = value.take(MAX_TEXTO)) }

    /**
     * Añade un recambio al reporte. Devuelve `false` (sin tocar el estado) si la
     * entrada no es válida: pieza vacía, cantidad < 1, o coste con formato
     * inválido. La UI usa el booleano para no limpiar los campos en ese caso.
     */
    fun addRecambio(pieza: String, cantidadRaw: String, costeRaw: String): Boolean {
        val piezaLimpia = pieza.trim()
        if (piezaLimpia.isEmpty()) return false
        val cantidad = cantidadRaw.trim().toIntOrNull()?.takeIf { it > 0 } ?: return false
        val coste = if (costeRaw.isBlank()) null else normalizeCoste(costeRaw) ?: return false
        _state.update {
            it.copy(
                recambios = it.recambios + RecambioFormItem(
                    pieza = piezaLimpia.take(MAX_PIEZA),
                    cantidad = cantidad,
                    coste = coste,
                ),
            )
        }
        return true
    }

    fun removeRecambio(index: Int) = _state.update {
        if (index !in it.recambios.indices) it
        else it.copy(recambios = it.recambios.filterIndexed { i, _ -> i != index })
    }

    fun consumeError() = _state.update { it.copy(errorCode = null) }

    fun onGuardar() {
        val current = _state.value
        val categoria = current.categoria ?: return
        if (!current.canGuardar) return

        val empresaId = (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
        if (empresaId == null) {
            _state.update { it.copy(errorCode = "auth") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(guardando = true, errorCode = null) }
            val input = ReportarAveriaInput(
                empresaId = empresaId,
                maquinaId = maquinaId,
                maquinaNumeroSerie = current.maquinaNumeroSerie,
                categoria = categoria.valor,
                descripcion = current.descripcion.trim().ifBlank { null },
                poneMaquinaFueraServicio = current.poneFueraServicio,
                notas = current.notas.trim().ifBlank { null },
                recambios = current.recambios.map {
                    RecambioInput(pieza = it.pieza, cantidad = it.cantidad, coste = it.coste, notas = null)
                },
            )
            when (val result = averiaRepository.encolar(input)) {
                is DomainResult.Success -> {
                    // Intentar subir cuanto antes; si no hay red, el reporte queda
                    // en cola y se subirá luego (la pantalla ya confirma el guardado).
                    averiaUploadManager.encolar(empresaId)
                    _state.update { it.copy(guardando = false, guardado = true) }
                }
                is DomainResult.Failure -> {
                    _state.update { it.copy(guardando = false, errorCode = "guardar") }
                }
            }
        }
    }

    companion object {
        const val ARG_MAQUINA_ID = "maquinaId"
        private const val MAX_TEXTO = 1000
        private const val MAX_PIEZA = 120
    }
}
