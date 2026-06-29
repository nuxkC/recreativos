package com.recre.app.core.data.remote.dto

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CuadreDtosTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodifica_filas_de_la_vista() {
        val payload = """
            [
              {"empresa_id":"e1","tecnico_id":"t1","semana_inicio":"2026-06-22",
               "denominacion":"50.00","cantidad_neta":1,"importe_neto":"50.00","num_recaudaciones":2},
              {"empresa_id":"e1","tecnico_id":"t1","semana_inicio":"2026-06-22",
               "denominacion":"2.00","cantidad_neta":5,"importe_neto":"10.00","num_recaudaciones":2}
            ]
        """.trimIndent()
        val filas = json.decodeFromString<List<CuadreSemanalRow>>(payload)
        assertEquals(2, filas.size)
        assertEquals("50.00", filas[0].denominacion)
        assertEquals(1L, filas[0].cantidadNeta)
    }

    @Test
    fun mapea_filas_a_CuadreSemanal_con_total_agregado() {
        val filas = listOf(
            CuadreSemanalRow("e1", "t1", "2026-06-22", "50.00", 1, "50.00", 2),
            CuadreSemanalRow("e1", "t1", "2026-06-22", "2.00", 5, "10.00", 2),
        )
        val cuadre = filas.aCuadreSemanal(LocalDate.of(2026, 6, 22))
        assertEquals(2, cuadre.numRecaudaciones)
        assertEquals(0, cuadre.totalEsperado.compareTo(BigDecimal("60.00")))
        assertEquals(1L, cuadre.esperadoPorDenominacion[BigDecimal("50.00")])
    }
}
