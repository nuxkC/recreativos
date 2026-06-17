package com.recre.app.feature.locales

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Recaudar todas" (cadena) solo se ofrece con 2+ máquinas instaladas. */
class RecaudarTodasVisibleTest {
    @Test
    fun solo_visible_con_dos_o_mas_instaladas() {
        assertEquals(false, mostrarRecaudarTodas(0))
        assertEquals(false, mostrarRecaudarTodas(1))
        assertEquals(true, mostrarRecaudarTodas(2))
        assertEquals(true, mostrarRecaudarTodas(5))
    }
}
