package com.recre.app.core.printer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream

/**
 * Constantes y helpers ESC/POS para impresoras térmicas de 58 mm.
 *
 * La AGPTEK PT210 es una impresora térmica BT (SPP) con ancho útil de
 * 58 mm = 384 dots = 32 columnas en font A 12×24 (lo que define el
 * "char width" estándar para tickets de la mayoría de los modelos
 * baratos de 58 mm). No tiene cuter mecánico, así que cerramos los
 * tickets con saltos de línea.
 *
 * Los comandos están descritos en la guía ESC/POS de Epson; la PT210
 * implementa el subset básico: `ESC @`, `ESC a n`, `ESC !`, `ESC E n`,
 * `GS V 0` y `GS v 0` para imágenes raster. No usamos paginación ni
 * códigos de barras.
 */
internal object EscPos {

    /** Ancho de impresión en dots para una térmica de 58 mm estándar. */
    const val WIDTH_DOTS: Int = 384

    /** Columnas en font A. */
    const val COLS: Int = 32

    // -------------------------------------------------------------- bytes ESC/POS

    private const val ESC: Byte = 0x1B
    private const val GS: Byte = 0x1D
    private const val LF: Byte = 0x0A

    /** `ESC @` — reset estado de la impresora (font, alineación, énfasis). */
    val INIT: ByteArray = byteArrayOf(ESC, 0x40)

    /** `LF` — nueva línea. */
    val LINE_FEED: ByteArray = byteArrayOf(LF)

    /** `ESC a 0/1/2` — alineación. */
    val ALIGN_LEFT: ByteArray = byteArrayOf(ESC, 0x61, 0x00)
    val ALIGN_CENTER: ByteArray = byteArrayOf(ESC, 0x61, 0x01)

    /** `ESC E n` — énfasis (negrita) on/off. */
    val BOLD_ON: ByteArray = byteArrayOf(ESC, 0x45, 0x01)
    val BOLD_OFF: ByteArray = byteArrayOf(ESC, 0x45, 0x00)

    /** `ESC ! n` — bit 4 = double-height, bit 5 = double-width. */
    val DOUBLE_OFF: ByteArray = byteArrayOf(ESC, 0x21, 0x00)
    val DOUBLE_HEIGHT: ByteArray = byteArrayOf(ESC, 0x21, 0x10)

    /** Avanza n líneas para que el ticket salga del rodillo. */
    fun feedLines(n: Int): ByteArray = byteArrayOf(ESC, 0x64, n.toByte())

    // -------------------------------------------------------------- texto

    /**
     * Codifica como CP437 (subset Latin sin acentos garantizados) y
     * normaliza los acentos del español a sus equivalentes ASCII para
     * que el firmware de la PT210 imprima correctamente. El firmware
     * "modo CP437" convierte 0xE1..0xFA a símbolos no-latinos, así que
     * normalizar antes de enviar evita la sorpresa al técnico.
     */
    fun text(s: String): ByteArray = normalize(s).toByteArray(Charsets.US_ASCII)

    /** Normaliza acentos / ñ a sus equivalentes ASCII. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    'á', 'à', 'ä', 'â' -> 'a'
                    'é', 'è', 'ë', 'ê' -> 'e'
                    'í', 'ì', 'ï', 'î' -> 'i'
                    'ó', 'ò', 'ö', 'ô' -> 'o'
                    'ú', 'ù', 'ü', 'û' -> 'u'
                    'Á', 'À', 'Ä', 'Â' -> 'A'
                    'É', 'È', 'Ë', 'Ê' -> 'E'
                    'Í', 'Ì', 'Ï', 'Î' -> 'I'
                    'Ó', 'Ò', 'Ö', 'Ô' -> 'O'
                    'Ú', 'Ù', 'Ü', 'Û' -> 'U'
                    'ñ' -> 'n'
                    'Ñ' -> 'N'
                    '€' -> 'E' // se acompaña de "EUR" en las cifras
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    /** Línea de separación de ancho [COLS] hecha de '-'. */
    val SEPARADOR: ByteArray = text("-".repeat(COLS)) + LINE_FEED

    /** Texto + LF. */
    fun line(s: String): ByteArray = text(s) + LINE_FEED

    /**
     * Combina `etiqueta` y `valor` en una sola línea, con `valor`
     * pegado a la derecha. Si la suma supera [COLS] caracteres, recorta
     * la etiqueta para preservar el valor (la cifra es lo importante).
     */
    fun keyValue(etiqueta: String, valor: String): ByteArray {
        val left = normalize(etiqueta)
        val right = normalize(valor)
        val totalDisponible = COLS - right.length
        val leftTrimmed = if (left.length > totalDisponible.coerceAtLeast(0)) {
            left.take(totalDisponible.coerceAtLeast(0))
        } else {
            left
        }
        val padding = (COLS - leftTrimmed.length - right.length).coerceAtLeast(1)
        return text(leftTrimmed + " ".repeat(padding) + right) + LINE_FEED
    }

    // -------------------------------------------------------------- imagen raster

    /**
     * Convierte un PNG (como el que produce [com.recre.app.core.calculo.FirmaRenderer])
     * en una secuencia `GS v 0 m xL xH yL yH d1...dn` que la PT210
     * interpreta como bitmap raster.
     *
     * Pasos:
     *  1. Decodificar el PNG.
     *  2. Escalarlo si es más ancho que el papel para que no se corte.
     *  3. Binarizar con umbral 128 (la firma es blanco/negro pura).
     *  4. Empaquetar 8 píxeles por byte, MSB = primer pixel.
     *
     * El parámetro `m` se queda en `0` (modo normal). `xL/xH` son el
     * ancho en bytes (= ceil(width / 8)). `yL/yH` la altura en filas.
     */
    fun rasterImage(png: ByteArray): ByteArray {
        val original = BitmapFactory.decodeByteArray(png, 0, png.size)
            ?: return ByteArray(0)
        val bitmap = if (original.width > WIDTH_DOTS) {
            val ratio = WIDTH_DOTS.toFloat() / original.width
            val newHeight = (original.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(original, WIDTH_DOTS, newHeight, true)
        } else {
            original
        }

        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        val out = ByteArrayOutputStream()
        // GS v 0 m xL xH yL yH
        out.write(GS.toInt())
        out.write(0x76)
        out.write(0x30)
        out.write(0x00)
        out.write(widthBytes and 0xFF)
        out.write((widthBytes ushr 8) and 0xFF)
        out.write(height and 0xFF)
        out.write((height ushr 8) and 0xFF)

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var packed = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    val on = if (x >= width) {
                        false
                    } else {
                        // Umbral simple: cualquier pixel con luminancia <128 es tinta.
                        val argb = bitmap.getPixel(x, y)
                        val r = Color.red(argb)
                        val g = Color.green(argb)
                        val b = Color.blue(argb)
                        val a = Color.alpha(argb)
                        // Si es transparente, lo tomamos como blanco.
                        if (a < 128) false else (r + g + b) / 3 < 128
                    }
                    if (on) packed = packed or (0x80 ushr bit)
                }
                out.write(packed)
            }
        }

        if (bitmap !== original) bitmap.recycle()
        original.recycle()
        return out.toByteArray()
    }
}
