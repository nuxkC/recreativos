package com.recre.app.feature.gestion.licencias

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.LicenciaDao
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.data.repository.LicenciaInput
import com.recre.app.core.data.repository.LicenciasGestorRepository
import com.recre.app.core.util.ConnectivityRepository
import com.recre.app.feature.gestion.ESTADOS_LICENCIA
import com.recre.app.feature.gestion.esFechaIsoValida
import com.recre.app.feature.gestion.normalizarOpcional
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Form de alta/edición de Licencia (T-66).
 *
 * - Campos: número (obligatorio), tipo, fechas ISO opcionales,
 *   comunidad autónoma, estado (enum), notas.
 * - Validación lexica local: número no vacío, fechas en formato
 *   `YYYY-MM-DD`, fechaCaducidad ≥ fechaExpedicion. El resto lo aplica
 *   el server vía CHECK + RLS y se traduce con `errorCode`.
 *
 * El modo lo determina [SavedStateHandle]: si trae [ARG_LICENCIA_ID]
 * estamos editando y precargamos desde Room (T-51 ya tiene la fila).
 */
data class LicenciaFormUiState(
    val cargando: Boolean = false,
    val numero: String = "",
    val tipo: String = "",
    val fechaExpedicion: String = "",
    val fechaCaducidad: String = "",
    val comunidadAutonoma: String = "",
    val estado: String = "activa",
    val notas: String = "",
    val errores: Map<String, String> = emptyMap(),
    val errorCode: String? = null,
    val guardando: Boolean = false,
    val guardado: Boolean = false,
    val esEdicion: Boolean = false,
    val online: Boolean = true,
)

@HiltViewModel
class LicenciaFormViewModel @Inject constructor(
    private val licenciaDao: LicenciaDao,
    private val repository: LicenciasGestorRepository,
    private val connectivityRepository: ConnectivityRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val licenciaId: String? = savedStateHandle[ARG_LICENCIA_ID]

    private val _state = MutableStateFlow(
        LicenciaFormUiState(esEdicion = licenciaId != null, cargando = licenciaId != null),
    )
    val state: StateFlow<LicenciaFormUiState> = _state.asStateFlow()

    init {
        if (licenciaId != null) {
            viewModelScope.launch {
                val entity = licenciaDao.obtener(licenciaId)
                if (entity != null) {
                    _state.update {
                        it.copy(
                            cargando = false,
                            numero = entity.numero,
                            tipo = entity.tipo.orEmpty(),
                            fechaExpedicion = entity.fechaExpedicion.orEmpty(),
                            fechaCaducidad = entity.fechaCaducidad.orEmpty(),
                            comunidadAutonoma = entity.comunidadAutonoma.orEmpty(),
                            estado = entity.estado,
                            notas = entity.notas.orEmpty(),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(cargando = false, errorCode = "not_found")
                    }
                }
            }
        }
        viewModelScope.launch {
            connectivityRepository.online.collect { online ->
                _state.update { it.copy(online = online) }
            }
        }
    }

    fun onNumeroChange(v: String) = _state.update { it.copy(numero = v) }
    fun onTipoChange(v: String) = _state.update { it.copy(tipo = v) }
    fun onFechaExpedicionChange(v: String) = _state.update { it.copy(fechaExpedicion = v) }
    fun onFechaCaducidadChange(v: String) = _state.update { it.copy(fechaCaducidad = v) }
    fun onComunidadAutonomaChange(v: String) = _state.update { it.copy(comunidadAutonoma = v) }
    fun onEstadoChange(v: String) = _state.update { it.copy(estado = v) }
    fun onNotasChange(v: String) = _state.update { it.copy(notas = v) }
    fun consumirError() = _state.update { it.copy(errorCode = null) }

    fun guardar() {
        val current = _state.value
        if (!current.online) {
            _state.update { it.copy(errorCode = "network") }
            return
        }
        val errores = validar(current)
        if (errores.isNotEmpty()) {
            _state.update { it.copy(errores = errores) }
            return
        }

        val input = LicenciaInput(
            numero = current.numero.trim(),
            tipo = current.tipo.normalizarOpcional(),
            fechaExpedicion = current.fechaExpedicion.normalizarOpcional(),
            fechaCaducidad = current.fechaCaducidad.normalizarOpcional(),
            comunidadAutonoma = current.comunidadAutonoma.normalizarOpcional(),
            estado = current.estado,
            notas = current.notas.normalizarOpcional(),
        )

        viewModelScope.launch {
            _state.update { it.copy(guardando = true, errores = emptyMap(), errorCode = null) }
            val result = if (licenciaId == null) {
                repository.crear(input)
            } else {
                repository.actualizar(licenciaId, input)
            }
            _state.update {
                when (result) {
                    is GestionResult.Success<*> -> it.copy(guardando = false, guardado = true)
                    is GestionResult.Failure -> it.copy(guardando = false, errorCode = result.code)
                }
            }
        }
    }

    private fun validar(s: LicenciaFormUiState): Map<String, String> {
        val errs = mutableMapOf<String, String>()
        if (s.numero.trim().isEmpty()) errs["numero"] = "requerido"
        if (s.fechaExpedicion.isNotBlank() && !esFechaIsoValida(s.fechaExpedicion)) {
            errs["fechaExpedicion"] = "fecha_invalida"
        }
        if (s.fechaCaducidad.isNotBlank() && !esFechaIsoValida(s.fechaCaducidad)) {
            errs["fechaCaducidad"] = "fecha_invalida"
        }
        if (
            errs.isEmpty() &&
            s.fechaExpedicion.isNotBlank() &&
            s.fechaCaducidad.isNotBlank()
        ) {
            val ini = LocalDate.parse(s.fechaExpedicion)
            val fin = LocalDate.parse(s.fechaCaducidad)
            if (fin.isBefore(ini)) {
                errs["fechaCaducidad"] = "fecha_caducidad_anterior_expedicion"
            }
        }
        if (s.estado !in ESTADOS_LICENCIA) errs["estado"] = "estado_invalido"
        return errs
    }

    companion object {
        const val ARG_LICENCIA_ID = "licenciaId"
    }
}
