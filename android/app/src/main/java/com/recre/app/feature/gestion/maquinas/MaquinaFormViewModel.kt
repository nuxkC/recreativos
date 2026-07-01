package com.recre.app.feature.gestion.maquinas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.MaquinaDao
import com.recre.app.core.data.repository.CatalogoRepository
import com.recre.app.core.data.repository.FabricanteCatalogo
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.data.repository.MaquinaInput
import com.recre.app.core.data.repository.MaquinasGestorRepository
import com.recre.app.core.data.repository.ModeloCatalogo
import com.recre.app.core.util.ConnectivityRepository
import com.recre.app.feature.gestion.ESTADOS_MAQUINA
import com.recre.app.feature.gestion.FkOption
import com.recre.app.feature.gestion.fabricantesComoOpciones
import com.recre.app.feature.gestion.modelosDeFabricante
import com.recre.app.feature.gestion.normalizarDecimal
import com.recre.app.feature.gestion.normalizarOpcional
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Form de Máquina (T-67).
 *
 * `valorCredito` se valida con [normalizarDecimal] (rango `0,01..999,99`,
 * dos decimales, acepta coma o punto). Los contadores iniciales son
 * enteros >= 0. La unicidad `(empresa_id, numero_serie)` se valida en
 * server (PG `23505` -> `numero_serie_duplicado`).
 */
data class MaquinaFormUiState(
    val cargando: Boolean = false,
    val numeroSerie: String = "",
    val modelo: String = "",
    val fabricante: String = "",
    val valorCredito: String = "",
    val contadorEntradasInicial: String = "0",
    val contadorSalidasInicial: String = "0",
    val estado: String = "almacen",
    val notas: String = "",
    val errores: Map<String, String> = emptyMap(),
    val errorCode: String? = null,
    val guardando: Boolean = false,
    val guardado: Boolean = false,
    val esEdicion: Boolean = false,
    val online: Boolean = true,
    val fabricantesDisponibles: List<FkOption> = emptyList(),
    val modelosDisponibles: List<FkOption> = emptyList(),
)

