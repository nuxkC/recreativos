package com.recre.app.core.data.repository

import com.recre.app.core.data.local.dao.AveriaPendienteDao
import com.recre.app.core.data.local.entity.AveriaPendienteEntity
import com.recre.app.core.data.local.entity.EstadoAveriaPendiente
import com.recre.app.core.data.remote.AveriasRemoteDataSource
import com.recre.app.core.data.remote.clasificarErrorAveria
import com.recre.app.core.data.remote.clasificarErrorGestion
import com.recre.app.core.data.remote.dto.AveriaConRecambiosDto
import com.recre.app.core.data.remote.dto.CrearAveriaParams
import com.recre.app.core.data.remote.dto.CrearRecambioParams
import com.recre.app.core.data.remote.dto.ResolverAveriaParams
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Sistema de averías en la app del técnico (T-222).
 *
 * Dos caminos, según el precedente del repo:
 * - **Reporte offline** (técnico): [encolar] persiste en `averia_pendiente` y la
 *   UI sigue sin esperar; [subirSiguiente] (llamado por el Worker) sube la
 *   avería + sus recambios vía RPC. Mismo criterio que la recaudación (T-57): el
 *   reporte nunca se pierde por falta de red.
 * - **Historial/cierre online** (gestión): [historial] lee `v_averia`/`averia`
 *   enriquecida y [resolver] cierra la avería, igual que el CRUD gestor.
 */
interface AveriaRepository {

    suspend fun encolar(input: ReportarAveriaInput): DomainResult<Unit>

    /** Sube la siguiente avería pendiente. Devuelve `null` si no hay ninguna. */
    suspend fun subirSiguiente(empresaId: String): DomainResult<AveriaPendienteEntity?>

    /** Reportes de una máquina aún no subidos (pendientes/error), para la UI. */
    fun observarPendientesPorMaquina(maquinaId: String): Flow<List<AveriaPendienteEntity>>

    /** Recuento de pendientes (incluye `error`/`subiendo`) para badges. */
    fun observarContadorPendientes(empresaId: String): Flow<Int>

    /** Historial en línea por máquina (hoja de vida, gestión). */
    suspend fun historial(empresaId: String, maquinaId: String): GestionResult<List<AveriaHistorial>>

    /** Cierra una avería en línea (gestión). */
    suspend fun resolver(averiaId: String, notas: String?): GestionResult<Unit>
}

/** Input de un reporte de avería (con sus recambios). Importes como String. */
data class ReportarAveriaInput(
    val empresaId: String,
    val maquinaId: String,
    val maquinaNumeroSerie: String,
    val categoria: String,
    val descripcion: String?,
    val poneMaquinaFueraServicio: Boolean,
    val notas: String?,
    val recambios: List<RecambioInput>,
)

/** Recambio capturado al reportar. `coste` es dinero → String (nunca Double). */
data class RecambioInput(
    val pieza: String,
    val cantidad: Int,
    val coste: String?,
    val notas: String?,
)

/** Avería del historial (dominio para la UI de gestión). */
data class AveriaHistorial(
    val id: String,
    val categoria: String,
    val descripcion: String?,
    val estado: String,
    val poneMaquinaFueraServicio: Boolean,
    val fechaReporte: Instant,
    val fechaResolucion: Instant?,
    val localNombre: String?,
    val notas: String?,
    val recambios: List<RecambioHistorial>,
)

data class RecambioHistorial(
    val id: String,
    val pieza: String,
    val cantidad: Int,
    val coste: String?,
    val notas: String?,
)

