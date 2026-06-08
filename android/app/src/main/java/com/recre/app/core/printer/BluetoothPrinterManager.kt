package com.recre.app.core.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Frontera de hardware contra el adaptador Bluetooth Classic del
 * dispositivo (T-62).
 *
 * Funciones:
 *  - [bondedDevices] lista las impresoras emparejadas en los Ajustes
 *    del sistema. La AGPTEK PT210 se aparea con PIN 0000 una sola vez;
 *    desde ese momento aparece aquí siempre.
 *  - [imprimir] abre un socket SPP, escribe el payload ESC/POS y cierra.
 *    Best-effort: cualquier error vuelve como [PrintResult.Failure] con
 *    un [PrinterError] discriminado para que la UI muestre el copy
 *    correcto.
 *
 * Permisos: en Android 12+ usamos `BLUETOOTH_CONNECT` (declarado en el
 * manifest, runtime). En versiones <= 11 los permisos de manifest se
 * consideran auto-otorgados (ver `AndroidManifest.xml`). [tienePermiso]
 * comprueba el caso correcto según `Build.VERSION.SDK_INT`.
 *
 * El adaptador se obtiene por demanda — es válido que el técnico abra
 * la app sin Bluetooth disponible y aún así pueda recaudar offline; la
 * impresión es una capa opcional.
 */
@Singleton
class BluetoothPrinterManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * UUID estándar de Serial Port Profile (SPP). La AGPTEK PT210
     * expone su servicio de impresión bajo este UUID, idéntico al de
     * la mayoría de impresoras térmicas BT chinas/genéricas.
     */
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val adapter: BluetoothAdapter?
        get() = ContextCompat
            .getSystemService(context, BluetoothManager::class.java)
            ?.adapter

    /** `true` si el sistema declara los permisos necesarios. */
    fun tienePermiso(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // En API <= 30 los permisos BLUETOOTH y BLUETOOTH_ADMIN son
            // implícitos al estar declarados en el manifest.
            true
        }
    }

    /** `true` si hay adaptador BT y está encendido. */
    fun bluetoothActivo(): Boolean = adapter?.isEnabled == true

    /**
     * Lista impresoras emparejadas. Si no hay permisos, adaptador o
     * está apagado, devuelve lista vacía: la UI consulta [tienePermiso]
     * y [bluetoothActivo] para mostrar el aviso correcto.
     *
     * @SuppressLint("MissingPermission") porque `tienePermiso()`
     *   ya valida BLUETOOTH_CONNECT en API >= 31.
     */
    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<PrinterDevice> {
        val ad = adapter ?: return emptyList()
        if (!tienePermiso()) return emptyList()
        if (!ad.isEnabled) return emptyList()
        return runCatching {
            ad.bondedDevices.orEmpty().map { device ->
                PrinterDevice(
                    mac = device.address,
                    name = device.name ?: device.address,
                )
            }.sortedBy { it.name.lowercase() }
        }.getOrElse {
            Timber.w(it, "No se pudieron leer los bondedDevices")
            emptyList()
        }
    }

    /**
     * Escribe los bytes en la impresora `mac`. La operación es síncrona
     * (suspendida en `Dispatchers.IO`) porque para un ticket de 1-2 KB
     * no merece la pena montar un canal persistente.
     *
     * Pasos:
     *  1. Validar permiso, adaptador y MAC en bonded.
     *  2. Cancelar discovery (el manual de Android lo recomienda para
     *     que no degrade la conexión SPP).
     *  3. `createRfcommSocketToServiceRecord(SPP)` + `connect()`.
     *  4. `OutputStream.write(payload)` + `flush()`.
     *  5. Cerrar siempre en `finally`.
     */
    @SuppressLint("MissingPermission")
    suspend fun imprimir(mac: String, payload: ByteArray): PrintResult =
        withContext(Dispatchers.IO) {
            if (!tienePermiso()) return@withContext PrintResult.Failure(PrinterError.SinPermiso)
            val ad = adapter
                ?: return@withContext PrintResult.Failure(PrinterError.BluetoothNoDisponible)
            if (!ad.isEnabled) {
                return@withContext PrintResult.Failure(PrinterError.BluetoothNoDisponible)
            }

            val device: BluetoothDevice = runCatching { ad.getRemoteDevice(mac) }
                .getOrElse {
                    return@withContext PrintResult.Failure(
                        PrinterError.ConexionFallida("MAC inválida: $mac"),
                    )
                }

            // Si el técnico no la tiene emparejada (la quitó desde
            // Ajustes), avisamos para que vuelva a vincularla; sin
            // emparejar, la API de socket no nos pide PIN.
            val emparejada = runCatching {
                ad.bondedDevices.orEmpty().any { it.address == mac }
            }.getOrElse { true }
            if (!emparejada) return@withContext PrintResult.Failure(PrinterError.NoEmparejada)

            // Cancelar el discovery puede requerir BLUETOOTH_SCAN en
            // algunos OEM. Lo envolvemos defensivamente — si no hay
            // discovery activo, ignorar el resultado es seguro.
            runCatching { ad.cancelDiscovery() }

            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                socket.outputStream.use { os ->
                    os.write(payload)
                    os.flush()
                }
                Timber.i("Ticket enviado a impresora %s (%d bytes)", mac, payload.size)
                PrintResult.Success
            } catch (io: IOException) {
                Timber.w(io, "Fallo de impresión Bluetooth a %s", mac)
                if (socket?.isConnected == true) {
                    PrintResult.Failure(PrinterError.ImpresionFallida(io.message))
                } else {
                    PrintResult.Failure(PrinterError.ConexionFallida(io.message))
                }
            } catch (t: Throwable) {
                Timber.e(t, "Error inesperado imprimiendo")
                PrintResult.Failure(PrinterError.ImpresionFallida(t.message))
            } finally {
                runCatching { socket?.close() }
            }
        }
}
