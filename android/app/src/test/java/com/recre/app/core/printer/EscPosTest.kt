package com.recre.app.core.printer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la lógica pura de formateo ESC/POS por perfil (T-105).
 *
 * Verifican que los helpers dependientes del ancho ([EscPos.separador],
 * [EscPos.keyValue]) producen líneas del ancho correcto según las
 * columnas del perfil (32 para 58 mm, 48 para 80 mm) y que los comandos
 * generados (corte, avance, normalización de acentos) son los esperados.
 * No tocan hardware ni Android: trabajan solo con `ByteArray`.
 */
class EscPosTest {

    private val LF: Byte = 0x0A

    /** Decodifica una línea ESC/POS quitando el salto final para medir su ancho. */
    private fun lineaSinLf(bytes: ByteArray): String {
        val sinLf = if (bytes.isNotEmpty() && bytes.last() == LF) {
            bytes.copyOf(bytes.size - 1)
        } else {
            bytes
        }
        return String(sinLf, Charsets.US_ASCII)
    }

    @Test
    fun `separador ocupa exactamente las columnas del perfil de 58mm`() {
        val sep = lineaSinLf(EscPos.separador(PrinterProfiles.PT210.cols))
        assertEquals(32, sep.length)
        assertTrue(sep.all { it == '-' })
    }

    @Test
    fun `separador ocupa exactamente las columnas del perfil de 80mm`() {
        val sep = lineaSinLf(EscPos.separador(PrinterProfiles.GENERICA_80.cols))
        assertEquals(48, sep.length)
        assertTrue(sep.all { it == '-' })
    }

    @Test
    fun `keyValue alinea el valor a la derecha rellenando hasta las columnas`() {
        val linea = lineaSinLf(EscPos.keyValue(32, "Bruto:", "10,00 EUR"))
        assertEquals(32, linea.length)
        assertTrue(linea.startsWith("Bruto:"))
        assertTrue(linea.endsWith("10,00 EUR"))
    }

    @Test
    fun `keyValue produce lineas mas anchas en 80mm que en 58mm`() {
        val linea58 = lineaSinLf(EscPos.keyValue(32, "Neto:", "5,00 EUR"))
        val linea80 = lineaSinLf(EscPos.keyValue(48, "Neto:", "5,00 EUR"))
        assertEquals(32, linea58.length)
        assertEquals(48, linea80.length)
        // El valor sigue pegado a la derecha en ambos anchos.
        assertTrue(linea58.endsWith("5,00 EUR"))
        assertTrue(linea80.endsWith("5,00 EUR"))
    }

    @Test
    fun `keyValue recorta la etiqueta para preservar el valor`() {
        val etiquetaLarga = "Etiqueta extremadamente larga que no cabe"
        val linea = lineaSinLf(EscPos.keyValue(32, etiquetaLarga, "999,99 EUR"))
        // El valor (lo importante) nunca se pierde.
        assertTrue(linea.endsWith("999,99 EUR"))
        // La etiqueta se trunca para no desbordar más allá de cols (+1 por
        // el espacio mínimo de separación).
        assertTrue(linea.length <= 33)
    }

    @Test
    fun `normalize convierte acentos y enie a ASCII`() {
        assertEquals("Maquina recaudacion", EscPos.normalize("Máquina recaudación"))
        assertEquals("Nino", EscPos.normalize("Niño"))
        assertEquals("E", EscPos.normalize("€"))
    }

    @Test
    fun `text codifica como ASCII normalizado`() {
        val bytes = EscPos.text("Tasa á")
        assertEquals("Tasa a", String(bytes, Charsets.US_ASCII))
    }

    @Test
    fun `CUT es el comando GS V corte parcial`() {
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0x01), EscPos.CUT)
    }

    @Test
    fun `feedLines genera ESC d n`() {
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 0x04), EscPos.feedLines(4))
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 0x02), EscPos.feedLines(2))
    }
}
