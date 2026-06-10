package com.recre.app.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la lógica pura de parseo de dígitos del OCR de contadores (T-100).
 *
 * Verifica la extracción del número del contador a partir del texto crudo de
 * ML Kit, incluyendo ruido típico de un display (etiquetas, separadores de
 * millar, ceros a la izquierda) y la estimación de confianza heurística.
 */
class ContadorOcrParserTest {

    @Test
    fun `un unico numero claro produce ese valor con confianza alta`() {
        val r = ContadorOcrParser.parse("012345")

        assertEquals(12345L, r.mejor)
        assertEquals(Confianza.ALTA, r.confianza)
        assertEquals(listOf(12345L), r.candidatos)
    }

    @Test
    fun `ignora separadores de millar dentro del numero`() {
        val r = ContadorOcrParser.parse("12.345")

        assertEquals(12345L, r.mejor)
        assertEquals(Confianza.ALTA, r.confianza)
    }

    @Test
    fun `une separador de millar con coma`() {
        assertEquals(1234567L, ContadorOcrParser.parse("1,234,567").mejor)
    }

    @Test
    fun `el espacio separa lecturas distintas, no une`() {
        val r = ContadorOcrParser.parse("1 234 567")

        // Tres grupos independientes; el más largo (3 dígitos) gana por valor.
        assertEquals(567L, r.mejor)
        assertTrue(r.candidatos.containsAll(listOf(1L, 234L, 567L)))
    }

    @Test
    fun `descarta etiquetas y se queda con el numero del display`() {
        val r = ContadorOcrParser.parse("IN 004812 OUT")

        assertEquals(4812L, r.mejor)
        assertEquals(Confianza.ALTA, r.confianza)
    }

    @Test
    fun `elige el numero mas largo como contador`() {
        // El display muestra el modelo "PT-21" (corto) y el contador largo.
        val r = ContadorOcrParser.parse("PT 21\n0098765")

        assertEquals(98765L, r.mejor)
        assertEquals(Confianza.ALTA, r.confianza)
    }

    @Test
    fun `varios numeros de igual longitud maxima dan confianza baja`() {
        val r = ContadorOcrParser.parse("12345 67890")

        // Ambos tienen 5 dígitos: ambiguo.
        assertEquals(Confianza.BAJA, r.confianza)
        // El mejor por desempate de valor es el mayor.
        assertEquals(67890L, r.mejor)
        assertTrue(r.candidatos.containsAll(listOf(12345L, 67890L)))
    }

    @Test
    fun `texto sin digitos no produce candidato`() {
        val r = ContadorOcrParser.parse("ERROR display")

        assertNull(r.mejor)
        assertEquals(Confianza.NINGUNA, r.confianza)
        assertTrue(r.candidatos.isEmpty())
    }

    @Test
    fun `texto vacio no produce candidato`() {
        val r = ContadorOcrParser.parse("")

        assertNull(r.mejor)
        assertEquals(Confianza.NINGUNA, r.confianza)
    }

    @Test
    fun `un solo digito da confianza baja`() {
        val r = ContadorOcrParser.parse("7")

        assertEquals(7L, r.mejor)
        assertEquals(Confianza.BAJA, r.confianza)
    }

    @Test
    fun `descarta numeros mas largos que el maximo permitido`() {
        // 13 dígitos: fuera del rango plausible de un contador.
        val r = ContadorOcrParser.parse("1234567890123")

        assertNull(r.mejor)
        assertEquals(Confianza.NINGUNA, r.confianza)
    }

    @Test
    fun `numero claramente mas largo gana con confianza alta pese a otros candidatos`() {
        val r = ContadorOcrParser.parse("12 0098765")

        assertEquals(98765L, r.mejor)
        assertEquals(Confianza.ALTA, r.confianza)
        // El más corto sigue como candidato secundario.
        assertEquals(listOf(98765L, 12L), r.candidatos)
    }

    @Test
    fun `ceros a la izquierda se normalizan al valor entero`() {
        val r = ContadorOcrParser.parse("000042")

        assertEquals(42L, r.mejor)
    }
}
