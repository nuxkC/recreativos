package com.recre.app.core.printer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream

/**
 * Comandos y helpers ESC/POS comunes a todas las impresoras térmicas
 * soportadas (T-62, generalizado en T-105).
 *
 * Los comandos básicos (`ESC @`, `ESC a n`, `ESC !`, `ESC E n`, `GS V`
 * y `GS v 0` para imágenes raster) son idénticos en el subset ESC/POS
 * que implementan los modelos baratos de 58/80 mm, así que viven aquí
 * una sola vez (DRY). Lo que varía entre modelos —ancho en dots,
 * columnas de texto, cuter— se parametriza vía [PrinterProfile]: los
 * helpers dependientes del ancho ([separador], [keyValue], [rasterImage])
 * reciben `cols`/`widthDots` en lugar de asumir 58 mm.
 *
 * No usamos paginación ni códigos de barras.
 */
internal object EscPos {

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

    /**
     * `GS V 1` — corte parcial. Solo lo usan los perfiles con cuter
     * mecánico ([PrinterProfile.tieneCuter]); los demás avanzan papel
     * con [feedLines] para arrancarlo a mano.
     */
    val CUT: ByteArray = byteArrayOf(GS, 0x56, 0x01)

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

    /** Línea de separación de ancho [cols] hecha de '-'. */
    fun separador(cols: Int): ByteArray = text("-".repeat(cols)) + LINE_FEED

    /** Texto + LF. */
    fun line(s: String): ByteArray = text(s) + LINE_FEED

    /**
     * Combina `etiqueta` y `valor` en una sola línea de `cols` columnas,
     * con `valor` pegado a la derecha. Si la suma supera `cols`
     * caracteres, recorta la etiqueta para preservar el valor (la cifra
     * es lo importante).
     */
    fun keyValue(cols: Int, etiqueta: String, valor: String): ByteArray {
        val left = normalize(etiqueta)
        val right = normalize(valor)
        val totalDisponible = cols - right.length
        val leftTrimmed = if (left.length > totalDisponible.coerceAtLeast(0)) {
            left.take(totalDisponible.coerceAtLeast(0))
        } else {
            left
        }
        val padding = (cols - leftTrimmed.length - right.length).coerceAtLeast(1)
        return text(leftTrimmed + " ".repeat(padding) + right) + LINE_FEED
    }

    // -------------------------------------------------------------- imagen raster

    /**
     * Convierte un PNG (como el que produce [com.recre.app.core.calculo.FirmaRenderer])
     * en una secuencia `GS v 0 m xL xH yL yH d1...dn` que la impresora
     * interpreta como bitmap raster.
     *
     * Pasos:
     *  1. Decodificar el PNG.
     *  2. Escalarlo si es más ancho que `widthDots` para que no se corte
     *     (cada perfil define su ancho útil: 384 dots a 58 mm, 576 a 80 mm).
     *  3. Binarizar con umbral 128 (la firma es blanco/negro pura).
     *  4. Empaquetar 8 píxeles por byte, MSB = primer pixel.
     *
     * El parámetro `m` se queda en `0` (modo normal). `xL/xH` son el
     * ancho en bytes (= ceil(width / 8)). `yL/yH` la altura en filas.
     */
    fun rasterImage(png: ByteArray, widthDots: Int): ByteArray {
        val original = BitmapFactory.decodeByteArray(png, 0, png.size)
            ?: return ByteArray(0)
        val bitmap = if (original.width > widthDots) {
            val ratio = widthDots.toFloat() / original.width
            val newHeight = (original.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(original, widthDots, newHeight, true)
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
