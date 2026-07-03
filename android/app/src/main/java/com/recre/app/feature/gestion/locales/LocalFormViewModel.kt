package com.recre.app.feature.gestion.locales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.repository.GeoRepository
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.data.repository.LocalInputData
import com.recre.app.core.data.repository.LocalesGestorRepository
import com.recre.app.core.data.repository.Provincia
import com.recre.app.core.util.ConnectivityRepository
import com.recre.app.feature.gestion.FkOption
import com.recre.app.feature.gestion.esCifNif
import com.recre.app.feature.gestion.esEmailValido
import com.recre.app.feature.gestion.esTelefono
import com.recre.app.feature.gestion.municipiosComoOpciones
import com.recre.app.feature.gestion.normalizarOpcional
import com.recre.app.feature.gestion.provinciasDeCcaa
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class LocalFormUiState(
    val cargando: Boolean = false,
    val nombre: String = "",
    val cifONif: String = "",
    val titularNombre: String = "",
    val telefono: String = "",
    val email: String = "",
    val notas: String = "",
    // Dirección estructurada (T-277). provincia/municipio guardan el CÓDIGO INE.
    val comunidadAutonoma: String = "",
    val provinciaCodigo: String = "",
    val municipioCodigo: String = "",
    val calle: String = "",
    val codigoPostal: String = "",
    val provinciasDisponibles: List<FkOption> = emptyList(),
    val municipiosDisponibles: List<FkOption> = emptyList(),
    val errores: Map<String, String> = emptyMap(),
    val errorCode: String? = null,
    val guardando: Boolean = false,
    val guardado: Boolean = false,
    val esEdicion: Boolean = false,
    val online: Boolean = true,
)

/**
 * Form de Local (T-68). Sin estado, sin unicidad sobre nombre — solo
 * `nombre` obligatorio y `email` con formato validado cuando se rellena.
 */
