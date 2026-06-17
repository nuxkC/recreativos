package com.recre.app.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Espaciado de marca: escala 4/8/12/16/24/32 (spec §3). */
class SpacingTest {
    @Test
    fun escala_4_8_12_16_24_32() {
        val s = RecreSpacing
        assertEquals(4.dp, s.xs)
        assertEquals(8.dp, s.sm)
        assertEquals(12.dp, s.md)
        assertEquals(16.dp, s.lg)
        assertEquals(24.dp, s.xl)
        assertEquals(32.dp, s.xxl)
    }
}
