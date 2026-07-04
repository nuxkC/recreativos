package com.recre.app.core.data.repository

import io.github.jan.supabase.auth.status.SessionStatus
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mapeo puro SessionStatus (supabase-kt) → "¿hay sesión?" (`null` = aún no se sabe).
 *
 * El flash de Login al desbloquear el móvil (proceso muerto en segundo plano →
 * cold start) venía de traducir `Initializing` a `false`: "aún no he leído la
 * sesión de disco" se trataba como "no logueado", el NavHost reseteaba a Login
 * y el colector de SessionRepository borraba la empresa activa persistida.
 * `Initializing` debe ser desconocido (null → no se emite nada) para que el
 * estado global se quede en Loading hasta saber la verdad.
 */
class SesionStatusMappingTest {

    @Test
    fun `Initializing es desconocido no logout`() {
        assertNull(isLoggedInFromStatus(SessionStatus.Initializing))
    }

    @Test
    fun `Authenticated es sesion valida`() {
        assertEquals(true, isLoggedInFromStatus(mockk<SessionStatus.Authenticated>()))
    }

    @Test
    fun `RefreshFailure conserva la sesion local`() {
        assertEquals(true, isLoggedInFromStatus(mockk<SessionStatus.RefreshFailure>()))
    }

    @Test
    fun `NotAuthenticated es logout`() {
        assertEquals(false, isLoggedInFromStatus(mockk<SessionStatus.NotAuthenticated>()))
    }
}
