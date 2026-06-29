package com.recre.app.feature.cuadre

import com.recre.app.feature.cuadre.data.CuadreRecuentoStore
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class CuadreRecuentoStoreTest {

    private val store = CuadreRecuentoStore()

    @Test
    fun `ida y vuelta conserva denominaciones y cantidades`() {
        val original = mapOf(BigDecimal("50") to 1L, BigDecimal("2") to 5L)
        val json = store.serializar(original)
        val vuelta = store.deserializar(json)
        assertEquals(1L, vuelta[BigDecimal("50")])
        assertEquals(5L, vuelta[BigDecimal("2")])
    }

    @Test
    fun `deserializar vacio o invalido devuelve mapa vacio`() {
        assertEquals(emptyMap<BigDecimal, Long>(), store.deserializar(""))
        assertEquals(emptyMap<BigDecimal, Long>(), store.deserializar("no-json"))
    }
}
