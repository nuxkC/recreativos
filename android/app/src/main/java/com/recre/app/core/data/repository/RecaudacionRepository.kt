package com.recre.app.core.data.repository

import com.recre.app.core.calculo.Cifras
import com.recre.app.core.calculo.DenominacionItem
import com.recre.app.core.data.local.dao.RecaudacionPendienteDao
import com.recre.app.core.data.local.entity.EstadoRecaudacionPendiente
import com.recre.app.core.data.local.entity.RecaudacionPendienteEntity
import com.recre.app.core.data.remote.RecaudacionRemoteDataSource
import com.recre.app.core.data.remote.RecaudacionRemoteError
import com.recre.app.core.data.remote.parseErrorCode
import com.recre.app.core.data.remote.parseErrorMessage
import com.recre.app.core.data.remote.dto.CrearRecaudacionRequest
import com.recre.app.core.data.remote.dto.DenominacionItemDto
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Cola offline de recaudaciones (T-57).
 *
 * - [encolar] persiste localmente. La UI puede continuar sin esperar.
 * - [subir] toma una pendiente y la envía a `crear-recaudacion`. Llamado
 *   por el Worker; reintentable hasta que [DomainError.Network] desaparezca.
 * - [observarPendientes] alimenta la pantalla T-63 de "mis recaudaciones".
 *
 * El repository convierte entre `BigDecimal` (UI / cálculo) y `String`
 * (Room / serialización), y entre `List<DenominacionItem>` y JSON.
 */
interface RecaudacionRepository {

    suspend fun encolar(input: EncolarRecaudacionInput): DomainResult<RecaudacionPendienteEntity>

    /** Sube la siguiente pendiente de una empresa. Devuelve `null` si no hay. */
    suspend fun subirSiguiente(empresaId: String): DomainResult<RecaudacionPendienteEntity?>

    fun observarPendientes(empresaId: String): Flow<List<RecaudacionPendienteEntity>>

    /** Recuento de no-enviadas (`pendiente`/`error`/`subiendo`/`fallida`) para badges. */
    fun observarContadorPendientes(empresaId: String): Flow<Int>

    /**
     * Recaudaciones BLOQUEADAS (estado IN 'error','fallida') con su `ultimoError`.
     * Alimenta el aviso global y el panel de subidas (Reintentar/Descartar).
     */
    fun observarBloqueadas(empresaId: String): Flow<List<RecaudacionPendienteEntity>>

    /** Reintento manual desde el panel: devuelve la fila a 'pendiente'. */
    suspend fun reintentar(id: String): DomainResult<Unit>

    /** Descarte manual desde el panel: elimina la fila de la cola. */
    suspend fun descartar(id: String): DomainResult<Unit>

    /** Recupera filas colgadas en 'subiendo' de una ejecución abortada. */
    suspend fun recuperarColgadas(empresaId: String)
}

/** Input para encolar una recaudación nueva. */
data class EncolarRecaudacionInput(
    val empresaId: String,
    val instalacionId: String,
    val tecnicoId: String,
    val fecha: Instant,
    val contadorEntradasActual: Long,
    val contadorSalidasActual: Long,
    val baselineEntradas: Long,
    val baselineSalidas: Long,
    val baselineOrigen: String,
    val baselineReferenciaId: String?,
    val cifras: Cifras,
    val desgloseTotal: List<DenominacionItem>,
    val desgloseLocal: List<DenominacionItem>,
    val firmaPng: ByteArray,
    val observaciones: String?,
    val dispositivoId: String?,
    /** Orden manual de imputación de la recuperación (T-215). `null` = defecto. */
    val ordenRecuperacion: List<String>? = null,
)

