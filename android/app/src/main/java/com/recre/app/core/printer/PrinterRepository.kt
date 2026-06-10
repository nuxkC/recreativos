package com.recre.app.core.printer

import com.recre.app.core.calculo.Cifras
import com.recre.app.core.calculo.DenominacionItem
import com.recre.app.core.data.local.entity.EmpresaParamsEntity
import com.recre.app.core.data.repository.MaquinaConInstalacion
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    /**
     * Perfil/modelo de impresora seleccionado (T-105). Emite
     * [PrinterProfiles.POR_DEFECTO] (PT210) mientras el técnico no haya
     * elegido otro modelo, de modo que el formato de ticket existente
     * no cambia.
     */
    val perfilSeleccionadoFlow: Flow<PrinterProfile>

    /** Catálogo de modelos soportados, para el selector de Ajustes. */
    fun perfilesDisponibles(): List<PrinterProfile>

    fun bondedDevices(): List<PrinterDevice>
    fun bluetoothActivo(): Boolean
    fun tienePermiso(): Boolean

    suspend fun seleccionarImpresora(device: PrinterDevice?)

    /** Persiste el modelo/perfil de impresora elegido (T-105). */
    suspend fun seleccionarPerfil(perfil: PrinterProfile)

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

    override val perfilSeleccionadoFlow: Flow<PrinterProfile> =
        preferences.perfilIdFlow.map { PrinterProfiles.resolverOPorDefecto(it) }

    override fun perfilesDisponibles(): List<PrinterProfile> = PrinterProfiles.TODOS

    override fun bondedDevices(): List<PrinterDevice> = manager.bondedDevices()

    override fun bluetoothActivo(): Boolean = manager.bluetoothActivo()

    override fun tienePermiso(): Boolean = manager.tienePermiso()

    override suspend fun seleccionarImpresora(device: PrinterDevice?) {
        preferences.setSeleccionada(device)
    }

    override suspend fun seleccionarPerfil(perfil: PrinterProfile) {
        preferences.setPerfil(perfil)
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

        // Resuelve el perfil persistido. Si hay un id guardado pero no
        // lo reconocemos (p. ej. downgrade), avisamos en lugar de
        // imprimir con un formato equivocado. Sin id guardado usamos la
        // PT210 por defecto (compatibilidad T-62).
        val perfilId = preferences.perfilId()
        val perfil = if (perfilId == null) {
            PrinterProfiles.POR_DEFECTO
        } else {
            PrinterProfiles.resolver(perfilId)
                ?: return PrintResult.Failure(PrinterError.ModeloNoSoportado)
        }

        val payload = runCatching {
            TicketEscPos.render(
                profile = perfil,
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
