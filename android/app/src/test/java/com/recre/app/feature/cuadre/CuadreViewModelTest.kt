package com.recre.app.feature.cuadre

import com.recre.app.feature.cuadre.domain.CuadreSemanal
import com.recre.app.feature.cuadre.domain.VeredictoCuadre
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test del motor de estado del cuadre.
 *
 * La lógica de decisión vive en la función pura [construirEstado], extraída del
 * ViewModel para poder probarla sin Hilt ni los `final` no-fakeables
 * ([CuadreRepository], [RealtimeManager]). El VM se limita a cablear las fuentes
 * y delegar la composición aquí, así que probando las tres transiciones
 * (bloqueado / vacío / listo-con-diferencia) se cubre el comportamiento real.
 */
class CuadreViewModelTest {

    private val semana: LocalDate = LocalDate.of(2026, 6, 29)

    @Test
    fun `pendientes bloquean y separan reintentables de fallidas`() {
        val estado = construirEstado(
            cuadre = CuadreSemanal(semana, 1, BigDecimal("20"), mapOf(BigDecimal("20") to 1L)),
            contado = emptyMap(),
            pendientes = 4,
            fallidas = 1,
            semana = semana,
        )
        assertTrue(estado is CuadreUiState.BloqueadoPorPendientes)
        estado as CuadreUiState.BloqueadoPorPendientes
        assertEquals(3, estado.reintentables)
        assertEquals(1, estado.fallidas)
    }

    @Test
    fun `sin recaudaciones la semana esta vacia`() {
        val estado = construirEstado(
            cuadre = CuadreSemanal(semana, 0, BigDecimal.ZERO, emptyMap()),
            contado = emptyMap(),
            pendientes = 0,
            fallidas = 0,
            semana = semana,
        )
        assertTrue(estado is CuadreUiState.Vacio)
        assertEquals(semana, (estado as CuadreUiState.Vacio).semana)
    }

    @Test
    fun `listo muestra diferencia al contar de menos`() {
        val estado = construirEstado(
            cuadre = CuadreSemanal(semana, 2, BigDecimal("20"), mapOf(BigDecimal("20") to 1L)),
            contado = emptyMap(),
            pendientes = 0,
            fallidas = 0,
            semana = semana,
        )
        assertTrue(estado is CuadreUiState.Listo)
        estado as CuadreUiState.Listo
        assertEquals(2, estado.numRecaudaciones)
        assertEquals(VeredictoCuadre.FALTA, estado.diferencia.veredicto)
    }
}
