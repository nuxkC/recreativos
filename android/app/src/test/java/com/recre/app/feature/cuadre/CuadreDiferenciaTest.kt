package com.recre.app.feature.cuadre

import com.recre.app.feature.cuadre.domain.VeredictoCuadre
import com.recre.app.feature.cuadre.domain.calcularDiferencia
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class CuadreDiferenciaTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `cuadra cuando contado iguala esperado`() {
        val esperado = mapOf(bd("50") to 1L, bd("2") to 5L)
        val contado = mapOf(bd("50") to 1L, bd("2") to 5L)
        val r = calcularDiferencia(esperado, contado)
        assertEquals(VeredictoCuadre.CUADRA, r.veredicto)
        assertEquals(0, r.diferencia.compareTo(BigDecimal.ZERO))
        assertEquals(0, r.totalEsperado.compareTo(bd("60.00")))
    }

    @Test
    fun `falta cuando contado es menor`() {
        // esperado 1×20 ; contado 0 -> faltan 20,00
        val r = calcularDiferencia(mapOf(bd("20") to 1L), emptyMap())
        assertEquals(VeredictoCuadre.FALTA, r.veredicto)
        assertEquals(0, r.diferencia.compareTo(bd("-20.00")))
        val linea20 = r.lineas.first { it.denominacion.compareTo(bd("20")) == 0 }
        assertEquals(-1L, linea20.delta)
    }

    @Test
    fun `sobra cuando contado es mayor`() {
        val r = calcularDiferencia(mapOf(bd("10") to 1L), mapOf(bd("10") to 3L))
        assertEquals(VeredictoCuadre.SOBRA, r.veredicto)
        assertEquals(0, r.diferencia.compareTo(bd("20.00")))
    }

    @Test
    fun `incluye todas las denominaciones aunque esten a cero`() {
        val r = calcularDiferencia(emptyMap(), emptyMap())
        assertEquals(9, r.lineas.size)
        assertEquals(VeredictoCuadre.CUADRA, r.veredicto)
    }
}
