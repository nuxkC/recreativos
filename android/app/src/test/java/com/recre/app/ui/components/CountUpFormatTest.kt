package com.recre.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Formateo es-ES de la cifra de [CountUpText] (la parte con lógica; la animación no es unit-testeable). */
class CountUpFormatTest {
    @Test
    fun formatea_euros_es_ES_con_dos_decimales() {
        assertEquals("1.200,00", formatearImporteEs("1200.00"))
        assertEquals("0,00", formatearImporteEs("0"))
        assertEquals("1.234,56", formatearImporteEs("1234.56"))
    }
}
