package com.recre.app.core.data.repository

import com.recre.app.core.data.local.dao.AveriaPendienteDao
import com.recre.app.core.data.local.entity.AveriaPendienteEntity
import com.recre.app.core.data.local.entity.EstadoAveriaPendiente
import com.recre.app.core.data.remote.AveriasRemoteDataSource
import com.recre.app.core.data.remote.dto.CrearAveriaParams
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [AveriaRepositoryImpl] (T-222).
 *
 * Cubren el contrato offline: encolar persiste; y la subida en dos fases
 * (avería + recambios) es **reanudable** — si la avería ya tiene id remoto no
 * se recrea, y los recambios continúan desde el cursor `recambios_subidos`, de
 * modo que un corte de red no duplica nada.
 */
class AveriaRepositoryImplTest {

    private val dao: AveriaPendienteDao = mockk(relaxed = true)
    private val remote: AveriasRemoteDataSource = mockk()

    private fun repo() = AveriaRepositoryImpl(dao, remote)

    private fun pendiente(
        recambiosJson: String = "[]",
        averiaIdRemoto: String? = null,
        recambiosSubidos: Int = 0,
        afectaTolva: Boolean = false,
        importeTolva: String? = null,
    ) = AveriaPendienteEntity(
        id = "p-1",
        empresaId = "emp-1",
        maquinaId = "maq-1",
        maquinaNumeroSerie = "SN-1",
        categoria = "atasco_billete",
        descripcion = "se traga el billete",
        poneMaquinaFueraServicio = true,
        notas = null,
        afectaTolva = afectaTolva,
        importeTolva = importeTolva,
        recambiosJson = recambiosJson,
        estado = EstadoAveriaPendiente.PENDIENTE,
        intentos = 0,
        ultimoError = null,
        ultimoIntentoAt = null,
        createdAt = Instant.now(),
        subidaAt = null,
        averiaIdRemoto = averiaIdRemoto,
        recambiosSubidos = recambiosSubidos,
    )

    @Test
    fun `encolar persiste una avería pendiente`() = runTest {
        coEvery { dao.insert(any()) } just Runs
        val capturado = slot<AveriaPendienteEntity>()

        val result = repo().encolar(
            ReportarAveriaInput(
                empresaId = "emp-1",
                maquinaId = "maq-1",
                maquinaNumeroSerie = "SN-1",
                categoria = "error",
                descripcion = null,
                poneMaquinaFueraServicio = false,
                notas = null,
                recambios = emptyList(),
            ),
        )

        assertEquals(DomainResult.Success(Unit), result)
        coVerify(exactly = 1) { dao.insert(capture(capturado)) }
        assertEquals(EstadoAveriaPendiente.PENDIENTE, capturado.captured.estado)
        assertEquals("error", capturado.captured.categoria)
    }

    @Test
    fun `subirSiguiente sin pendientes devuelve null`() = runTest {
        coEvery { dao.siguientePendiente("emp-1") } returns null

        val result = repo().subirSiguiente("emp-1")

        assertTrue(result is DomainResult.Success && result.value == null)
        coVerify(exactly = 0) { remote.crearAveria(any()) }
    }

    @Test
    fun `subirSiguiente crea la avería y sus recambios y la marca enviada`() = runTest {
        val json = """[{"pieza":"Validador","cantidad":1,"coste":"12.50","notas":null}]"""
        coEvery { dao.siguientePendiente("emp-1") } returns pendiente(recambiosJson = json)
        coEvery { remote.crearAveria(any()) } returns "av-9"
        coEvery { remote.crearRecambio(any()) } returns "rc-9"

        val result = repo().subirSiguiente("emp-1")

        assertTrue(result is DomainResult.Success)
        coVerify(exactly = 1) { remote.crearAveria(any()) }
        coVerify(exactly = 1) { remote.crearRecambio(any()) }
        coVerify(exactly = 1) { dao.marcarAveriaCreada("p-1", "av-9") }
        coVerify(exactly = 1) { dao.marcarRecambiosSubidos("p-1", 1) }
        coVerify(exactly = 1) { dao.marcarEnviada("p-1", any()) }
    }

    @Test
    fun `subirSiguiente envía la merma de tolva a crear_averia`() = runTest {
        // §5.6: si la avería pagó premio de la tolva, afecta_tolva + importe_tolva
        // viajan a la RPC, que inserta la merma. El importe es dinero → String.
        val capturado = slot<CrearAveriaParams>()
        coEvery { dao.siguientePendiente("emp-1") } returns
            pendiente(afectaTolva = true, importeTolva = "15.00")
        coEvery { remote.crearAveria(capture(capturado)) } returns "av-9"

        val result = repo().subirSiguiente("emp-1")

        assertTrue(result is DomainResult.Success)
        assertTrue(capturado.captured.afectaTolva)
        assertEquals("15.00", capturado.captured.importeTolva)
    }

