package com.recre.app.feature.cambio_placa

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.remote.RecaudacionRemoteDataSource
import com.recre.app.core.data.remote.RecaudacionRemoteError
import com.recre.app.core.data.remote.dto.CrearCambioPlacaRequest
import com.recre.app.core.data.repository.InventoryRepository
import com.recre.app.core.data.repository.MaquinaConInstalacion
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.util.DomainError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Estado de la pantalla "Cambio de placa" (T-61).
 */
data class CambioPlacaUiState(
    val maquina: MaquinaConInstalacion? = null,
    val contadorEntradasInput: String = "0",
    val contadorSalidasInput: String = "0",
    val motivo: String = "",
    val numeroSerieNueva: String = "",
    val guardando: Boolean = false,
    val guardado: Boolean = false,
    /** Código de error i18n para mostrar el snackbar. `null` = sin error. */
    val errorCode: String? = null,
) {
    val canGuardar: Boolean
        get() = !guardando &&
            maquina != null &&
            contadorEntradasInput.toLongOrNull()?.let { it >= 0 } == true &&
            contadorSalidasInput.toLongOrNull()?.let { it >= 0 } == true
}

/**
 * Registra un cambio de placa de máquina vía Edge Function `crear-cambio-placa`.
 *
 * A diferencia del flujo de recaudación (T-57), aquí **no hay cola offline**:
 * un cambio de placa requiere conexión y el técnico ve un error si no la
 * tiene. Las recaudaciones siempre se persisten (HU-15), pero las
 * operaciones de inventario sí pueden bloquearse con red caída para
 * evitar baselines inconsistentes (mismo criterio que aplicaremos en
 * T-66..T-69 con el CRUD gestor).
 *
 * Tras un cambio exitoso, fuerza un sync para que la nueva baseline
 * llegue a Room y la siguiente recaudación lo refleje.
 */
@HiltViewModel
class CambioPlacaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inventoryRepository: InventoryRepository,
    private val sessionRepository: SessionRepository,
    private val remote: RecaudacionRemoteDataSource,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val instalacionId: String = checkNotNull(savedStateHandle[ARG_INSTALACION_ID]) {
        "Falta argumento '$ARG_INSTALACION_ID' al abrir CambioPlacaScreen"
    }

    private val _uiState = MutableStateFlow(CambioPlacaUiState())
    val uiState: StateFlow<CambioPlacaUiState> = _uiState.asStateFlow()

    val maquinaFlow: StateFlow<MaquinaConInstalacion?> =
        inventoryRepository.observarMaquinaPorInstalacion(instalacionId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    init {
        viewModelScope.launch {
            maquinaFlow.collect { maquina ->
                _uiState.update { it.copy(maquina = maquina) }
            }
        }
    }

    fun onContadorEntradasChange(value: String) {
        val sanitized = value.filter { it.isDigit() }.take(MAX_DIGITS).ifEmpty { "0" }
        _uiState.update { it.copy(contadorEntradasInput = sanitized) }
    }

    fun onContadorSalidasChange(value: String) {
        val sanitized = value.filter { it.isDigit() }.take(MAX_DIGITS).ifEmpty { "0" }
        _uiState.update { it.copy(contadorSalidasInput = sanitized) }
    }

    fun onMotivoChange(value: String) {
        _uiState.update { it.copy(motivo = value.take(MAX_TEXT)) }
    }

    fun onNumeroSerieNuevaChange(value: String) {
        _uiState.update { it.copy(numeroSerieNueva = value.take(MAX_NUM_SERIE)) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorCode = null) }
    }

    fun onGuardar() {
        val state = _uiState.value
        val maquina = state.maquina ?: return
        if (!state.canGuardar) return

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, errorCode = null) }

            val empresaId = (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
            val zona = ZoneId.systemDefault()
            val fechaIso = LocalDate.now(zona).toString()

            val request = CrearCambioPlacaRequest(
                instalacionId = maquina.instalacionId,
                fecha = fechaIso,
                contadorEntradasNuevo = state.contadorEntradasInput.toLongOrNull() ?: 0L,
                contadorSalidasNuevo = state.contadorSalidasInput.toLongOrNull() ?: 0L,
                motivo = state.motivo.takeIf { it.isNotBlank() },
                numeroSeriePlacaAnterior = maquina.numeroSerie,
                numeroSeriePlacaNueva = state.numeroSerieNueva.takeIf { it.isNotBlank() },
            )

            val result = runCatching { remote.crearCambioPlaca(request) }
            result.fold(
                onSuccess = { response ->
                    Timber.i(
                        "Cambio de placa OK: id=%s instalacion=%s",
                        response.id,
                        response.instalacionId,
                    )
                    // Refrescar sync para que la nueva baseline llegue a Room.
                    if (empresaId != null) syncManager.forzarSincronizacion(empresaId)
                    _uiState.update { it.copy(guardando = false, guardado = true) }
                },
                onFailure = { throwable ->
                    val (_, code) = clasificar(throwable)
                    Timber.w(throwable, "Cambio de placa fallido: %s", code)
                    _uiState.update { it.copy(guardando = false, errorCode = code) }
                },
            )
        }
    }

    private fun clasificar(throwable: Throwable): Pair<DomainError, String> = when (throwable) {
        is RecaudacionRemoteError -> when (throwable.code) {
            "validation_error" -> DomainError.Validation(throwable.message) to "validation_error"
            "forbidden" -> DomainError.Auth(throwable.message) to "forbidden"
            "not_found" -> DomainError.NotFound(throwable.message) to "not_found"
            else -> DomainError.Unknown(throwable.message) to (throwable.code ?: "unknown")
        }
        else -> {
            val msg = throwable.message ?: "unknown"
            if (msg.contains("network", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true)
            ) {
                DomainError.Network(msg) to "network"
            } else {
                DomainError.Unknown(msg) to "unknown"
            }
        }
    }

    companion object {
        const val ARG_INSTALACION_ID = "instalacionId"
        private const val MAX_DIGITS = 12
        private const val MAX_TEXT = 500
        private const val MAX_NUM_SERIE = 60
    }
}
