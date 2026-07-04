package com.recre.app.core.data.local

import com.recre.app.core.auth.Rol
import com.recre.app.core.session.EmpresaResumen
import com.recre.app.core.session.Membresia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Serialización de la cache de membresías (respaldo del arranque offline).
 *
 * Son funciones puras extraídas de [MembresiasCache] para poder probarlas
 * sin DataStore. La regla de oro: un dato ilegible nunca rompe el arranque —
 * un rol desconocido descarta esa entrada, un JSON corrupto equivale a no
 * tener cache.
 */
class MembresiasJsonTest {

    private val membresia = Membresia(
        empresa = EmpresaResumen(id = "e1", nombre = "Levante", zonaHoraria = "Europe/Madrid"),
        rol = Rol.TECNICO,
    )

    @Test
    fun `ida y vuelta conserva la lista`() {
        assertEquals(
            listOf(membresia),
            membresiasFromJson(membresiasToJson(listOf(membresia))),
        )
    }

    @Test
    fun `rol desconocido se descarta sin tirar la lista`() {
        val json = membresiasToJson(listOf(membresia)).replace("tecnico", "becario")
        assertEquals(emptyList<Membresia>(), membresiasFromJson(json))
    }

    @Test
    fun `json corrupto es como no tener cache`() {
        assertNull(membresiasFromJson("esto no es json"))
    }
}
