package com.recre.app.feature.gestion

import com.recre.app.core.data.repository.FabricanteCatalogo
import com.recre.app.core.data.repository.ModeloCatalogo
import org.junit.Assert.assertEquals
import org.junit.Test

// Nombres de test en ASCII a propósito: el runner mangla acentos con locale no-UTF8.
class CatalogoCascadaTest {

    private val fabricantes = listOf(
        FabricanteCatalogo("fab-cirsa", "Cirsa"),
        FabricanteCatalogo("fab-unidesa", "Unidesa"),
    )
    private val modelos = listOf(
        ModeloCatalogo("m1", "Diplomat", "fab-cirsa"),
        ModeloCatalogo("m2", "Super", "fab-cirsa"),
        ModeloCatalogo("m3", "Gallo", "fab-unidesa"),
    )

    @Test
    fun fabricantes_como_opciones_mapea_id_y_label() {
        assertEquals(
            listOf(FkOption("fab-cirsa", "Cirsa"), FkOption("fab-unidesa", "Unidesa")),
            fabricantesComoOpciones(fabricantes),
        )
    }

    @Test
    fun modelos_del_fabricante_por_nombre_laxo() {
        assertEquals(
            listOf(FkOption("m1", "Diplomat"), FkOption("m2", "Super")),
            modelosDeFabricante("  cirsa ", fabricantes, modelos),
        )
    }

    @Test
    fun modelos_vacio_si_fabricante_nuevo_o_vacio() {
        assertEquals(emptyList<FkOption>(), modelosDeFabricante("Nueva SL", fabricantes, modelos))
        assertEquals(emptyList<FkOption>(), modelosDeFabricante("", fabricantes, modelos))
    }
}
