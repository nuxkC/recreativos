package com.recre.app.core.printer

import com.recre.app.core.calculo.Cifras
import com.recre.app.core.calculo.DenominacionItem
import com.recre.app.core.data.local.entity.EmpresaParamsEntity
import com.recre.app.core.data.repository.MaquinaConInstalacion
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Facade de impresión que expone el resto de la app.
 *
 * Combina:
 *  - [PrinterPreferences] para saber qué dispositivo usar.
 *  - [BluetoothPrinterManager] para hablar con el hardware.
 *  - [TicketEscPos] para construir el payload.
 *
 * El flujo de recaudación llama únicamente a [imprimirTicketRecaudacion]
 * — todo el resto (chequeo de permisos, fallback, errores) queda
 * encapsulado aquí. El selector de impresora usa [bondedDevices],
 * [seleccionarImpresora] y [seleccionadaFlow].
 */
interface PrinterRepository {

    val seleccionadaFlow: Flow<PrinterDevice?>

    fun bondedDevices(): List<PrinterDevice>
    fun bluetoothActivo(): Boolean
    fun tienePermiso(): Boolean

    suspend fun seleccionarImpresora(device: PrinterDevice?)

    /**
     * Envía bytes ESC/POS arbitrarios a la MAC indicada. Se usa para la
     * "página de prueba" del selector. El flujo de recaudación NO
     * llama a este método — usa [imprimirTicketRecaudacion] que
     * encapsula la construcción del payload.
     */
    suspend fun imprimirRaw(mac: String, payload: ByteArray): PrintResult

    /**
     * Imprime el ticket de recaudación. Si no hay impresora vinculada,
     * permisos o Bluetooth disponible, devuelve el [PrinterError]
     * correspondiente. Nunca lanza excepciones — el llamador lo trata
     * como información, no como error fatal.
     */
    suspend fun imprimirTicketRecaudacion(
        empresa: EmpresaParamsEntity?,
        localNombre: String,
        localDireccion: String?,
        maquina: MaquinaConInstalacion,
        tecnicoEmail: String?,
        fecha: Instant,
        contadorEntradasActual: Long,
        contadorSalidasActual: Long,
        cifras: Cifras,
        desgloseTotal: List<DenominacionItem>,
        desgloseLocal: List<DenominacionItem>,
        firmaPng: ByteArray,
    ): PrintResult
}

@Singleton
class PrinterRepositoryImpl @Inject constructor(
    private val preferences: PrinterPreferences,
    private val manager: BluetoothPrinterManager,
) : PrinterRepository {

    override val seleccionadaFlow: Flow<PrinterDevice?> = preferences.seleccionadaFlow

    override fun bondedDevices(): List<PrinterDevice> = manager.bondedDevices()

    override fun bluetoothActivo(): Boolean = manager.bluetoothActivo()

    override fun tienePermiso(): Boolean = manager.tienePermiso()

    override suspend fun seleccionarImpresora(device: PrinterDevice?) {
        preferences.setSeleccionada(device)
    }

    override suspend fun imprimirRaw(mac: String, payload: ByteArray): PrintResult {
        if (!manager.tienePermiso()) return PrintResult.Failure(PrinterError.SinPermiso)
        if (!manager.bluetoothActivo()) {
            return PrintResult.Failure(PrinterError.BluetoothNoDisponible)
        }
        return manager.imprimir(mac, payload)
    }

    override suspend fun imprimirTicketRecaudacion(
        empresa: EmpresaParamsEntity?,
        localNombre: String,
        localDireccion: String?,
        maquina: MaquinaConInstalacion,
        tecnicoEmail: String?,
        fecha: Instant,
        contadorEntradasActual: Long,
        contadorSalidasActual: Long,
        cifras: Cifras,
        desgloseTotal: List<DenominacionItem>,
        desgloseLocal: List<DenominacionItem>,
        firmaPng: ByteArray,
    ): PrintResult {
        if (!manager.tienePermiso()) return PrintResult.Failure(PrinterError.SinPermiso)
        if (!manager.bluetoothActivo()) {
            return PrintResult.Failure(PrinterError.BluetoothNoDisponible)
        }
        val device = preferences.seleccionada()
            ?: return PrintResult.Failure(PrinterError.SinImpresora)

        val payload = runCatching {
            TicketEscPos.render(
                empresa = empresa,
                localNombre = localNombre,
                localDireccion = localDireccion,
                maquina = maquina,
                tecnicoEmail = tecnicoEmail,
                fecha = fecha,
                contadorEntradasActual = contadorEntradasActual,
                contadorSalidasActual = contadorSalidasActual,
                cifras = cifras,
                desgloseTotal = desgloseTotal,
                desgloseLocal = desgloseLocal,
                firmaPng = firmaPng,
            )
        }.getOrElse {
            Timber.e(it, "No se pudo construir el payload ESC/POS")
            return PrintResult.Failure(PrinterError.ImpresionFallida(it.message))
        }

        return manager.imprimir(device.mac, payload)
    }
}