@Singleton
class AveriaRepositoryImpl @Inject constructor(
    private val dao: AveriaPendienteDao,
    private val remote: AveriasRemoteDataSource,
) : AveriaRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun encolar(input: ReportarAveriaInput): DomainResult<Unit> {
        val now = Instant.now()
        val entity = AveriaPendienteEntity(
            id = java.util.UUID.randomUUID().toString(),
            empresaId = input.empresaId,
            maquinaId = input.maquinaId,
            maquinaNumeroSerie = input.maquinaNumeroSerie,
            categoria = input.categoria,
            descripcion = input.descripcion,
            poneMaquinaFueraServicio = input.poneMaquinaFueraServicio,
            notas = input.notas,
            recambiosJson = serializarRecambios(input.recambios),
            estado = EstadoAveriaPendiente.PENDIENTE,
            intentos = 0,
            ultimoError = null,
            ultimoIntentoAt = null,
            createdAt = now,
            subidaAt = null,
            averiaIdRemoto = null,
            recambiosSubidos = 0,
        )
        return runCatching { dao.insert(entity) }.fold(
            onSuccess = { DomainResult.Success(Unit) },
            onFailure = { throwable ->
                Timber.e(throwable, "No se pudo encolar la avería")
                DomainResult.Failure(DomainError.Unknown(throwable.message))
            },
        )
    }

    override suspend fun subirSiguiente(
        empresaId: String,
    ): DomainResult<AveriaPendienteEntity?> {
        val pendiente = dao.siguientePendiente(empresaId)
            ?: return DomainResult.Success(null)

        dao.marcarSubiendo(pendiente.id, Instant.now())
        val recambios = deserializarRecambios(pendiente.recambiosJson)

        val result = runCatching {
            // 1. La avería se crea una sola vez: si ya tiene id remoto (reintento
            //    tras un corte de red), no la recreamos — evitamos duplicados.
            val averiaId = pendiente.averiaIdRemoto ?: run {
                val nuevoId = remote.crearAveria(
                    CrearAveriaParams(
                        empresaId = pendiente.empresaId,
                        maquinaId = pendiente.maquinaId,
                        categoria = pendiente.categoria,
                        descripcion = pendiente.descripcion,
                        poneMaquinaFueraServicio = pendiente.poneMaquinaFueraServicio,
                        notas = pendiente.notas,
                    ),
                )
                dao.marcarAveriaCreada(pendiente.id, nuevoId)
                nuevoId
            }
            // 2. Subimos los recambios que falten, avanzando el cursor tras cada
            //    éxito para poder reanudar exactamente donde se cortó.
            var subidos = pendiente.recambiosSubidos
            while (subidos < recambios.size) {
                val r = recambios[subidos]
                remote.crearRecambio(
                    CrearRecambioParams(
                        averiaId = averiaId,
                        pieza = r.pieza,
                        cantidad = r.cantidad,
                        coste = r.coste,
                        notas = r.notas,
                    ),
                )
                subidos++
                dao.marcarRecambiosSubidos(pendiente.id, subidos)
            }
        }

        return result.fold(
            onSuccess = {
                dao.marcarEnviada(pendiente.id, Instant.now())
                Timber.i("Avería subida: id=%s maquina=%s", pendiente.id, pendiente.maquinaId)
                DomainResult.Success(pendiente.copy(estado = EstadoAveriaPendiente.ENVIADA))
            },
            onFailure = { throwable ->
                val (error, code) = clasificarErrorGestion(throwable)
                dao.marcarError(pendiente.id, code, Instant.now())
                Timber.w(throwable, "Fallo subiendo avería %s: %s", pendiente.id, code)
                DomainResult.Failure(error)
            },
        )
    }

    override fun observarPendientesPorMaquina(
        maquinaId: String,
    ): Flow<List<AveriaPendienteEntity>> = dao.observarPorMaquina(maquinaId)

    override fun observarContadorPendientes(empresaId: String): Flow<Int> =
        dao.observarContadorPendientes(empresaId)

    override suspend fun historial(
        empresaId: String,
        maquinaId: String,
    ): GestionResult<List<AveriaHistorial>> =
        runCatching { remote.fetchHistorial(empresaId, maquinaId) }.fold(
            onSuccess = { dtos -> GestionResult.Success(dtos.map { it.toDomain() }) },
            onFailure = ::failure,
        )

    override suspend fun resolver(averiaId: String, notas: String?): GestionResult<Unit> =
        runCatching {
            remote.resolverAveria(
                ResolverAveriaParams(id = averiaId, notasResolucion = notas?.takeIf { it.isNotBlank() }),
            )
        }.fold(
            onSuccess = { GestionResult.Success(Unit) },
            onFailure = { throwable ->
                val (error, code) = clasificarErrorAveria(throwable)
                Timber.w(throwable, "Resolver avería %s falló: %s", averiaId, code)
                GestionResult.Failure(error, code)
            },
        )

    private fun <T> failure(throwable: Throwable): GestionResult<T> {
        val (error, code) = clasificarErrorAveria(throwable)
        Timber.w(throwable, "Averías falló: %s", code)
        return GestionResult.Failure(error, code)
    }

    // -------------------------------------------------------------------------
    // Mappers / serialización
    // -------------------------------------------------------------------------

    private fun AveriaConRecambiosDto.toDomain(): AveriaHistorial = AveriaHistorial(
        id = id,
        categoria = categoria,
        descripcion = descripcion,
        estado = estado,
        poneMaquinaFueraServicio = poneMaquinaFueraServicio,
        fechaReporte = parseTimestamp(fechaReporte),
        fechaResolucion = fechaResolucion?.let { parseTimestamp(it) },
        localNombre = local?.nombre,
        notas = notas,
        recambios = recambios.map {
            RecambioHistorial(
                id = it.id,
                pieza = it.pieza,
                cantidad = it.cantidad,
                coste = it.coste,
                notas = it.notas,
            )
        },
    )

    private fun serializarRecambios(items: List<RecambioInput>): String =
        json.encodeToString(
            ListSerializer(RecambioPendienteJson.serializer()),
            items.map { RecambioPendienteJson(it.pieza, it.cantidad, it.coste, it.notas) },
        )

    private fun deserializarRecambios(raw: String): List<RecambioInput> =
        json.decodeFromString(ListSerializer(RecambioPendienteJson.serializer()), raw)
            .map { RecambioInput(it.pieza, it.cantidad, it.coste, it.notas) }

    /** Igual que en [SyncRepositoryImpl]: PostgREST devuelve ISO 8601 con offset. */
    private fun parseTimestamp(raw: String): Instant {
        val iso = raw.replace(' ', 'T')
        return runCatching { OffsetDateTime.parse(iso).toInstant() }
            .getOrElse { Instant.parse(if (iso.endsWith("Z")) iso else "${iso}Z") }
    }
}

/** DTO interno solo para serializar los recambios dentro de `recambios_json`. */
@Serializable
private data class RecambioPendienteJson(
    val pieza: String,
    val cantidad: Int,
    val coste: String?,
    val notas: String?,
)