@HiltViewModel
class MaquinaFormViewModel @Inject constructor(
    private val maquinaDao: MaquinaDao,
    private val repository: MaquinasGestorRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val catalogoRepository: CatalogoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val maquinaId: String? = savedStateHandle[ARG_MAQUINA_ID]

    // Catálogo crudo cacheado para recalcular la cascada sin re-consultar.
    private var catalogoFabricantes: List<FabricanteCatalogo> = emptyList()
    private var catalogoModelos: List<ModeloCatalogo> = emptyList()

    private val _state = MutableStateFlow(
        MaquinaFormUiState(esEdicion = maquinaId != null, cargando = maquinaId != null),
    )
    val state: StateFlow<MaquinaFormUiState> = _state.asStateFlow()

    init {
        if (maquinaId != null) {
            viewModelScope.launch {
                val entity = maquinaDao.obtener(maquinaId)
                if (entity != null) {
                    _state.update {
                        it.copy(
                            cargando = false,
                            numeroSerie = entity.numeroSerie,
                            modelo = entity.modelo.orEmpty(),
                            fabricante = entity.fabricante.orEmpty(),
                            // Cierra la carrera con cargarCatalogo(): si el catálogo llegó
                            // antes que la entidad, ya recalcula la lista de modelos del
                            // fabricante cargado (si aún no llegó, queda vacío y lo rellena
                            // cargarCatalogo). El valor de modelo se preserva siempre.
                            modelosDisponibles =
                                modelosDeFabricante(
                                    entity.fabricante.orEmpty(),
                                    catalogoFabricantes,
                                    catalogoModelos,
                                ),
                            valorCredito = entity.valorCredito,
                            contadorEntradasInicial = entity.contadorEntradasInicial.toString(),
                            contadorSalidasInicial = entity.contadorSalidasInicial.toString(),
                            estado = entity.estado,
                            notas = entity.notas.orEmpty(),
                        )
                    }
                } else {
                    _state.update { it.copy(cargando = false, errorCode = "not_found") }
                }
            }
        }
        viewModelScope.launch {
            connectivityRepository.online.collect { online ->
                _state.update { it.copy(online = online) }
            }
        }
        viewModelScope.launch { cargarCatalogo() }
    }

    fun onNumeroSerieChange(v: String) = _state.update { it.copy(numeroSerie = v) }
    fun onModeloChange(v: String) = _state.update { it.copy(modelo = v) }
    fun onFabricanteChange(v: String) = _state.update { s ->
        // Con el autocomplete, esto es un COMMIT (elegir/crear), no cada tecla.
        val cambiaFabricante = v.trim() != s.fabricante.trim()
        s.copy(
            fabricante = v,
            // Cambiar de fabricante invalida el modelo (pertenece a un fabricante).
            modelo = if (cambiaFabricante) "" else s.modelo,
            modelosDisponibles = modelosDeFabricante(v, catalogoFabricantes, catalogoModelos),
        )
    }
    fun onValorCreditoChange(v: String) = _state.update { it.copy(valorCredito = v) }
    fun onContadorEntradasChange(v: String) = _state.update { it.copy(contadorEntradasInicial = v) }
    fun onContadorSalidasChange(v: String) = _state.update { it.copy(contadorSalidasInicial = v) }
    fun onEstadoChange(v: String) = _state.update { it.copy(estado = v) }
    fun onNotasChange(v: String) = _state.update { it.copy(notas = v) }
    fun consumirError() = _state.update { it.copy(errorCode = null) }

    fun guardar() {
        val s = _state.value
        if (!s.online) {
            _state.update { it.copy(errorCode = "network") }
            return
        }
        val errores = mutableMapOf<String, String>()
        if (s.numeroSerie.trim().isEmpty()) errores["numeroSerie"] = "requerido"

        val valor = normalizarDecimal(
            raw = s.valorCredito,
            min = MIN_VALOR,
            max = MAX_VALOR,
            decimales = 2,
        )
        if (valor == null) errores["valorCredito"] = "valor_credito_invalido"

        val entradas = s.contadorEntradasInicial.toLongOrNull()
        if (entradas == null || entradas < 0) errores["contadorEntradas"] = "contador_invalido"
        val salidas = s.contadorSalidasInicial.toLongOrNull()
        if (salidas == null || salidas < 0) errores["contadorSalidas"] = "contador_invalido"

        if (s.estado !in ESTADOS_MAQUINA) errores["estado"] = "estado_invalido"

        if (errores.isNotEmpty()) {
            _state.update { it.copy(errores = errores) }
            return
        }

        val input = MaquinaInput(
            numeroSerie = s.numeroSerie.trim(),
            modelo = s.modelo.normalizarOpcional(),
            fabricante = s.fabricante.normalizarOpcional(),
            valorCredito = valor!!.toPlainString(),
            contadorEntradasInicial = entradas!!,
            contadorSalidasInicial = salidas!!,
            estado = s.estado,
            notas = s.notas.normalizarOpcional(),
        )

        viewModelScope.launch {
            _state.update { it.copy(guardando = true, errores = emptyMap(), errorCode = null) }
            val result = if (maquinaId == null) repository.crear(input)
            else repository.actualizar(maquinaId, input)
            _state.update {
                when (result) {
                    is GestionResult.Success<*> -> it.copy(guardando = false, guardado = true)
                    is GestionResult.Failure -> it.copy(guardando = false, errorCode = result.code)
                }
            }
        }
    }

    private suspend fun cargarCatalogo() {
        when (val r = catalogoRepository.cargar()) {
            is GestionResult.Success -> {
                catalogoFabricantes = r.value.fabricantes
                catalogoModelos = r.value.modelos
                _state.update {
                    it.copy(
                        fabricantesDisponibles = fabricantesComoOpciones(r.value.fabricantes),
                        modelosDisponibles =
                            modelosDeFabricante(it.fabricante, r.value.fabricantes, r.value.modelos),
                    )
                }
            }
            is GestionResult.Failure ->
                // Silencioso: sin catálogo el usuario aún teclea libre; la RPC crea al guardar.
                Timber.w("Catálogo de máquinas no disponible: %s", r.code)
        }
    }

    companion object {
        const val ARG_MAQUINA_ID = "maquinaId"
        private val MIN_VALOR = BigDecimal("0.01")
        private val MAX_VALOR = BigDecimal("999.99")
    }
}
