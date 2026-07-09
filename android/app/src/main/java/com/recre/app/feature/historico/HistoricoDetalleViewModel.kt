package com.recre.app.feature.historico

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.repository.RecaudacionHistorica
import com.recre.app.core.data.repository.RecaudacionHistoricaRepository
import com.recre.app.core.printer.PrintResult
import com.recre.app.core.printer.PrinterError
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
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
 * Estado de la pantalla de detalle (T-63).
 *
 * Las acciones de "Reimprimir PDF" y "Reimprimir ESC/POS" son
 * concurrentes lo justo: bloqueamos los botones mientras uno está en
 * vuelo para no mandar dos jobs a la vez al hardware.
 */
data class HistoricoDetalleUiState(
    val cargando: Boolean = true,
    val recaudacion: RecaudacionHistorica? = null,
    val errorCarga: HistoricoErrorCode? = null,

    /**
     * Nombre de la empresa activa para la cabecera del ticket (N8). No viene en
     * el modelo del histórico; se toma de la sesión offline. `null` si aún no
     * hay empresa activa resuelta (el ticket omite la línea sin romper).
     */
    val empresaNombre: String? = null,

    /** Pdf URL que la UI abre en navegador. La consume y reset. */
    val pdfSignedUrl: String? = null,
    val descargandoPdf: Boolean = false,
    val errorPdf: HistoricoErrorCode? = null,

    val imprimiendoBluetooth: Boolean = false,
    val printResult: PrintResult? = null,
)

@HiltViewModel
class HistoricoDetalleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecaudacionHistoricaRepository,
    private val realtimeManager: RealtimeManager,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val recaudacionId: String = checkNotNull(savedStateHandle[ARG_RECAUDACION_ID]) {
        "Falta argumento '$ARG_RECAUDACION_ID' en HistoricoDetalleViewModel"
    }

    // Nombre de la empresa activa (offline) para la cabecera del ticket.
    private val empresaNombre: String?
        get() = (sessionRepository.state.value as? SessionState.Active)?.empresa?.nombre

    private val _state = MutableStateFlow(HistoricoDetalleUiState())
    val state: StateFlow<HistoricoDetalleUiState> = _state.asStateFlow()

    init {
        cargar()
        // Realtime: refresca el detalle ante cualquier cambio server-side.
        viewModelScope.launch {
            realtimeManager.revision.drop(1).collect { cargar() }
        }
    }

    fun cargar() {
        viewModelScope.launch {
            _state.update { it.copy(cargando = true, errorCarga = null) }
            when (val result = repository.obtenerDetalle(recaudacionId)) {
                is DomainResult.Success ->
                    _state.update {
                        it.copy(
                            cargando = false,
                            recaudacion = result.value,
                            empresaNombre = empresaNombre,
                        )
                    }

                is DomainResult.Failure ->
                    _state.update {
                        it.copy(cargando = false, errorCarga = mapError(result.error))
                    }
            }
        }
    }

    /**
     * Pide la signed URL del PDF de archivo al backend (Edge
     * `reimprimir-ticket`). La pantalla la abre en navegador con un
     * `Intent.ACTION_VIEW`. Tras consumirla, la pantalla llama a
     * [onPdfUrlConsumido] para que no se vuelva a abrir al recomponer.
     */
    fun reimprimirPdf() {
        if (_state.value.descargandoPdf || _state.value.imprimiendoBluetooth) return
        viewModelScope.launch {
            _state.update {
                it.copy(descargandoPdf = true, errorPdf = null, pdfSignedUrl = null)
            }
            when (val result = repository.reimprimirPdf(recaudacionId)) {
                is DomainResult.Success ->
                    _state.update {
                        it.copy(descargandoPdf = false, pdfSignedUrl = result.value)
                    }

                is DomainResult.Failure ->
                    _state.update {
                        it.copy(descargandoPdf = false, errorPdf = mapError(result.error))
                    }
            }
        }
    }

    fun onPdfUrlConsumido() {
        _state.update { it.copy(pdfSignedUrl = null) }
    }

    /**
     * Reimprime el ticket en la PT210 vinculada (T-62). Reusa el
     * pipeline ESC/POS: descarga la firma desde Storage y reconstruye
     * el ticket. La UI muestra el PrintResult con el mismo card que en
     * el flujo principal de recaudación.
     */
    fun reimprimirBluetooth() {
        if (_state.value.descargandoPdf || _state.value.imprimiendoBluetooth) return
        viewModelScope.launch {
            _state.update { it.copy(imprimiendoBluetooth = true, printResult = null) }
            when (val result = repository.reimprimirBluetooth(recaudacionId)) {
                is DomainResult.Success ->
                    _state.update {
                        it.copy(imprimiendoBluetooth = false, printResult = result.value)
                    }

                is DomainResult.Failure -> {
                    val pr = PrintResult.Failure(domainErrorAPrinter(result.error))
                    _state.update {
                        it.copy(imprimiendoBluetooth = false, printResult = pr)
                    }
                }
            }
        }
    }

    fun limpiarPrintResult() {
        _state.update { it.copy(printResult = null) }
    }

    fun limpiarErrorPdf() {
        _state.update { it.copy(errorPdf = null) }
    }

    private fun domainErrorAPrinter(error: DomainError): PrinterError = when (error) {
        is DomainError.Network -> PrinterError.ConexionFallida(error.message)
        else -> PrinterError.ImpresionFallida(error.message)
    }

    private fun mapError(error: DomainError): HistoricoErrorCode = when (error) {
        is DomainError.Network -> HistoricoErrorCode.Network
        is DomainError.Auth -> HistoricoErrorCode.Auth
        is DomainError.NotFound -> HistoricoErrorCode.Unknown
        else -> HistoricoErrorCode.Unknown
    }

    companion object {
        const val ARG_RECAUDACION_ID = "recaudacionId"
    }
}
