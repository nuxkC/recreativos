package com.recre.app.feature.impresora

import com.recre.app.core.printer.EscPos
import com.recre.app.core.printer.PrinterDevice
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Mini-ticket de prueba para validar conectividad con la impresora
 * desde el selector. No incluye datos de empresa/recaudación: solo
 * confirma que la app puede abrir el socket SPP, escribir y que el
 * cabezal funciona.
 */
internal object TicketPruebaEscPos {

    private val FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    fun render(device: PrinterDevice): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(EscPos.INIT)
        out.write(EscPos.ALIGN_CENTER)
        out.write(EscPos.BOLD_ON)
        out.write(EscPos.DOUBLE_HEIGHT)
        out.write(EscPos.line("PRUEBA RECRE"))
        out.write(EscPos.DOUBLE_OFF)
        out.write(EscPos.BOLD_OFF)
        out.write(EscPos.ALIGN_LEFT)
        out.write(EscPos.SEPARADOR)
        out.write(EscPos.keyValue("Impresora:", device.name.take(EscPos.COLS - 11)))
        out.write(EscPos.keyValue("MAC:", device.mac))
        out.write(EscPos.keyValue("Fecha:", FORMATTER.format(LocalDateTime.now())))
        out.write(EscPos.SEPARADOR)
        out.write(EscPos.line("Si lees este ticket, la"))
        out.write(EscPos.line("impresora esta lista para"))
        out.write(EscPos.line("imprimir recaudaciones."))
        out.write(EscPos.feedLines(4))
        return out.toByteArray()
    }
}
