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
 * en la AGPTEK PT210 al cerrar el flujo (T-62).
 *
 * El layout sigue [design.md §11] al pie de la letra. Se usa font A
 * (32 columnas) sin redimensionar el texto base — solo la cabecera
 * imprime el nombre de la empresa en doble altura para que el técnico
 * pueda leerla incluso si pierde el ticket entre otros papeles.
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
        val out = ByteArrayOutputStream()
        out.write(EscPos.INIT)

        cabecera(out, empresa)
        seccion(out, "RECAUDACION")

        val zona = runCatching { ZoneId.of(empresa?.zonaHoraria ?: "Europe/Madrid") }
            .getOrElse { ZoneId.of("Europe/Madrid") }
        keyValue(out, "Fecha:", DATE_FORMAT.format(fecha.atZone(zona)))
        keyValue(out, "Local:", localNombre.take(EscPos.COLS - 7))
        if (!localDireccion.isNullOrBlank()) {
            keyValue(out, "Direcc.:", localDireccion.take(EscPos.COLS - 9))
        }
        val modelo = listOfNotNull(maquina.fabricante, maquina.modelo).joinToString(" ")
            .ifBlank { "-" }
        keyValue(out, "Maquina:", "${maquina.numeroSerie} ($modelo)".take(EscPos.COLS - 9))
        keyValue(out, "Licencia:", maquina.licenciaNumero)
        if (!tecnicoEmail.isNullOrBlank()) {
            keyValue(out, "Tecnico:", tecnicoEmail.take(EscPos.COLS - 9))
        }

        out.write(EscPos.SEPARADOR)
        keyValue(out, "Cont. Entradas:", "${maquina.baselineEntradas} -> $contadorEntradasActual")
        keyValue(out, "Cont. Salidas:", "${maquina.baselineSalidas} -> $contadorSalidasActual")
        keyValue(out, "Creditos netos:", cifras.creditos.toString())
        keyValue(out, "Valor credito:", formatEur(cifras.valorCredito))

        out.write(EscPos.SEPARADOR)
        keyValue(out, "Bruto:", formatEur(cifras.bruto))
        keyValue(out, "Semanas tasa:", cifras.semanas.toString())
        keyValue(out, "Tasa semanal:", formatEur(cifras.tasaSemanal))
        keyValue(out, "Tasa total:", formatEur(cifras.tasaTotal))
        keyValue(out, "Neto:", formatEur(cifras.neto))
        keyValue(out, "% Local:", "${cifras.porcentajeLocal.stripTrailingZeros().toPlainString()}%")
        keyValue(out, "Parte Local:", formatEur(cifras.parteLocal))
        keyValue(out, "Parte Empresa:", formatEur(cifras.parteEmpresa))

        out.write(EscPos.SEPARADOR)
        if (desgloseTotal.isNotEmpty()) {
            out.write(EscPos.line("Desglose Total:"))
            for (item in desgloseTotal) {
                desgloseLine(out, item)
            }
        }
        if (desgloseLocal.isNotEmpty()) {
            out.write(EscPos.line("Desglose Local:"))
            for (item in desgloseLocal) {
                desgloseLine(out, item)
            }
        }

        out.write(EscPos.SEPARADOR)
        out.write(EscPos.line("Firma titular:"))
        out.write(EscPos.LINE_FEED)
        if (firmaPng.isNotEmpty()) {
            out.write(EscPos.ALIGN_CENTER)
            out.write(EscPos.rasterImage(firmaPng))
            out.write(EscPos.ALIGN_LEFT)
            out.write(EscPos.LINE_FEED)
        }

        pie(out, empresa)
        // Avance suficiente para que el ticket salga del cabezal y
        // pueda arrancarse a mano (la PT210 no tiene cuter mecánico).
        out.write(EscPos.feedLines(4))
        return out.toByteArray()
    }

    // ----------------------------------------------------------- helpers internos

    private fun cabecera(out: ByteArrayOutputStream, empresa: EmpresaParamsEntity?) {
        if (empresa == null) return
        out.write(EscPos.ALIGN_CENTER)
        out.write(EscPos.BOLD_ON)
        out.write(EscPos.DOUBLE_HEIGHT)
        out.write(EscPos.line(empresa.nombre.take(EscPos.COLS)))
        out.write(EscPos.DOUBLE_OFF)
        out.write(EscPos.BOLD_OFF)
        if (!empresa.cif.isNullOrBlank()) {
            out.write(EscPos.line("CIF: ${empresa.cif}"))
        }
        if (!empresa.ticketCabecera.isNullOrBlank()) {
            for (linea in empresa.ticketCabecera.lines()) {
                if (linea.isNotBlank()) out.write(EscPos.line(linea.take(EscPos.COLS)))
            }
        }
        out.write(EscPos.ALIGN_LEFT)
    }

    private fun pie(out: ByteArrayOutputStream, empresa: EmpresaParamsEntity?) {
        out.write(EscPos.ALIGN_CENTER)
        val pie = empresa?.ticketPie?.takeIf { it.isNotBlank() } ?: "Gracias."
        for (linea in pie.lines()) {
            if (linea.isNotBlank()) out.write(EscPos.line(linea.take(EscPos.COLS)))
        }
        out.write(EscPos.ALIGN_LEFT)
    }

    private fun seccion(out: ByteArrayOutputStream, titulo: String) {
        out.write(EscPos.SEPARADOR)
        out.write(EscPos.BOLD_ON)
        out.write(EscPos.line(titulo))
        out.write(EscPos.BOLD_OFF)
    }

    private fun keyValue(out: ByteArrayOutputStream, etiqueta: String, valor: String) {
        out.write(EscPos.keyValue(etiqueta, valor))
    }

    private fun desgloseLine(out: ByteArrayOutputStream, item: DenominacionItem) {
        if (item.cantidad <= 0) return
        val denom = formatEur(item.denominacion)
        val subtotal = formatEur(
            item.denominacion.multiply(BigDecimal(item.cantidad)).setScale(2, RoundingMode.HALF_UP),
        )
        // "  10€ x 4 = 40,00 EUR" — entradas con dos espacios para
        // simular sangría dentro de la sección "Desglose ...".
        out.write(EscPos.keyValue("  $denom x ${item.cantidad}", subtotal))
    }

    /**
     * Formatea un importe en céntimos con coma decimal, 2 decimales y
     * sufijo " EUR" (la PT210 no imprime fiable el símbolo €, por eso
     * normalizamos a "EUR" al imprimir; en la pantalla y el PDF sigue
     * apareciendo el "€" porque renderizamos a Bitmap con la fuente
     * del sistema).
     */
    private fun formatEur(value: BigDecimal): String {
        val scaled = value.setScale(2, RoundingMode.HALF_UP)
        return "${scaled.toPlainString().replace('.', ',')} EUR"
    }
}
