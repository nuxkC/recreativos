package com.recre.app.feature.impresora

import com.recre.app.core.printer.EscPos
import com.recre.app.core.printer.PrinterDevice
import com.recre.app.core.printer.PrinterProfile
import com.recre.app.core.printer.PrinterProfiles
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Mini-ticket de prueba para validar conectividad con la impresora
 * desde el selector. No incluye datos de empresa/recaudación: solo
 * confirma que la app puede abrir el socket SPP, escribir y que el
 * cabezal funciona.
 *
 * Usa el [PrinterProfile] seleccionado para que la prueba refleje el
 * ancho real del papel (32/48 col) y el cierre (corte vs avance) del
 * modelo elegido (T-105).
 */
internal object TicketPruebaEscPos {

    private val FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    fun render(
        device: PrinterDevice,
        profile: PrinterProfile = PrinterProfiles.POR_DEFECTO,
    ): ByteArray {
        val cols = profile.cols
        val out = ByteArrayOutputStream()
        out.write(EscPos.INIT)
        out.write(EscPos.ALIGN_CENTER)
        out.write(EscPos.BOLD_ON)
        out.write(EscPos.DOUBLE_HEIGHT)
        out.write(EscPos.line("PRUEBA RECRE"))
        out.write(EscPos.DOUBLE_OFF)
        out.write(EscPos.BOLD_OFF)
        out.write(EscPos.ALIGN_LEFT)
        out.write(EscPos.separador(cols))
        out.write(EscPos.keyValue(cols, "Impresora:", device.name.take(cols - 11)))
        out.write(EscPos.keyValue(cols, "MAC:", device.mac))
        out.write(EscPos.keyValue(cols, "Fecha:", FORMATTER.format(LocalDateTime.now())))
        out.write(EscPos.separador(cols))
        out.write(EscPos.line("Si lees este ticket, la"))
        out.write(EscPos.line("impresora esta lista para"))
        out.write(EscPos.line("imprimir recaudaciones."))
        out.write(EscPos.feedLines(profile.lineasFinales))
        if (profile.tieneCuter) {
            out.write(EscPos.CUT)
        }
        return out.toByteArray()
    }
}
