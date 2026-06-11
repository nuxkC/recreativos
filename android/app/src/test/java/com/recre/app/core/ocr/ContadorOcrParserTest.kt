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

    // -------------------------------------------------------------------------
    // parseAmbos: identifica ambos contadores en una sola foto (HU-14 fase 2)
    // -------------------------------------------------------------------------

    @Test
    fun `parseAmbos detecta entradas y salidas con confianza alta`() {
        val r = ContadorOcrParser.parseAmbos(
            "ENTRADAS 12345 SALIDAS 10000",
            baselineEntradas = 10000,
            baselineSalidas = 8000,
        )

        assertEquals(12345L, r.entradas)
        assertEquals(10000L, r.salidas)
        assertEquals(Confianza.ALTA, r.confianza)
    }

    @Test
    fun `parseAmbos asigna el mayor a entradas y el menor a salidas`() {
        // El orden en el texto no importa: entradas es siempre el mayor.
        val r = ContadorOcrParser.parseAmbos("9000 12345", baselineEntradas = 0, baselineSalidas = 0)

        assertEquals(12345L, r.entradas)
        assertEquals(9000L, r.salidas)
    }

    @Test
    fun `parseAmbos descarta como salidas un numero por debajo del 70 por ciento`() {
        // 5000 < 70% de 12345 (8641,5): no puede ser el contador de salidas.
        val r = ContadorOcrParser.parseAmbos("12345 5000", baselineEntradas = 0, baselineSalidas = 0)

        assertEquals(12345L, r.entradas)
        assertNull(r.salidas)
        assertEquals(Confianza.BAJA, r.confianza)
    }

    @Test
    fun `parseAmbos excluye como entradas un numero por debajo de su baseline`() {
        // 9000 < baselineEntradas(10000): no es entradas; sí es una salida válida.
        val r = ContadorOcrParser.parseAmbos(
            "11000 9000",
            baselineEntradas = 10000,
            baselineSalidas = 8000,
        )

        assertEquals(11000L, r.entradas)
        assertEquals(9000L, r.salidas)
        assertEquals(Confianza.ALTA, r.confianza)
    }

    @Test
    fun `parseAmbos con varias salidas plausibles da confianza baja`() {
        val r = ContadorOcrParser.parseAmbos("12000 10000 9000", baselineEntradas = 0, baselineSalidas = 0)

        assertEquals(12000L, r.entradas)
        // El mayor candidato válido es la salida elegida, pero es ambiguo.
        assertEquals(10000L, r.salidas)
        assertEquals(Confianza.BAJA, r.confianza)
    }

    @Test
    fun `parseAmbos sin numeros plausibles no detecta nada`() {
        val r = ContadorOcrParser.parseAmbos("ERROR display", baselineEntradas = 0, baselineSalidas = 0)

        assertNull(r.entradas)
        assertNull(r.salidas)
        assertEquals(Confianza.NINGUNA, r.confianza)
    }

    @Test
    fun `parseAmbos con un solo contador detecta entradas y pide revision`() {
        val r = ContadorOcrParser.parseAmbos("12345", baselineEntradas = 0, baselineSalidas = 0)

        assertEquals(12345L, r.entradas)
        assertNull(r.salidas)
        assertEquals(Confianza.BAJA, r.confianza)
    }
}
