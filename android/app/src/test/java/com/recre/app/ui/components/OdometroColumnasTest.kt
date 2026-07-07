package com.recre.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class OdometroColumnasTest {
    @Test
    fun `descompone digitos y fijos preservando el orden`() {
        val columnas = columnasOdometro("1.284,50 €")
        assertEquals(
            listOf(
                ColumnaOdometro.Digito(1), ColumnaOdometro.Fijo('.'),
                ColumnaOdometro.Digito(2), ColumnaOdometro.Digito(8), ColumnaOdometro.Digito(4),
                ColumnaOdometro.Fijo(','), ColumnaOdometro.Digito(5), ColumnaOdometro.Digito(0),
                ColumnaOdometro.Fijo(' '), ColumnaOdometro.Fijo('€'),
            ),
            columnas,
        )
    }

    @Test
    fun `cadena sin digitos produce solo fijos`() {
        assertEquals(
            listOf(ColumnaOdometro.Fijo('—')),
            columnasOdometro("—"),
        )
    }
}
