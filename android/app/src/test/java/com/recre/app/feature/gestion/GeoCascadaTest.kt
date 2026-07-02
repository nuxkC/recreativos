package com.recre.app.feature.gestion

import com.recre.app.core.data.repository.Municipio
import com.recre.app.core.data.repository.Provincia
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoCascadaTest {

    private val provincias = listOf(
        Provincia("46", "Valencia/Valencia", "Comunidad Valenciana"),
        Provincia("03", "Alicante/Alacant", "Comunidad Valenciana"),
        Provincia("28", "Madrid", "Madrid"),
    )
    private val municipios = listOf(
        Municipio("46250", "Valencia", "46"),
        Municipio("46102", "Alaquas", "46"),
    )

    @Test
    fun provincias_de_ccaa_filtra_exacto_y_ordena_por_nombre() {
        assertEquals(
            listOf(FkOption("03", "Alicante/Alacant"), FkOption("46", "Valencia/Valencia")),
            provinciasDeCcaa("Comunidad Valenciana", provincias),
        )
    }

    @Test
    fun provincias_vacio_si_ccaa_vacia() {
        assertEquals(emptyList<FkOption>(), provinciasDeCcaa("", provincias))
    }

    @Test
    fun municipios_como_opciones_id_codigo_label_nombre_ordenado() {
        assertEquals(
            listOf(FkOption("46102", "Alaquas"), FkOption("46250", "Valencia")),
            municipiosComoOpciones(municipios),
        )
    }
}