    @Test
    fun `encolar persiste la merma de tolva`() = runTest {
        coEvery { dao.insert(any()) } just Runs
        val capturado = slot<AveriaPendienteEntity>()

        repo().encolar(
            ReportarAveriaInput(
                empresaId = "emp-1",
                maquinaId = "maq-1",
                maquinaNumeroSerie = "SN-1",
                categoria = "error",
                descripcion = null,
                poneMaquinaFueraServicio = false,
                notas = null,
                recambios = emptyList(),
                afectaTolva = true,
                importeTolva = "15.00",
            ),
        )

        coVerify(exactly = 1) { dao.insert(capture(capturado)) }
        assertTrue(capturado.captured.afectaTolva)
        assertEquals("15.00", capturado.captured.importeTolva)
    }

    @Test
    fun `subirSiguiente reanuda sin recrear la avería ya creada`() = runTest {
        // La avería ya tiene id remoto y 1 de 2 recambios subidos: el reintento
        // NO debe volver a crear la avería, solo subir el recambio que falta.
        val json = """
            [{"pieza":"A","cantidad":1,"coste":null,"notas":null},
             {"pieza":"B","cantidad":2,"coste":"3.00","notas":null}]
        """.trimIndent()
        coEvery { dao.siguientePendiente("emp-1") } returns
            pendiente(recambiosJson = json, averiaIdRemoto = "av-7", recambiosSubidos = 1)
        coEvery { remote.crearRecambio(any()) } returns "rc-7"

        val result = repo().subirSiguiente("emp-1")

        assertTrue(result is DomainResult.Success)
        coVerify(exactly = 0) { remote.crearAveria(any()) }
        coVerify(exactly = 1) { remote.crearRecambio(any()) }
        coVerify(exactly = 1) { dao.marcarRecambiosSubidos("p-1", 2) }
        coVerify(exactly = 1) { dao.marcarEnviada("p-1", any()) }
    }

    @Test
    fun `subirSiguiente con fallo de red marca error y devuelve Network`() = runTest {
        coEvery { dao.siguientePendiente("emp-1") } returns pendiente()
        coEvery { remote.crearAveria(any<CrearAveriaParams>()) } throws IOException("sin red")

        val result = repo().subirSiguiente("emp-1")

        assertTrue(result is DomainResult.Failure)
        assertTrue((result as DomainResult.Failure).error is DomainError.Network)
        coVerify(exactly = 1) { dao.marcarError("p-1", any(), any()) }
        coVerify(exactly = 0) { dao.marcarFallida(any(), any(), any()) }
        coVerify(exactly = 0) { dao.marcarEnviada(any(), any()) }
    }

    @Test
    fun `subirSiguiente con fallo permanente (no red) marca fallida y no bloquea la cola`() =
        runTest {
            // Un fallo que NO se arregla reintingando la misma avería congelada
            // (validación/permiso/…) → terminal: sale del drenado para no bloquear
            // a las averías VÁLIDAS que tiene detrás (T-63).
            coEvery { dao.siguientePendiente("emp-1") } returns pendiente()
            coEvery { remote.crearAveria(any<CrearAveriaParams>()) } throws
                RuntimeException("boom")

            val result = repo().subirSiguiente("emp-1")

            assertTrue(result is DomainResult.Failure)
            assertTrue((result as DomainResult.Failure).error !is DomainError.Network)
            coVerify(exactly = 1) { dao.marcarFallida("p-1", any(), any()) }
            coVerify(exactly = 0) { dao.marcarError(any(), any(), any()) }
            coVerify(exactly = 0) { dao.marcarEnviada(any(), any()) }
        }

    @Test
    fun `subirSiguiente con red agotada (maxIntentosRed) pasa a fallida`() = runTest {
        // Tras agotar los reintentos de red, una fila de red también pasa a terminal
        // para no quedarse reintentando eternamente.
        coEvery { dao.siguientePendiente("emp-1") } returns pendiente().copy(intentos = 7)
        coEvery { remote.crearAveria(any<CrearAveriaParams>()) } throws IOException("sin red")

        val result = repo().subirSiguiente("emp-1")

        assertTrue(result is DomainResult.Failure)
        assertTrue((result as DomainResult.Failure).error is DomainError.Network)
        coVerify(exactly = 1) { dao.marcarFallida("p-1", any(), any()) }
        coVerify(exactly = 0) { dao.marcarError(any(), any(), any()) }
    }

    @Test
    fun `reintentar reencola la fila`() = runTest {
        val result = repo().reintentar("p-1")

        assertEquals(DomainResult.Success(Unit), result)
        coVerify(exactly = 1) { dao.reencolar("p-1") }
    }

    @Test
    fun `descartar elimina la fila de la cola`() = runTest {
        val result = repo().descartar("p-1")

        assertEquals(DomainResult.Success(Unit), result)
        coVerify(exactly = 1) { dao.descartar("p-1") }
    }

    @Test
    fun `recuperarColgadas rearma las filas colgadas en subiendo`() = runTest {
        repo().recuperarColgadas("emp-1")

        coVerify(exactly = 1) { dao.rearmarColgadas("emp-1") }
    }
}
