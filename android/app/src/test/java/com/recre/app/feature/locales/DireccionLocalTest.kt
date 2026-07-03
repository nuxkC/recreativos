package com.recre.app.feature.locales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DireccionLocalTest {

    @Test
    fun estructurada_muestra_calle_cp_ccaa() {
        assertEquals(
            "Rambla 1, 08002, Cataluña",
            formatearDireccionLocal("Rambla 1", "08002", "Cataluña", null),
        )
    }

    @Test
    fun estructura_tiene_prioridad_sobre_texto_libre() {
        assertEquals(
            "Madrid",
            formatearDireccionLocal(null, null, "Madrid", "texto libre ignorado"),
        )
    }

    @Test
    fun cae_al_texto_libre_sin_estructura() {
        assertEquals(
            "Calle Vieja 1",
            formatearDireccionLocal(null, "  ", null, "Calle Vieja 1"),
        )
    }

    @Test
    fun null_si_no_hay_nada() {
        assertNull(formatearDireccionLocal(null, null, null, null))
        assertNull(formatearDireccionLocal("  ", "", "  ", "   "))
    }
}
