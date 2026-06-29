package com.recre.app.feature.gestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vectores oro de CIF/NIF y teléfono: deben coincidir con web/src/lib/shared/validators.test.ts. */
class GestionValidadoresTest {

    @Test
    fun cifNif_validos() {
        listOf("12345678Z", "00000000T", " 12345678-z ", "X1234567L", "Y1234567X", "A58818501", "P1234567D")
            .forEach { assertTrue("esperaba valido: $it", esCifNif(it)) }
    }

    @Test
    fun cifNif_invalidos() {
        listOf("12345678A", "X1234567A", "A58818500", "P1234567A", "1234")
            .forEach { assertFalse("esperaba invalido: $it", esCifNif(it)) }
    }

    @Test
    fun telefono_validos() {
        listOf("612345678", "912345678", "+34 612 345 678", "0034612345678")
            .forEach { assertTrue("esperaba valido: $it", esTelefono(it)) }
    }

    @Test
    fun telefono_invalidos() {
        listOf("512345678", "61234567", "61234567a", "1234")
            .forEach { assertFalse("esperaba invalido: $it", esTelefono(it)) }
    }

    @Test
    fun cifNif_paridad_con_email_existente() {
        // sanity: el helper no rompe el módulo existente
        assertEquals(true, esEmailValido("a@b.com"))
    }
}
