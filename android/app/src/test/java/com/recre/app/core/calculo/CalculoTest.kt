package com.recre.app.core.calculo

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replica los 8 casos del test Deno `_shared/calculo.test.ts` para
 * garantizar que la implementación Kotlin produce los mismos importes
 * que la Edge Function `crear-recaudacion`.
 *
 * Reglas verificadas (`design.md §5`):
 *   1. flujo normal procede=true con cifras esperadas
 *   2. bruto<tasa → procede=false con neto=parteLocal=parteEmpresa=0
 *   3. redondeo HALF_UP absorbido por la empresa (parte_local + parte_empresa = neto)
 *   4. half-up edge case en céntimos
 *   5. créditos negativos (Δsalidas > Δentradas) producen bruto negativo y procede=false
 *   6. desglose de denominaciones cuadra con bruto
 *   7. importesIguales ignora la escala
 *   8. sumarDesglose con cantidades 0 da 0.00
 */
class CalculoTest {

    @Test
    fun `flujo normal calcula bruto neto y reparto`() {
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 1000,
                baselineSalidas = 200,
                contadorEntradasActual = 1100,
                contadorSalidasActual = 220,
                valorCredito = bd("0.20"),
                tasaSemanal = bd("10.00"),
                porcentajeLocal = bd("50.00"),
                semanas = 2,
            ),
        )

        // 100 - 20 = 80 créditos × 0.20 = 16.00 bruto
        // tasa_total = 10 × 2 = 20.00 → bruto < tasa
        // Espera: procede=false porque bruto<tasa
        assertFalse(cifras.procede)
        assertEquals(bd("16.00"), cifras.bruto)
        assertEquals(bd("20.00"), cifras.tasaTotal)
        assertEquals(bd("0.00"), cifras.neto)
        assertEquals(bd("0.00"), cifras.parteLocal)
        assertEquals(bd("0.00"), cifras.parteEmpresa)
    }

    @Test
    fun `flujo normal con bruto mayor que tasa`() {
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 1000,
                baselineSalidas = 200,
                contadorEntradasActual = 1500,
                contadorSalidasActual = 220,
                valorCredito = bd("0.20"),
                tasaSemanal = bd("10.00"),
                porcentajeLocal = bd("50.00"),
                semanas = 2,
            ),
        )
        // 500 - 20 = 480 créditos × 0.20 = 96.00 bruto
        // tasa_total = 20.00 → neto = 76.00, mitad = 38.00
        assertTrue(cifras.procede)
        assertEquals(bd("96.00"), cifras.bruto)
        assertEquals(bd("76.00"), cifras.neto)
        assertEquals(bd("38.00"), cifras.parteLocal)
        assertEquals(bd("38.00"), cifras.parteEmpresa)
    }

    @Test
    fun `bruto menor que tasa devuelve no procede con neto cero`() {
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 0, baselineSalidas = 0,
                contadorEntradasActual = 10, contadorSalidasActual = 0,
                valorCredito = bd("0.20"),
                tasaSemanal = bd("5.00"),
                porcentajeLocal = bd("50.00"),
                semanas = 2,
            ),
        )
        // 10 créditos × 0.20 = 2.00 bruto vs 10.00 tasa
        assertFalse(cifras.procede)
        assertEquals(bd("2.00"), cifras.bruto)
        assertEquals(bd("0.00"), cifras.neto)
    }

    @Test
    fun `redondeo HALF_UP absorbido por la empresa`() {
        // neto = 1.01, % local = 50% → 0.505 → HALF_UP → parte_local = 0.51
        // parte_empresa = 1.01 - 0.51 = 0.50 (la empresa absorbe el centavo)
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 0, baselineSalidas = 0,
                contadorEntradasActual = 1, contadorSalidasActual = 0,
                valorCredito = bd("2.00"),
                tasaSemanal = bd("0.99"),
                porcentajeLocal = bd("50.00"),
                semanas = 1,
            ),
        )
        // 1 × 2.00 = 2.00 bruto - 0.99 tasa = 1.01 neto
        assertTrue(cifras.procede)
        assertEquals(bd("2.00"), cifras.bruto)
        assertEquals(bd("1.01"), cifras.neto)
        assertEquals(bd("0.51"), cifras.parteLocal)
        assertEquals(bd("0.50"), cifras.parteEmpresa)
        // Suma debe igualar neto exacto, sin pérdida ni ganancia
        assertEquals(cifras.neto, cifras.parteLocal.add(cifras.parteEmpresa))
    }

    @Test
    fun `half-up edge case 0_005`() {
        // neto = 0.01, % = 50 → parte_local = 0.01 (half-up de 0.005)
        // parte_empresa = 0.00
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 0, baselineSalidas = 0,
                contadorEntradasActual = 1, contadorSalidasActual = 0,
                valorCredito = bd("0.10"),
                tasaSemanal = bd("0.09"),
                porcentajeLocal = bd("50.00"),
                semanas = 1,
            ),
        )
        assertTrue(cifras.procede)
        assertEquals(bd("0.10"), cifras.bruto)
        assertEquals(bd("0.01"), cifras.neto)
        assertEquals(bd("0.01"), cifras.parteLocal)
        assertEquals(bd("0.00"), cifras.parteEmpresa)
    }

    @Test
    fun `creditos negativos no proceden y bruto es negativo`() {
        // Δentradas = 5, Δsalidas = 10 → créditos = -5 → bruto = -1.00
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 0, baselineSalidas = 0,
                contadorEntradasActual = 5, contadorSalidasActual = 10,
                valorCredito = bd("0.20"),
                tasaSemanal = bd("0.00"),
                porcentajeLocal = bd("50.00"),
                semanas = 1,
            ),
        )
        assertFalse(cifras.procede)
        assertEquals(bd("-1.00"), cifras.bruto)
        assertEquals(-5L, cifras.creditos)
    }

    @Test
    fun `sumarDesglose acumula en BigDecimal`() {
        val total = sumarDesglose(
            listOf(
                DenominacionItem(bd("0.10"), 5),    // 0.50
                DenominacionItem(bd("0.20"), 3),    // 0.60
                DenominacionItem(bd("1.00"), 2),    // 2.00
                DenominacionItem(bd("5.00"), 1),    // 5.00
            ),
        )
        assertEquals(bd("8.10"), total)
    }

    @Test
    fun `sumarDesglose con todas cantidades cero devuelve cero`() {
        val total = sumarDesglose(
            DENOMINACIONES_PERMITIDAS.map { DenominacionItem(it, 0) },
        )
        assertEquals(bd("0.00"), total)
    }

    @Test
    fun `importesIguales ignora la escala`() {
        assertTrue(importesIguales(BigDecimal("1.00"), BigDecimal("1")))
        assertTrue(importesIguales(BigDecimal("1.10"), BigDecimal("1.1")))
        assertFalse(importesIguales(BigDecimal("1.00"), BigDecimal("1.01")))
    }

    private fun bd(value: String): BigDecimal = BigDecimal(value).setScale(2)
}
