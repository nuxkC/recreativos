package com.recre.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Forma de marca: radios 12/16/20 (spec §3). */
class ShapeTest {
    @Test
    fun radios_de_marca_12_16_20() {
        assertEquals(RoundedCornerShape(12.dp), RecreShapes.small)
        assertEquals(RoundedCornerShape(16.dp), RecreShapes.medium)
        assertEquals(RoundedCornerShape(20.dp), RecreShapes.large)
    }

    @Test
    fun pill_es_50_por_ciento() {
        assertEquals(RoundedCornerShape(percent = 50), PillShape)
    }
}
