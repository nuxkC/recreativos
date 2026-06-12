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

    // -------------------------------------------------------------------------
    // Redondeo del bruto (T-211). Espejo de los casos de _shared/calculo.test.ts:
    // garantiza que el preview/offline en Android cuadra con el servidor.
    // -------------------------------------------------------------------------

    @Test
    fun `redondeo a la baja 234,20 a 230`() {
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 0, baselineSalidas = 0,
                contadorEntradasActual = 2000, contadorSalidasActual = 829,
                valorCredito = bd("0.20"),
                tasaSemanal = bd("0.00"),
                porcentajeLocal = bd("50.00"),
                semanas = 0,
                redondeoUnidad = 10,
            ),
        )
        // creditos = 1171 -> bruto real 234.20 -> redondeado 230.00
        assertTrue(cifras.procede)
        assertEquals(bd("230.00"), cifras.bruto)
        assertEquals(bd("234.20"), cifras.brutoReal)
        assertEquals(10, cifras.redondeoAplicado)
        assertEquals(bd("115.00"), cifras.parteLocal)
        assertEquals(bd("115.00"), cifras.parteEmpresa)
    }

    @Test
    fun `redondeo al alza 237,80 a 240`() {
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 0, baselineSalidas = 0,
                contadorEntradasActual = 2000, contadorSalidasActual = 811,
                valorCredito = bd("0.20"),
                tasaSemanal = bd("0.00"),
                porcentajeLocal = bd("50.00"),
                semanas = 0,
                redondeoUnidad = 10,
            ),
        )
        // creditos = 1189 -> bruto real 237.80 -> redondeado 240.00
        assertEquals(bd("240.00"), cifras.bruto)
        assertEquals(bd("237.80"), cifras.brutoReal)
        assertEquals(10, cifras.redondeoAplicado)
    }

    @Test
    fun `el redondeo nunca deja el neto negativo`() {
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 1000, baselineSalidas = 1000,
                contadorEntradasActual = 1365, contadorSalidasActual = 1000,
                valorCredito = bd("0.20"),
                tasaSemanal = bd("72.00"),
                porcentajeLocal = bd("50.00"),
                semanas = 1,
                redondeoUnidad = 10,
            ),
        )
        // bruto real 73.00, tasa 72: nearest=70 < 72 -> ceil = 80.
        assertTrue(cifras.procede)
        assertEquals(bd("80.00"), cifras.bruto)
        assertEquals(bd("8.00"), cifras.neto)
    }

    @Test
    fun `sin redondeo el bruto no cambia y redondeoAplicado es cero`() {
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 0, baselineSalidas = 0,
                contadorEntradasActual = 2000, contadorSalidasActual = 829,
                valorCredito = bd("0.20"),
                tasaSemanal = bd("0.00"),
                porcentajeLocal = bd("50.00"),
                semanas = 0,
            ),
        )
        assertEquals(bd("234.20"), cifras.bruto)
        assertEquals(0, cifras.redondeoAplicado)
    }

    // -------------------------------------------------------------------------
    // Reposición de tolva por avería ANTES del reparto (T-224, §5.6). Espejo
    // bit-a-bit de los casos de _shared/calculo.test.ts.
    // -------------------------------------------------------------------------

    /** Entrada base: bruto 100, tasa 0, neto 100, % local 50 (varía pendienteTolva). */
    private fun inputNeto100(pendienteTolva: BigDecimal) = CalcularInput(
        baselineEntradas = 0, baselineSalidas = 0,
        contadorEntradasActual = 500, contadorSalidasActual = 0,
        valorCredito = bd("0.20"),
        tasaSemanal = bd("0.00"),
        porcentajeLocal = bd("50.00"),
        semanas = 0,
        pendienteTolva = pendienteTolva,
    )

    @Test
    fun `tolva repone min(neto,pendiente) antes del reparto`() {
        // neto 100, pendiente 50 → reposicion 50, base 50, mitad 25/25.
        val cifras = calcularRecaudacion(inputNeto100(bd("50.00")))
        assertTrue(cifras.procede)
        assertEquals(bd("100.00"), cifras.neto)
        assertEquals(bd("50.00"), cifras.reposicionTolva)
        assertEquals(bd("50.00"), cifras.baseReparto)
        assertEquals(bd("25.00"), cifras.parteLocal)
        assertEquals(bd("25.00"), cifras.parteEmpresa)
        // Invariante: reposicion + parte_local + parte_empresa = neto.
        assertEquals(cifras.neto, cifras.reposicionTolva.add(cifras.parteLocal).add(cifras.parteEmpresa))
    }

    @Test
    fun `tolva se topa al neto cuando el pendiente lo supera`() {
        // pendiente 200 > neto 100 → reposicion 100, base 0, reparto 0/0.
        val cifras = calcularRecaudacion(inputNeto100(bd("200.00")))
        assertTrue(cifras.procede)
        assertEquals(bd("100.00"), cifras.reposicionTolva)
        assertEquals(bd("0.00"), cifras.baseReparto)
        assertEquals(bd("0.00"), cifras.parteLocal)
        assertEquals(bd("0.00"), cifras.parteEmpresa)
    }

    @Test
    fun `tolva parcial reparte el resto`() {
        // pendiente 30, neto 100 → reposicion 30, base 70, mitad 35/35.
        val cifras = calcularRecaudacion(inputNeto100(bd("30.00")))
        assertEquals(bd("30.00"), cifras.reposicionTolva)
        assertEquals(bd("70.00"), cifras.baseReparto)
        assertEquals(bd("35.00"), cifras.parteLocal)
        assertEquals(bd("35.00"), cifras.parteEmpresa)
    }

    @Test
    fun `sin pendiente de tolva el reparto es el historico`() {
        // pendiente 0 (default) → reposicion 0, base = neto, reparto 50/50.
        val cifras = calcularRecaudacion(inputNeto100(BigDecimal.ZERO))
        assertEquals(bd("0.00"), cifras.reposicionTolva)
        assertEquals(bd("100.00"), cifras.baseReparto)
        assertEquals(bd("50.00"), cifras.parteLocal)
        assertEquals(bd("50.00"), cifras.parteEmpresa)
    }

    @Test
    fun `tolva no procede arrastra reposicion cero`() {
        // bruto < tasa → procede=false, reposicion 0 aunque haya pendiente.
        val cifras = calcularRecaudacion(
            CalcularInput(
                baselineEntradas = 0, baselineSalidas = 0,
                contadorEntradasActual = 10, contadorSalidasActual = 0,
                valorCredito = bd("0.20"),
                tasaSemanal = bd("5.00"),
                porcentajeLocal = bd("50.00"),
                semanas = 2,
                pendienteTolva = bd("50.00"),
            ),
        )
        assertFalse(cifras.procede)
        assertEquals(bd("0.00"), cifras.reposicionTolva)
        assertEquals(bd("0.00"), cifras.baseReparto)
    }

    private fun bd(value: String): BigDecimal = BigDecimal(value).setScale(2)
}
