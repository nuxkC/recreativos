package com.recre.app.core.printer

import com.recre.app.core.calculo.Cifras
import com.recre.app.core.calculo.DenominacionItem
import com.recre.app.core.data.local.entity.EmpresaParamsEntity
import com.recre.app.core.data.repository.MaquinaConInstalacion
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Construye los bytes ESC/POS del ticket de recaudación que se imprime
 * al cerrar el flujo (T-62, multi-modelo en T-105).
 *
 * El layout sigue [design.md §11]. El ancho de columnas, el escalado de
 * la firma y el cierre del ticket (corte vs avance de papel) salen del
 * [PrinterProfile] seleccionado, de modo que el mismo render sirve para
 * la PT210 (58 mm, 32 col, sin cuter) y para modelos de 80 mm con cuter
 * sin duplicar la lógica de formateo. Por defecto el perfil es la PT210,
 * así que el ticket de los técnicos existentes no cambia.
 *
 * La firma se manda como imagen raster (`GS v 0`) reusando el mismo
 * PNG que se persiste en Storage por T-57. Eso garantiza que ticket
 * y archivo digital muestran exactamente la misma firma.
 *
 * No persiste estado: pasa de inputs a `ByteArray` y devuelve. Es
 * `object` porque no tiene dependencias.
 */
object TicketEscPos {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /**
     * Render principal del ticket.
     *
     * @param profile perfil de la impresora destino (ancho, columnas,
     *        cuter). Por defecto [PrinterProfiles.POR_DEFECTO] (PT210).
     * @param empresa parámetros de empresa (nombre, CIF, cabecera/pie).
     * @param localNombre nombre del local físico.
     * @param localDireccion dirección opcional.
     * @param maquina máquina recaudada con su licencia y baseline.
     * @param tecnicoEmail email del técnico (para auditoría sobre el
     *        papel; en el PDF de archivo va el id Supabase).
     * @param fecha instante en que se cerró la recaudación.
     * @param contadorEntradasActual contador leído en este servicio.
     * @param contadorSalidasActual contador leído en este servicio.
     * @param cifras cifras calculadas (SSOT).
     * @param desgloseTotal desglose de monedas/billetes del bruto.
     * @param desgloseLocal desglose entregado al local.
     * @param firmaPng PNG de la firma (mismo que sube T-57). Vacío =
     *        se omite la sección visual de firma.
     */
    fun render(
        profile: PrinterProfile = PrinterProfiles.POR_DEFECTO,
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
    ): ByteArray {
        val cols = profile.cols
        val out = ByteArrayOutputStream()
        out.write(EscPos.INIT)

        cabecera(out, cols, empresa)
        seccion(out, cols, "RECAUDACION")

        val zona = runCatching { ZoneId.of(empresa?.zonaHoraria ?: "Europe/Madrid") }
            .getOrElse { ZoneId.of("Europe/Madrid") }
        keyValue(out, cols, "Fecha:", DATE_FORMAT.format(fecha.atZone(zona)))
        keyValue(out, cols, "Local:", localNombre.take(cols - 7))
        if (!localDireccion.isNullOrBlank()) {
            keyValue(out, cols, "Direcc.:", localDireccion.take(cols - 9))
        }
        val modelo = listOfNotNull(maquina.fabricante, maquina.modelo).joinToString(" ")
            .ifBlank { "-" }
        keyValue(out, cols, "Maquina:", "${maquina.numeroSerie} ($modelo)".take(cols - 9))
        keyValue(out, cols, "Licencia:", maquina.licenciaNumero)
        if (!tecnicoEmail.isNullOrBlank()) {
            keyValue(out, cols, "Tecnico:", tecnicoEmail.take(cols - 9))
        }

        out.write(EscPos.separador(cols))
        keyValue(out, cols, "Cont. Entradas:", "${maquina.baselineEntradas} -> $contadorEntradasActual")
        keyValue(out, cols, "Cont. Salidas:", "${maquina.baselineSalidas} -> $contadorSalidasActual")
        keyValue(out, cols, "Creditos netos:", cifras.creditos.toString())
        keyValue(out, cols, "Valor credito:", formatEur(cifras.valorCredito))

        out.write(EscPos.separador(cols))
        keyValue(out, cols, "Bruto:", formatEur(cifras.bruto))
        keyValue(out, cols, "Semanas tasa:", cifras.semanas.toString())
        keyValue(out, cols, "Tasa semanal:", formatEur(cifras.tasaSemanal))
        keyValue(out, cols, "Tasa total:", formatEur(cifras.tasaTotal))
        keyValue(out, cols, "Neto:", formatEur(cifras.neto))
        keyValue(out, cols, "% Local:", "${cifras.porcentajeLocal.stripTrailingZeros().toPlainString()}%")
        keyValue(out, cols, "Parte Local:", formatEur(cifras.parteLocal))
        keyValue(out, cols, "Parte Empresa:", formatEur(cifras.parteEmpresa))

        out.write(EscPos.separador(cols))
        if (desgloseTotal.isNotEmpty()) {
            out.write(EscPos.line("Desglose Total:"))
            for (item in desgloseTotal) {
                desgloseLine(out, cols, item)
            }
        }
        if (desgloseLocal.isNotEmpty()) {
            out.write(EscPos.line("Desglose Local:"))
            for (item in desgloseLocal) {
                desgloseLine(out, cols, item)
            }
        }

        out.write(EscPos.separador(cols))
        out.write(EscPos.line("Firma titular:"))
        out.write(EscPos.LINE_FEED)
        if (firmaPng.isNotEmpty()) {
            out.write(EscPos.ALIGN_CENTER)
            out.write(EscPos.rasterImage(firmaPng, profile.widthDots))
            out.write(EscPos.ALIGN_LEFT)
            out.write(EscPos.LINE_FEED)
        }

        pie(out, cols, empresa)
        finalizar(out, profile)
        return out.toByteArray()
    }

