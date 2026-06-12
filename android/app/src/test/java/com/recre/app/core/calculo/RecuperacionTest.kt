package com.recre.app.core.calculo

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Replica los 8 casos de `supabase/functions/_shared/recuperacion.test.ts`
 * para garantizar que el espejo Kotlin ([planificarRecuperacion]) produce el
 * mismo plan que el SSOT TypeScript que ejecuta el servidor.
 *
 * Si la lógica del servidor cambia, este test fallará y obligará a actualizar
 * el espejo cliente al mismo tiempo (igual que [CalculoTest] / [SemanasIsoTest]).
 */
class RecuperacionTest {

    private fun bd(v: String) = BigDecimal(v)

    private fun tolva(id: String, saldo: String, fecha: String = "2026-03-01") =
        CreditoAbierto(id = id, tipo = "tolva", saldo = bd(saldo), fecha = fecha)

    private fun prestamo(id: String, saldo: String, fecha: String) =
        CreditoAbierto(id = id, tipo = "prestamo", saldo = bd(saldo), fecha = fecha)

    /** Compara importes ignorando la escala (`0` ≡ `0.00`). */
    private fun assertImporte(esperado: String, real: BigDecimal) =
        assertEquals(0, bd(esperado).compareTo(real))

    @Test
    fun `pct=0 no recupera nada`() {
        val plan = planificarRecuperacion(bd("100.00"), 0, listOf(tolva("t1", "100.00")))
        assertImporte("0.00", plan.recuperadoTotal)
        assertImporte("100.00", plan.pagadoLocal)
        assertEquals(emptyList<AsignacionRecuperacion>(), plan.asignaciones)
    }

    @Test
    fun `sin deudas pagado igual a parte_local aunque pct mayor que 0`() {
        val plan = planificarRecuperacion(bd("100.00"), 100, emptyList())
        assertImporte("0.00", plan.recuperadoTotal)
        assertImporte("100.00", plan.pagadoLocal)
    }

    @Test
    fun `100 por ciento con deuda suficiente el local no se lleva nada`() {
        val plan = planificarRecuperacion(bd("100.00"), 100, listOf(tolva("t1", "100.00")))
        assertImporte("100.00", plan.recuperadoTotal)
        assertImporte("0.00", plan.pagadoLocal)
        assertEquals(1, plan.asignaciones.size)
        assertEquals("t1", plan.asignaciones[0].creditoId)
        assertImporte("100.00", plan.asignaciones[0].importe)
    }

    @Test
    fun `50 por ciento retiene la mitad`() {
        val plan = planificarRecuperacion(bd("100.00"), 50, listOf(tolva("t1", "100.00")))
        assertImporte("50.00", plan.recuperadoTotal)
        assertImporte("50.00", plan.pagadoLocal)
    }

    @Test
    fun `se topa al saldo de la deuda`() {
        val plan = planificarRecuperacion(
            bd("100.00"),
            100,
            listOf(prestamo("p1", "30.00", "2026-01-01")),
        )
        assertImporte("30.00", plan.recuperadoTotal)
        assertImporte("70.00", plan.pagadoLocal)
    }

    @Test
    fun `imputa tolva primero luego prestamo aunque el prestamo sea mas antiguo`() {
        val plan = planificarRecuperacion(
            bd("200.00"),
            100,
            listOf(
                prestamo("p1", "100.00", "2026-01-01"),
                tolva("t1", "50.00", "2026-05-01"),
            ),
        )
        assertImporte("150.00", plan.recuperadoTotal)
        assertImporte("50.00", plan.pagadoLocal)
        assertEquals(listOf("t1", "p1"), plan.asignaciones.map { it.creditoId })
        assertImporte("50.00", plan.asignaciones[0].importe)
        assertImporte("100.00", plan.asignaciones[1].importe)
    }

    @Test
    fun `FIFO entre prestamos la deuda mas antigua primero`() {
        val plan = planificarRecuperacion(
            bd("100.00"),
            100,
            listOf(
                prestamo("nuevo", "80.00", "2026-05-01"),
                prestamo("viejo", "40.00", "2026-01-01"),
            ),
        )
        assertImporte("100.00", plan.recuperadoTotal)
        assertEquals(listOf("viejo", "nuevo"), plan.asignaciones.map { it.creditoId })
        assertImporte("40.00", plan.asignaciones[0].importe)
        assertImporte("60.00", plan.asignaciones[1].importe)
    }

    @Test
    fun `orden manual antepone los creditos indicados`() {
        val plan = planificarRecuperacion(
            bd("100.00"),
            100,
            listOf(
                prestamo("nuevo", "80.00", "2026-05-01"),
                prestamo("viejo", "40.00", "2026-01-01"),
            ),
            orden = listOf("nuevo"),
        )
        assertImporte("100.00", plan.recuperadoTotal)
        assertEquals(listOf("nuevo", "viejo"), plan.asignaciones.map { it.creditoId })
        assertImporte("80.00", plan.asignaciones[0].importe)
        assertImporte("20.00", plan.asignaciones[1].importe)
    }
}
