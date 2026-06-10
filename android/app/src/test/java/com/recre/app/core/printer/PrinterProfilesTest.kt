package com.recre.app.core.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del catálogo de perfiles y de la resolución de ids persistidos
 * (T-105). Es la lógica que decide qué formato de ticket se usa y cuándo
 * emitir [PrinterError.ModeloNoSoportado].
 */
class PrinterProfilesTest {

    @Test
    fun `el perfil por defecto es la PT210 para no romper T-62`() {
        assertSame(PrinterProfiles.PT210, PrinterProfiles.POR_DEFECTO)
        assertEquals(PrinterModelId.PT210, PrinterProfiles.POR_DEFECTO.id)
        assertEquals(32, PrinterProfiles.POR_DEFECTO.cols)
        assertEquals(384, PrinterProfiles.POR_DEFECTO.widthDots)
        assertEquals(false, PrinterProfiles.POR_DEFECTO.tieneCuter)
    }

    @Test
    fun `resolver devuelve null cuando el id es nulo`() {
        assertNull(PrinterProfiles.resolver(null))
    }

    @Test
    fun `resolver devuelve null cuando el id es desconocido`() {
        assertNull(PrinterProfiles.resolver("MODELO_INVENTADO"))
    }

    @Test
    fun `resolver mapea cada id conocido a su perfil`() {
        assertEquals(PrinterProfiles.PT210, PrinterProfiles.resolver("PT210"))
        assertEquals(PrinterProfiles.GENERICA_80, PrinterProfiles.resolver("GENERICA_80"))
        assertEquals(PrinterProfiles.EPSON_TM_T20, PrinterProfiles.resolver("EPSON_TM_T20"))
    }

    @Test
    fun `resolverOPorDefecto cae a PT210 con id nulo o desconocido`() {
        assertSame(PrinterProfiles.POR_DEFECTO, PrinterProfiles.resolverOPorDefecto(null))
        assertSame(PrinterProfiles.POR_DEFECTO, PrinterProfiles.resolverOPorDefecto("NOPE"))
    }

    @Test
    fun `resolverOPorDefecto respeta un id conocido`() {
        assertEquals(
            PrinterProfiles.GENERICA_80,
            PrinterProfiles.resolverOPorDefecto("GENERICA_80"),
        )
    }

    @Test
    fun `TODOS cubre cada modelo del enum exactamente una vez`() {
        val idsCatalogo = PrinterProfiles.TODOS.map { it.id }.toSet()
        assertEquals(PrinterModelId.entries.toSet(), idsCatalogo)
        assertEquals(PrinterModelId.entries.size, PrinterProfiles.TODOS.size)
    }

    @Test
    fun `cada perfil tiene parametros fisicos validos`() {
        PrinterProfiles.TODOS.forEach { perfil ->
            assertTrue("cols > 0 en ${perfil.id}", perfil.cols > 0)
            assertTrue("widthDots > 0 en ${perfil.id}", perfil.widthDots > 0)
            assertTrue("lineasFinales >= 0 en ${perfil.id}", perfil.lineasFinales >= 0)
        }
    }

    @Test
    fun `los perfiles de 80mm tienen mas columnas que los de 58mm`() {
        assertTrue(PrinterProfiles.GENERICA_80.cols > PrinterProfiles.PT210.cols)
        assertTrue(PrinterProfiles.GENERICA_80.widthDots > PrinterProfiles.PT210.widthDots)
    }
}
