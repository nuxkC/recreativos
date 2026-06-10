package com.recre.app.core.printer

/**
 * Errores específicos del subsistema de impresión.
 *
 * No usamos `DomainError` (de network/auth) porque aquí los modos de
 * fallo son distintos y queremos que la UI mapee cada caso a un copy
 * concreto sin tener que adivinar.
 */
sealed interface PrinterError {

    /** El dispositivo no tiene Bluetooth o está apagado. */
    data object BluetoothNoDisponible : PrinterError

    /**
     * El usuario aún no ha concedido los permisos runtime en Android
     * 12+ (BLUETOOTH_CONNECT). Sin ellos no podemos ni listar bonded
     * devices ni conectar.
     */
    data object SinPermiso : PrinterError

    /** No hay ninguna impresora seleccionada en preferencias. */
    data object SinImpresora : PrinterError

    /**
     * El modelo/perfil de impresora persistido no se reconoce (T-105).
     * Ocurre si una preferencia quedó escrita con un id desconocido —
     * por ejemplo tras un downgrade de la app. La UI pide al técnico
     * que vuelva a elegir el modelo en Ajustes.
     */
    data object ModeloNoSoportado : PrinterError

    /**
     * La MAC guardada ya no aparece entre los `bondedDevices()`. El
     * técnico ha desemparejado la PT210 desde los ajustes del sistema;
     * la UI debe pedirle re-vincularla.
     */
    data object NoEmparejada : PrinterError

    /**
     * Falló la conexión SPP — lo más habitual: la impresora está
     * apagada, fuera de rango o ya conectada a otro móvil.
     */
    data class ConexionFallida(val mensaje: String?) : PrinterError

    /** Se conectó pero al escribir el `OutputStream` saltó la excepción. */
    data class ImpresionFallida(val mensaje: String?) : PrinterError
}

/** Resultado de una operación de impresión. */
sealed interface PrintResult {
    data object Success : PrintResult
    data class Failure(val error: PrinterError) : PrintResult
}
