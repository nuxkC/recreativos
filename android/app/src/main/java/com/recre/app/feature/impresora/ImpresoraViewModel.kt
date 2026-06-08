package com.recre.app.feature.impresora

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.printer.PrintResult
import com.recre.app.core.printer.PrinterDevice
import com.recre.app.core.printer.PrinterError
import com.recre.app.core.printer.PrinterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state de la pantalla de vinculación de impresora.
 */
data class ImpresoraUiState(
    val tienePermiso: Boolean = false,
    val bluetoothActivo: Boolean = false,
    /** Lista de dispositivos emparejados en el sistema. */
    val emparejados: List<PrinterDevice> = emptyList(),
    /** Impresora actualmente persistida en preferencias. */
    val seleccionada: PrinterDevice? = null,
    /**
     * MAC en proceso de prueba (botón "Probar impresión"). Mientras
     * está activa, los demás botones se deshabilitan.
     */
    val probandoMac: String? = null,
    /** Código de error/éxito último para snackbar. */
    val mensaje: ImpresoraMensaje? = null,
)

/**
 * Códigos de feedback que la pantalla traduce a `strings.xml`.
 *
 * Hacemos un sealed en lugar de `String` para que añadir nuevos casos
 * obligue a actualizar `when` exhaustivos en la pantalla.
 */
sealed interface ImpresoraMensaje {
    data object PruebaOk : ImpresoraMensaje
    data class PruebaError(val error: PrinterError) : ImpresoraMensaje
    data object SeleccionGuardada : ImpresoraMensaje
}

/**
 * ViewModel de "Vincular impresora" (T-62).
 *
 * No solicita permisos directamente: la pantalla expone un launcher
 * de `RequestPermission` y llama a [onPermisoConcedido] cuando cambie
 * el estado para que refresquemos la lista de bonded devices. La
 * misma señal sirve cuando el usuario activa Bluetooth desde fuera y
 * vuelve.
 */
@HiltViewModel
class ImpresoraViewModel @Inject constructor(
    private val repository: PrinterRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImpresoraUiState())
    val state: StateFlow<ImpresoraUiState> = _state.asStateFlow()

    init {
        // Hidrata la selección persistida.
        viewModelScope.launch {
            repository.seleccionadaFlow.collect { sel ->
                _state.update { it.copy(seleccionada = sel) }
            }
        }
        refrescar()
    }

    /**
     * Re-lee el adaptador y la lista de bonded. La pantalla la llama
     * al volver del `Settings` del sistema (vía `LifecycleResumeEffect`)
     * o tras conceder/denegar permisos.
     */
    fun refrescar() {
        val tienePermiso = repository.tienePermiso()
        val activo = repository.bluetoothActivo()
        val devices = if (tienePermiso && activo) repository.bondedDevices() else emptyList()
        _state.update {
            it.copy(
                tienePermiso = tienePermiso,
                bluetoothActivo = activo,
                emparejados = devices,
            )
        }
    }

    fun onPermisoConcedido() {
        refrescar()
    }

    fun seleccionar(device: PrinterDevice) {
        viewModelScope.launch {
            repository.seleccionarImpresora(device)
            _state.update { it.copy(mensaje = ImpresoraMensaje.SeleccionGuardada) }
        }
    }

    fun limpiarSeleccion() {
        viewModelScope.launch {
            repository.seleccionarImpresora(null)
        }
    }

    /**
     * Imprime una página de prueba con el mismo formato del ticket
     * pero con datos de muestra. Útil para que el técnico verifique
     * en sitio que la impresora responde antes de irse del local.
     */
    fun probarImpresion(device: PrinterDevice) {
        viewModelScope.launch {
            _state.update { it.copy(probandoMac = device.mac) }
            // Ticket de prueba simple: "PRUEBA RECRE\n<MAC>\n--- timestamp ---\n"
            val payload = TicketPruebaEscPos.render(device)
            val result = runCatching { repository.imprimirRaw(device.mac, payload) }
                .getOrElse { PrintResult.Failure(PrinterError.ImpresionFallida(it.message)) }
            val mensaje = when (result) {
                is PrintResult.Success -> ImpresoraMensaje.PruebaOk
                is PrintResult.Failure -> {
                    Timber.w("Prueba de impresion fallo: %s", result.error)
                    ImpresoraMensaje.PruebaError(result.error)
                }
            }
            _state.update { it.copy(probandoMac = null, mensaje = mensaje) }
        }
    }

    fun consumirMensaje() {
        _state.update { it.copy(mensaje = null) }
    }
}