@Singleton
class RecaudacionRepositoryImpl @Inject constructor(
    private val dao: RecaudacionPendienteDao,
    private val remote: RecaudacionRemoteDataSource,
) : RecaudacionRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /** Reintentos de RED antes de marcar la fila como terminal ('fallida'). */
    private val maxIntentosRed = 8

    override suspend fun encolar(
        input: EncolarRecaudacionInput,
    ): DomainResult<RecaudacionPendienteEntity> {
        val now = Instant.now()
        val entity = RecaudacionPendienteEntity(
            id = UUID.randomUUID().toString(),
            empresaId = input.empresaId,
            instalacionId = input.instalacionId,
            tecnicoId = input.tecnicoId,
            fecha = input.fecha,
            contadorEntradasActual = input.contadorEntradasActual,
            contadorSalidasActual = input.contadorSalidasActual,
            baselineEntradas = input.baselineEntradas,
            baselineSalidas = input.baselineSalidas,
            baselineOrigen = input.baselineOrigen,
            baselineReferenciaId = input.baselineReferenciaId,
            valorCredito = input.cifras.valorCredito.toPlainString(),
            tasaSemanal = input.cifras.tasaSemanal.toPlainString(),
            semanas = input.cifras.semanas,
            tasaTotal = input.cifras.tasaTotal.toPlainString(),
            bruto = input.cifras.bruto.toPlainString(),
            neto = input.cifras.neto.toPlainString(),
            porcentajeLocal = input.cifras.porcentajeLocal.toPlainString(),
            parteLocal = input.cifras.parteLocal.toPlainString(),
            parteEmpresa = input.cifras.parteEmpresa.toPlainString(),
            desgloseTotalJson = serializarDesglose(input.desgloseTotal),
            desgloseLocalJson = serializarDesglose(input.desgloseLocal),
            ordenRecuperacionJson = input.ordenRecuperacion
                ?.takeIf { it.isNotEmpty() }
                ?.let { json.encodeToString(ListSerializer(String.serializer()), it) },
            firmaPng = input.firmaPng,
            observaciones = input.observaciones,
            dispositivoId = input.dispositivoId,
            idempotencyKey = UUID.randomUUID().toString(),
            estado = EstadoRecaudacionPendiente.PENDIENTE,
            intentos = 0,
            ultimoError = null,
            ultimoIntentoAt = null,
            createdAt = now,
            subidaAt = null,
            recaudacionIdRemoto = null,
            conflicto = false,
        )
        return runCatching { dao.insert(entity) }.fold(
            onSuccess = { DomainResult.Success(entity) },
            onFailure = { throwable ->
                Timber.e(throwable, "No se pudo encolar la recaudación")
                DomainResult.Failure(DomainError.Unknown(throwable.message))
            },
        )
    }

    override suspend fun subirSiguiente(
        empresaId: String,
    ): DomainResult<RecaudacionPendienteEntity?> {
        val pendiente = dao.siguientePendiente(empresaId)
            ?: return DomainResult.Success(null)

        val now = Instant.now()
        dao.marcarSubiendo(pendiente.id, now)

        val request = mapToRequest(pendiente)
        val result = runCatching { remote.crearRecaudacion(request) }

        return result.fold(
            onSuccess = { response ->
                dao.marcarEnviada(
                    id = pendiente.id,
                    idRemoto = response.recaudacion.id,
                    conflicto = response.conflicto,
                    ahora = Instant.now(),
                )
                Timber.i(
                    "Recaudacion subida: id=%s remoto=%s conflicto=%s",
                    pendiente.id,
                    response.recaudacion.id,
                    response.conflicto,
                )
                DomainResult.Success(pendiente.copy(estado = EstadoRecaudacionPendiente.ENVIADA))
            },
            onFailure = { throwable ->
                val (error, mensaje) = clasificar(throwable)
                val ahora = Instant.now()
                // Solo un fallo de RED es transitorio: la fila sigue 'error' y se
                // reintenta. Cualquier otro (validación/auth/not_found/conflicto…)
                // NO se arregla reintentando el MISMO payload congelado → 'fallida'
                // (terminal, fuera del drenado) para no bloquear la cola. Una fila
                // que agota los reintentos de red también pasa a terminal.
                val terminal = error !is DomainError.Network ||
                    pendiente.intentos + 1 >= maxIntentosRed
                if (terminal) {
                    dao.marcarFallida(pendiente.id, mensaje, ahora)
                } else {
                    dao.marcarError(pendiente.id, mensaje, ahora)
                }
                Timber.w(
                    throwable,
                    "Fallo subiendo %s (%s): %s",
                    pendiente.id,
                    if (terminal) "terminal/fallida" else "reintentable",
                    mensaje,
                )
                DomainResult.Failure(error)
            },
        )
    }

    override fun observarPendientes(
        empresaId: String,
    ): Flow<List<RecaudacionPendienteEntity>> = dao.observarPorEmpresa(empresaId)

    override fun observarContadorPendientes(empresaId: String): Flow<Int> =
        dao.observarContadorPendientes(empresaId)

    override fun observarBloqueadas(empresaId: String): Flow<List<RecaudacionPendienteEntity>> =
        dao.observarBloqueadas(empresaId)

    override suspend fun reintentar(id: String): DomainResult<Unit> =
        runCatching { dao.reencolar(id) }.fold(
            onSuccess = { DomainResult.Success(Unit) },
            onFailure = { DomainResult.Failure(DomainError.Unknown(it.message)) },
        )

    override suspend fun descartar(id: String): DomainResult<Unit> =
        runCatching { dao.descartar(id) }.fold(
            onSuccess = { DomainResult.Success(Unit) },
            onFailure = { DomainResult.Failure(DomainError.Unknown(it.message)) },
        )

    override suspend fun recuperarColgadas(empresaId: String) {
        runCatching { dao.rearmarColgadas(empresaId) }
            .onFailure { Timber.w(it, "No se pudieron rearmar colgadas de %s", empresaId) }
    }

    private fun mapToRequest(entity: RecaudacionPendienteEntity): CrearRecaudacionRequest {
        val desgloseTotal = deserializarDesglose(entity.desgloseTotalJson)
        val desgloseLocal = deserializarDesglose(entity.desgloseLocalJson)
        val orden = entity.ordenRecuperacionJson?.let {
            json.decodeFromString(ListSerializer(String.serializer()), it)
        }
        val fechaIso = OffsetDateTime.ofInstant(entity.fecha, ZoneOffset.UTC).toString()
        return CrearRecaudacionRequest(
            instalacionId = entity.instalacionId,
            fecha = fechaIso,
            contadorEntradasActual = entity.contadorEntradasActual,
            contadorSalidasActual = entity.contadorSalidasActual,
            desgloseTotal = desgloseTotal.map { it.toDto() },
            desgloseLocal = desgloseLocal.map { it.toDto() },
            firmaBase64 = android.util.Base64.encodeToString(entity.firmaPng, android.util.Base64.NO_WRAP),
            observaciones = entity.observaciones,
            dispositivoId = entity.dispositivoId,
            idempotencyKey = entity.idempotencyKey,
            baselineOrigen = entity.baselineOrigen,
            baselineId = entity.baselineReferenciaId,
            baselineEntradas = entity.baselineEntradas,
            baselineSalidas = entity.baselineSalidas,
            ordenRecuperacion = orden,
        )
    }

    private fun serializarDesglose(items: List<DenominacionItem>): String =
        json.encodeToString(
            ListSerializer(DenominacionItemSerializable.serializer()),
            items.map { DenominacionItemSerializable(it.denominacion.toPlainString(), it.cantidad) },
        )

    private fun deserializarDesglose(raw: String): List<DenominacionItem> =
        json.decodeFromString(
            ListSerializer(DenominacionItemSerializable.serializer()),
            raw,
        ).map { DenominacionItem(java.math.BigDecimal(it.denominacion), it.cantidad) }

    private fun DenominacionItem.toDto(): DenominacionItemDto =
        DenominacionItemDto(
            denominacion = this.denominacion.toDouble(),
            cantidad = this.cantidad,
        )

    private fun clasificar(throwable: Throwable): Pair<DomainError, String> {
        // El cuerpo de error de la Edge viaja en el `message` de la excepción
        // —ya sea [RecaudacionRemoteError] o la `BadRequestRestException` de
        // supabase-kt— como `{ error: { code, message } }`. Extraemos AMBOS: el
        // `code` decide reintento vs fallo, y el `message` legible es lo que se
        // le enseña al técnico en el aviso (no el JSON crudo ni el código pelado).
        val raw = throwable.message ?: "unknown"
        val codigo = (throwable as? RecaudacionRemoteError)?.code ?: parseErrorCode(raw)
        val legible = parseErrorMessage(raw) ?: raw.take(200)
        return when (codigo) {
            "validation_error" -> DomainError.Validation(legible) to legible
            "forbidden" -> DomainError.Auth(legible) to legible
            "not_found" -> DomainError.NotFound(legible) to legible
            "conflict" -> DomainError.Conflict(legible) to legible
            else ->
                if (esRedCaida(raw)) {
                    DomainError.Network(raw) to "network"
                } else {
                    DomainError.Unknown(legible) to legible
                }
        }
    }

    /** Heurística de "no había red" (para reintentar en vez de marcar error). */
    private fun esRedCaida(msg: String): Boolean =
        msg.contains("network", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("unable to resolve", ignoreCase = true) ||
            msg.contains("name resolution", ignoreCase = true)
}

/**
 * DTO interno solo para serialización JSON dentro del campo
 * `desglose_*_json` en Room. Mantenemos `denominacion` como String para
 * preservar precisión decimal exacta cuando re-cargamos.
 */
@kotlinx.serialization.Serializable
private data class DenominacionItemSerializable(
    val denominacion: String,
    val cantidad: Int,
)
