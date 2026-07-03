package com.recre.app.feature.locales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DireccionLocalTest {

    @Test
    fun estructurada_muestra_calle_cp_ccaa() {
        assertEquals(
            "Rambla 1, 08002, Cantabria",
            formatearDireccionLocal("Rambla 1", "08002", "Cantabria"),
        )
    }

    @Test
    fun compone_solo_los_campos_presentes() {
        assertEquals("Madrid", formatearDireccionLocal(null, null, "Madrid"))
        assertEquals("Rambla 1, Madrid", formatearDireccionLocal("Rambla 1", "  ", "Madrid"))
    }

    @Test
    fun null_si_no_hay_nada() {
        assertNull(formatearDireccionLocal(null, null, null))
        assertNull(formatearDireccionLocal("  ", "", "  "))
    }
}