@HiltViewModel
class LocalFormViewModel @Inject constructor(
    private val localDao: LocalDao,
    private val repository: LocalesGestorRepository,
    private val geoRepository: GeoRepository,
    private val connectivityRepository: ConnectivityRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val localId: String? = savedStateHandle[ARG_LOCAL_ID]

    private val _state = MutableStateFlow(
        LocalFormUiState(esEdicion = localId != null, cargando = localId != null),
    )
    val state: StateFlow<LocalFormUiState> = _state.asStateFlow()

    // Catálogo de provincias cacheado para filtrar por CCAA en cliente; el job de
    // municipios se cancela al cambiar de provincia (evita carrera de respuestas).
    private var catalogoProvincias: List<Provincia> = emptyList()
    private var jobMunicipios: Job? = null

    init {
        if (localId != null) {
            viewModelScope.launch {
                val entity = localDao.obtener(localId)
                if (entity != null) {
                    _state.update {
                        it.copy(
                            cargando = false,
                            nombre = entity.nombre,
                            cifONif = entity.cifONif.orEmpty(),
                            titularNombre = entity.titularNombre.orEmpty(),
                            telefono = entity.telefono.orEmpty(),
                            email = entity.email.orEmpty(),
                            notas = entity.notas.orEmpty(),
                            comunidadAutonoma = entity.comunidadAutonoma.orEmpty(),
                            provinciaCodigo = entity.provinciaCodigo.orEmpty(),
                            municipioCodigo = entity.municipioCodigo.orEmpty(),
                            calle = entity.calle.orEmpty(),
                            codigoPostal = entity.codigoPostal.orEmpty(),
                            // Si las provincias ya cargaron, deriva las de esta CCAA;
                            // si no, cargarProvincias() lo recalcula al terminar.
                            provinciasDisponibles =
                                provinciasDeCcaa(entity.comunidadAutonoma.orEmpty(), catalogoProvincias),
                        )
                    }
                    // Precarga los municipios de la provincia guardada (prefill).
                    if (!entity.provinciaCodigo.isNullOrBlank()) cargarMunicipios(entity.provinciaCodigo)
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
        cargarProvincias()
    }

    fun onNombreChange(v: String) = _state.update { it.copy(nombre = v) }
    fun onCifChange(v: String) = _state.update { it.copy(cifONif = v) }
    fun onTitularChange(v: String) = _state.update { it.copy(titularNombre = v) }
    fun onTelefonoChange(v: String) = _state.update { it.copy(telefono = v) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v) }
    fun onNotasChange(v: String) = _state.update { it.copy(notas = v) }

    fun onComunidadAutonomaChange(v: String) {
        val cambia = v.trim() != _state.value.comunidadAutonoma.trim()
        // Corta una carga de municipios en vuelo de la CCAA anterior: si no, podría
        // repoblar municipiosDisponibles con municipios ya inválidos tras el reset.
        if (cambia) jobMunicipios?.cancel()
        _state.update { s ->
            s.copy(
                comunidadAutonoma = v,
                // Cambiar de CCAA invalida provincia y municipio (pertenecen a la CCAA).
                provinciaCodigo = if (cambia) "" else s.provinciaCodigo,
                municipioCodigo = if (cambia) "" else s.municipioCodigo,
                municipiosDisponibles = if (cambia) emptyList() else s.municipiosDisponibles,
                provinciasDisponibles = provinciasDeCcaa(v, catalogoProvincias),
            )
        }
    }

    fun onProvinciaChange(codigo: String) {
        val cambia = codigo != _state.value.provinciaCodigo
        _state.update {
            it.copy(
                provinciaCodigo = codigo,
                // Cambiar de provincia invalida el municipio.
                municipioCodigo = if (cambia) "" else it.municipioCodigo,
                municipiosDisponibles = if (cambia) emptyList() else it.municipiosDisponibles,
            )
        }
        if (cambia) cargarMunicipios(codigo)
    }

    fun onMunicipioChange(codigo: String) = _state.update { it.copy(municipioCodigo = codigo) }
    fun onCalleChange(v: String) = _state.update { it.copy(calle = v) }
    fun onCodigoPostalChange(v: String) = _state.update { it.copy(codigoPostal = v) }
    fun consumirError() = _state.update { it.copy(errorCode = null) }

    fun guardar() {
        val s = _state.value
        if (!s.online) {
            _state.update { it.copy(errorCode = "network") }
            return
        }
        val errores = mutableMapOf<String, String>()
        if (s.nombre.trim().isEmpty()) errores["nombre"] = "requerido"
        val email = s.email.trim()
        if (email.isNotEmpty() && !esEmailValido(email)) errores["email"] = "email_invalido"
        val cif = s.cifONif.trim()
        if (cif.isNotEmpty() && !esCifNif(cif)) errores["cifONif"] = "cif_invalido"
        val telefono = s.telefono.trim()
        if (telefono.isNotEmpty() && !esTelefono(telefono)) errores["telefono"] = "telefono_invalido"
        val cp = s.codigoPostal.trim()
        if (cp.isNotEmpty() && !cp.matches(Regex("^\\d{5}$"))) errores["codigoPostal"] = "codigo_postal_invalido"

        if (errores.isNotEmpty()) {
            _state.update { it.copy(errores = errores) }
            return
        }

        val input = LocalInputData(
            nombre = s.nombre.trim(),
            cifONif = s.cifONif.normalizarOpcional(),
            titularNombre = s.titularNombre.normalizarOpcional(),
            telefono = s.telefono.normalizarOpcional(),
            email = email.ifEmpty { null },
            notas = s.notas.normalizarOpcional(),
            comunidadAutonoma = s.comunidadAutonoma.normalizarOpcional(),
            provinciaCodigo = s.provinciaCodigo.normalizarOpcional(),
            municipioCodigo = s.municipioCodigo.normalizarOpcional(),
            calle = s.calle.normalizarOpcional(),
            codigoPostal = cp.ifEmpty { null },
        )

        viewModelScope.launch {
            _state.update { it.copy(guardando = true, errores = emptyMap(), errorCode = null) }
            val result = if (localId == null) repository.crear(input)
            else repository.actualizar(localId, input)
            _state.update {
                when (result) {
                    is GestionResult.Success<*> -> it.copy(guardando = false, guardado = true)
                    is GestionResult.Failure -> it.copy(guardando = false, errorCode = result.code)
                }
            }
        }
    }

    private fun cargarProvincias() {
        viewModelScope.launch {
            when (val r = geoRepository.cargarProvincias()) {
                is GestionResult.Success -> {
                    catalogoProvincias = r.value
                    // Recalcula con la CCAA ya presente en el state (prefill en edición).
                    _state.update {
                        it.copy(provinciasDisponibles = provinciasDeCcaa(it.comunidadAutonoma, r.value))
                    }
                }
                is GestionResult.Failure -> Timber.w("Provincias no disponibles: %s", r.code)
            }
        }
    }

    private fun cargarMunicipios(provinciaCodigo: String) {
        jobMunicipios?.cancel()
        if (provinciaCodigo.isBlank()) {
            _state.update { it.copy(municipiosDisponibles = emptyList()) }
            return
        }
        jobMunicipios = viewModelScope.launch {
            when (val r = geoRepository.cargarMunicipios(provinciaCodigo)) {
                is GestionResult.Success ->
                    _state.update { it.copy(municipiosDisponibles = municipiosComoOpciones(r.value)) }
                is GestionResult.Failure -> Timber.w("Municipios no disponibles: %s", r.code)
            }
        }
    }

    companion object {
        const val ARG_LOCAL_ID = "localGestorId"
    }
}
