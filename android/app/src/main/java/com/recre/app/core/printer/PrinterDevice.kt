package com.recre.app.core.printer

/**
 * Una impresora Bluetooth Classic emparejada en el sistema (lista que
 * devuelve `BluetoothAdapter.getBondedDevices()`).
 *
 * El [mac] es la dirección estable que usa [BluetoothPrinterManager]
 * para abrir el socket SPP. El [name] es el alias que el usuario ve en
 * la lista de "Dispositivos Bluetooth" del sistema (p. ej.
 * "PT-210" o "Printer001"); siempre tiene un valor — si el dispositivo
 * no expone nombre, fallback al propio MAC.
 */
data class PrinterDevice(
    val mac: String,
    val name: String,
)
