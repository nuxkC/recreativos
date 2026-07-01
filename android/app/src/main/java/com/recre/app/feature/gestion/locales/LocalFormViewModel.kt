package com.recre.app.feature.gestion.locales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.data.repository.LocalInputData
import com.recre.app.core.data.repository.LocalesGestorRepository
import com.recre.app.core.util.ConnectivityRepository
import com.recre.app.feature.gestion.esCifNif
import com.recre.app.feature.gestion.esEmailValido
import com.recre.app.feature.gestion.esTelefono
import com.recre.app.feature.gestion.normalizarOpcional
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalFormUiState(
    val cargando: Boolean = false,
    val nombre: String = "",
    val direccion: String = "",
    val cifONif: String = "",
    val titularNombre: String = "",
    val telefono: String = "",
    val email: String = "",
    val notas: String = "",
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
    private val connectivityRepository: ConnectivityRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val localId: String? = savedStateHandle[ARG_LOCAL_ID]

    private val _state = MutableStateFlow(
        LocalFormUiState(esEdicion = localId != null, cargando = localId != null),
    )
    val state: StateFlow<LocalFormUiState> = _state.asStateFlow()

    init {
        if (localId != null) {
            viewModelScope.launch {
                val entity = localDao.obtener(localId)
                if (entity != null) {
                    _state.update {
                        it.copy(
                            cargando = false,
                            nombre = entity.nombre,
                            direccion = entity.direccion.orEmpty(),
                            cifONif = entity.cifONif.orEmpty(),
                            titularNombre = entity.titularNombre.orEmpty(),
                            telefono = entity.telefono.orEmpty(),
                            email = entity.email.orEmpty(),
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
    }

    fun onNombreChange(v: String) = _state.update { it.copy(nombre = v) }
    fun onDireccionChange(v: String) = _state.update { it.copy(direccion = v) }
    fun onCifChange(v: String) = _state.update { it.copy(cifONif = v) }
    fun onTitularChange(v: String) = _state.update { it.copy(titularNombre = v) }
    fun onTelefonoChange(v: String) = _state.update { it.copy(telefono = v) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v) }
    fun onNotasChange(v: String) = _state.update { it.copy(notas = v) }
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

        if (errores.isNotEmpty()) {
            _state.update { it.copy(errores = errores) }
            return
        }

        val input = LocalInputData(
            nombre = s.nombre.trim(),
            direccion = s.direccion.normalizarOpcional(),
            cifONif = s.cifONif.normalizarOpcional(),
            titularNombre = s.titularNombre.normalizarOpcional(),
            telefono = s.telefono.normalizarOpcional(),
            email = email.ifEmpty { null },
            notas = s.notas.normalizarOpcional(),
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

    companion object {
        const val ARG_LOCAL_ID = "localGestorId"
    }
}
