package com.recre.app.core.session

import com.recre.app.core.auth.Rol
import com.recre.app.core.data.local.EmpresaPreferences
import com.recre.app.core.data.local.MembresiasCache
import com.recre.app.core.data.repository.AuthRepository
import com.recre.app.core.data.repository.EmpresaRepository
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transiciones de [SessionRepository.state] que la UI convierte en navegación.
 *
 * Los estados transitorios importan tanto como el final: un `NotAuthenticated`
 * o `NoMemberships` de milisegundos se convierte en un flash de Login/SinAcceso
 * porque `navigateForState` resetea la pila en cuanto lo ve. El colector
 * unconfined captura cada valor del StateFlow de forma síncrona (sin
 * conflación), y el scope inyectado con dispatcher de test hace determinista
 * el orden combine-primero que en producción es una carrera.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {

    private val membresia = Membresia(
        empresa = EmpresaResumen(id = "e1", nombre = "Levante", zonaHoraria = "Europe/Madrid"),
        rol = Rol.TECNICO,
    )

    private val empresaActivaId = MutableStateFlow<String?>(null)
    private val prefs: EmpresaPreferences = mockk {
        every { empresaActivaIdFlow } returns empresaActivaId
        coEvery { setEmpresaActivaId(anyNullable()) } coAnswers {
            empresaActivaId.value = firstArg()
        }
    }

    private val cache: MembresiasCache = mockk {
        coEvery { leer() } returns null
        coJustRun { guardar(any()) }
        coJustRun { limpiar() }
    }

    /** `sesion = null` simula el Initializing de supabase: no se emite nada. */
    private class FakeAuthRepository : AuthRepository {
        val sesion = MutableStateFlow<Boolean?>(null)
        override fun observeIsLoggedIn(): Flow<Boolean> = sesion.filterNotNull()
        override suspend fun signIn(email: String, password: String): DomainResult<Unit> =
            error("no usado")
        override suspend fun signOut(): DomainResult<Unit> = error("no usado")
        override fun currentUserId(): String? = null
        override fun currentUserEmail(): String? = null
    }

    private class FakeEmpresaRepository : EmpresaRepository {
        /** Si está presente, el fetch queda "en vuelo" hasta completarlo. */
        var gate: CompletableDeferred<Unit>? = null
        var respuesta: DomainResult<List<Membresia>> = DomainResult.Success(emptyList())
        override suspend fun listarMembresiasActivas(): DomainResult<List<Membresia>> {
            gate?.await()
            return respuesta
        }
    }

    private fun TestScope.crearRepo(
        auth: AuthRepository,
        empresas: EmpresaRepository,
    ): SessionRepository = SessionRepository(
        authRepository = auth,
        empresaRepository = empresas,
        empresaPreferences = prefs,
        membresiasCache = cache,
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun `arranque en frio con sesion en disco no pasa por Login ni SinAcceso`() = runTest {
        val auth = FakeAuthRepository()
        val empresas = FakeEmpresaRepository().apply {
            respuesta = DomainResult.Success(listOf(membresia))
        }
        empresaActivaId.value = "e1"
        val repo = crearRepo(auth, empresas)

        // Mientras supabase lee la sesión de disco no llega ninguna emisión:
        // el estado debe seguir en Loading y la empresa persistida intacta.
        advanceUntilIdle()
        assertTrue(repo.state.value is SessionState.Loading)
        assertEquals("e1", empresaActivaId.value)

        val estados = mutableListOf<SessionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.state.toList(estados)
        }

        auth.sesion.value = true // sesión restaurada de disco
        advanceUntilIdle()

        assertTrue(
            "no debe pasar por NotAuthenticated: $estados",
            estados.none { it is SessionState.NotAuthenticated },
        )
        assertTrue(
            "no debe pasar por NoMemberships: $estados",
            estados.none { it is SessionState.NoMemberships },
        )
        assertTrue(repo.state.value is SessionState.Active)
    }

    @Test
    fun `relogin tras logout no pasa por SinAcceso mientras cargan membresias`() = runTest {
        val auth = FakeAuthRepository()
        val empresas = FakeEmpresaRepository().apply {
            respuesta = DomainResult.Success(listOf(membresia))
        }
        empresaActivaId.value = "e1"
        val repo = crearRepo(auth, empresas)

        auth.sesion.value = true
        advanceUntilIdle()
        assertTrue(repo.state.value is SessionState.Active)

        auth.sesion.value = false // logout real: aquí sí se borra la empresa
        advanceUntilIdle()
        assertTrue(repo.state.value is SessionState.NotAuthenticated)

        // Re-login con el fetch de membresías aún en vuelo: si quedó una lista
        // vacía obsoleta del logout, el combine emite NoMemberships antes de
        // que refreshMembresias() marque Loading → flash de SinAcceso.
        empresas.gate = CompletableDeferred()
        val estados = mutableListOf<SessionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.state.toList(estados)
        }

        auth.sesion.value = true
        advanceUntilIdle()
        empresas.gate?.complete(Unit)
        advanceUntilIdle()

        assertTrue(
            "flash de SinAcceso: $estados",
            estados.none { it is SessionState.NoMemberships },
        )
        // Tras un logout real la empresa se reelige: el estado final correcto
        // es NeedsEmpresaSelection, no Active.
        assertTrue(repo.state.value is SessionState.NeedsEmpresaSelection)
    }

    @Test
    fun `logout no deja cacheada una lista vacia de membresias`() = runTest {
        val auth = FakeAuthRepository()
        val empresas = FakeEmpresaRepository().apply {
            respuesta = DomainResult.Success(listOf(membresia))
        }
        val repo = crearRepo(auth, empresas)

        auth.sesion.value = true
        advanceUntilIdle()
        auth.sesion.value = false
        advanceUntilIdle()

        // La raíz del flash de SinAcceso era la `Success(emptyList())` que el
        // logout dejaba cacheada: cualquier lector posterior la tomaba por una
        // respuesta real ("cero membresías") en vez de por dato pendiente.
        // seleccionarEmpresa la observa sin carreras: con la lista vacía
        // cacheada rechaza sin consultar el backend; con Loading consulta y
        // acepta la membresía válida.
        assertTrue(repo.seleccionarEmpresa("e1"))
    }

    @Test
    fun `arranque sin red con cache usa la ultima lista conocida`() = runTest {
        val auth = FakeAuthRepository()
        val empresas = FakeEmpresaRepository().apply {
            respuesta = DomainResult.Failure(DomainError.Network("sin red"))
        }
        coEvery { cache.leer() } returns listOf(membresia)
        empresaActivaId.value = "e1"
        val repo = crearRepo(auth, empresas)

        auth.sesion.value = true
        advanceUntilIdle()

        // La app es offline-first: con sesión válida y cache de membresías el
        // arranque sin cobertura debe llegar a Active, no a un spinner eterno.
        assertTrue("estado: ${repo.state.value}", repo.state.value is SessionState.Active)
    }

    @Test
    fun `fallo de membresias sin cache expone error reintentable`() = runTest {
        val auth = FakeAuthRepository()
        val empresas = FakeEmpresaRepository().apply {
            respuesta = DomainResult.Failure(DomainError.Network("sin red"))
        }
        val repo = crearRepo(auth, empresas)

        auth.sesion.value = true
        advanceUntilIdle()

        // Antes esto era Loading para siempre (splash sin salida ni mensaje).
        assertTrue("estado: ${repo.state.value}", repo.state.value is SessionState.LoadError)
    }

    @Test
    fun `el refresh persiste la cache y el logout la limpia`() = runTest {
        val auth = FakeAuthRepository()
        val empresas = FakeEmpresaRepository().apply {
            respuesta = DomainResult.Success(listOf(membresia))
        }
        val repo = crearRepo(auth, empresas)

        auth.sesion.value = true
        advanceUntilIdle()
        coVerify { cache.guardar(listOf(membresia)) }
        assertTrue(repo.state.value !is SessionState.Loading)

        // Otro usuario podría entrar después: su lista no debe heredarse.
        auth.sesion.value = false
        advanceUntilIdle()
        coVerify { cache.limpiar() }
    }
}
