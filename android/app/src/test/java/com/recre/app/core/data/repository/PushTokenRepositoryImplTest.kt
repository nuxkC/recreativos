package com.recre.app.core.data.repository

import com.recre.app.core.auth.Rol
import com.recre.app.core.data.remote.PushTokenRemoteDataSource
import com.recre.app.core.data.remote.RecaudacionRemoteError
import com.recre.app.core.session.EmpresaResumen
import com.recre.app.core.session.Membresia
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [PushTokenRepositoryImpl] (T-101).
 *
 * Verifican el contrato de registro de token frente a la empresa activa:
 * sin empresa → fallo de auth; con empresa → delega en el data source y
 * mapea los errores remotos a [DomainError].
 */
class PushTokenRepositoryImplTest {

    private val remote: PushTokenRemoteDataSource = mockk()

    private fun sessionConEstado(state: SessionState): SessionRepository {
        val session: SessionRepository = mockk()
        io.mockk.every { session.state } returns MutableStateFlow(state)
        return session
    }

    private fun estadoActivo(empresaId: String): SessionState.Active {
        val empresa = EmpresaResumen(id = empresaId, nombre = "Acme", zonaHoraria = "Europe/Madrid")
        val membresia = Membresia(empresa = empresa, rol = Rol.TECNICO)
        return SessionState.Active(membresia = membresia, membresias = listOf(membresia))
    }

    @Test
    fun `sin empresa activa devuelve Auth sin llamar al remoto`() = runTest {
        val repo = PushTokenRepositoryImpl(remote, sessionConEstado(SessionState.Loading))

        val result = repo.registrarToken("tok-1")

        assertTrue(result is DomainResult.Failure)
        assertTrue((result as DomainResult.Failure).error is DomainError.Auth)
        coVerify(exactly = 0) { remote.registrar(any(), any(), any()) }
    }

    @Test
    fun `con empresa activa registra el token y devuelve Success`() = runTest {
        coEvery { remote.registrar("emp-7", "tok-1", any()) } just Runs
        val repo = PushTokenRepositoryImpl(remote, sessionConEstado(estadoActivo("emp-7")))

        val result = repo.registrarToken("tok-1")

        assertEquals(DomainResult.Success(Unit), result)
        coVerify(exactly = 1) { remote.registrar("emp-7", "tok-1", any()) }
    }

    @Test
    fun `un fallo remoto se mapea a Network y no propaga la excepcion`() = runTest {
        coEvery { remote.registrar(any(), any(), any()) } throws
            RecaudacionRemoteError(status = 500, code = "internal_error", message = "boom")
        val repo = PushTokenRepositoryImpl(remote, sessionConEstado(estadoActivo("emp-7")))

        val result = repo.registrarToken("tok-1")

        assertTrue(result is DomainResult.Failure)
        assertTrue((result as DomainResult.Failure).error is DomainError.Network)
    }
}
