package com.recre.app.feature.recaudacion.denominaciones

import org.junit.Assert.assertEquals
import org.junit.Test

/** Etiqueta facial de denominación: euros enteros sin decimales, sub-euro con coma. */
class DenominacionFormatoTest {
    @Test
    fun etiqueta_facial_monedas_y_billetes() {
        assertEquals("0,10 €", etiquetaFacialDenominacion("0.10"))
        assertEquals("0,20 €", etiquetaFacialDenominacion("0.20"))
        assertEquals("0,50 €", etiquetaFacialDenominacion("0.50"))
        assertEquals("1 €", etiquetaFacialDenominacion("1.00"))
        assertEquals("2 €", etiquetaFacialDenominacion("2.00"))
        assertEquals("5 €", etiquetaFacialDenominacion("5.00"))
        assertEquals("50 €", etiquetaFacialDenominacion("50.00"))
    }
}