    // ----------------------------------------------------------- helpers internos

    /**
     * Cierra el ticket: avanza papel para que salga del cabezal y, si el
     * modelo tiene cuter mecánico, lanza el corte parcial. Los modelos
     * sin cuter (PT210) se arrancan a mano tras el avance.
     */
    private fun finalizar(out: ByteArrayOutputStream, profile: PrinterProfile) {
        out.write(EscPos.feedLines(profile.lineasFinales))
        if (profile.tieneCuter) {
            out.write(EscPos.CUT)
        }
    }

    private fun cabecera(out: ByteArrayOutputStream, cols: Int, empresa: EmpresaParamsEntity?) {
        if (empresa == null) return
        out.write(EscPos.ALIGN_CENTER)
        out.write(EscPos.BOLD_ON)
        out.write(EscPos.DOUBLE_HEIGHT)
        out.write(EscPos.line(empresa.nombre.take(cols)))
        out.write(EscPos.DOUBLE_OFF)
        out.write(EscPos.BOLD_OFF)
        if (!empresa.cif.isNullOrBlank()) {
            out.write(EscPos.line("CIF: ${empresa.cif}"))
        }
        if (!empresa.ticketCabecera.isNullOrBlank()) {
            for (linea in empresa.ticketCabecera.lines()) {
                if (linea.isNotBlank()) out.write(EscPos.line(linea.take(cols)))
            }
        }
        out.write(EscPos.ALIGN_LEFT)
    }

    private fun pie(out: ByteArrayOutputStream, cols: Int, empresa: EmpresaParamsEntity?) {
        out.write(EscPos.ALIGN_CENTER)
        val pie = empresa?.ticketPie?.takeIf { it.isNotBlank() } ?: "Gracias."
        for (linea in pie.lines()) {
            if (linea.isNotBlank()) out.write(EscPos.line(linea.take(cols)))
        }
        out.write(EscPos.ALIGN_LEFT)
    }

    private fun seccion(out: ByteArrayOutputStream, cols: Int, titulo: String) {
        out.write(EscPos.separador(cols))
        out.write(EscPos.BOLD_ON)
        out.write(EscPos.line(titulo))
        out.write(EscPos.BOLD_OFF)
    }

    private fun keyValue(out: ByteArrayOutputStream, cols: Int, etiqueta: String, valor: String) {
        out.write(EscPos.keyValue(cols, etiqueta, valor))
    }

    private fun desgloseLine(out: ByteArrayOutputStream, cols: Int, item: DenominacionItem) {
        if (item.cantidad <= 0) return
        val denom = formatEur(item.denominacion)
        val subtotal = formatEur(
            item.denominacion.multiply(BigDecimal(item.cantidad)).setScale(2, RoundingMode.HALF_UP),
        )
        // "  10€ x 4 = 40,00 EUR" — entradas con dos espacios para
        // simular sangría dentro de la sección "Desglose ...".
        out.write(EscPos.keyValue(cols, "  $denom x ${item.cantidad}", subtotal))
    }

    /**
     * Formatea un importe en céntimos con coma decimal, 2 decimales y
     * sufijo " EUR" (las térmicas no imprimen fiable el símbolo €, por
     * eso normalizamos a "EUR" al imprimir; en la pantalla y el PDF
     * sigue apareciendo el "€" porque renderizamos a Bitmap con la
     * fuente del sistema).
     */
    private fun formatEur(value: BigDecimal): String {
        val scaled = value.setScale(2, RoundingMode.HALF_UP)
        return "${scaled.toPlainString().replace('.', ',')} EUR"
    }
}
