package com.recre.app.core.data.repository

import com.recre.app.core.calculo.Cifras
import com.recre.app.core.calculo.DenominacionItem
import com.recre.app.core.data.local.dao.RecaudacionPendienteDao
import com.recre.app.core.data.local.entity.EstadoRecaudacionPendiente
import com.recre.app.core.data.local.entity.RecaudacionPendienteEntity
import com.recre.app.core.data.remote.RecaudacionRemoteDataSource
import com.recre.app.core.data.remote.RecaudacionRemoteError
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

    /** Recuento de pendientes (incluye `error` y `subiendo`) para badges. */
    fun observarContadorPendientes(empresaId: String): Flow<Int>
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
                dao.marcarError(pendiente.id, mensaje, Instant.now())
                Timber.w(throwable, "Fallo subiendo recaudacion %s: %s", pendiente.id, mensaje)
                DomainResult.Failure(error)
            },
        )
    }

    override fun observarPendientes(
        empresaId: String,
    ): Flow<List<RecaudacionPendienteEntity>> = dao.observarPorEmpresa(empresaId)

    override fun observarContadorPendientes(empresaId: String): Flow<Int> =
        dao.observarContadorPendientes(empresaId)

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
        return when (throwable) {
            is RecaudacionRemoteError -> when (throwable.code) {
                "validation_error" -> DomainError.Validation(throwable.message ?: "validation_error") to
                    "validation_error"
                "forbidden" -> DomainError.Auth(throwable.message ?: "forbidden") to "forbidden"
                "not_found" -> DomainError.NotFound(throwable.message ?: "not_found") to "not_found"
                "conflict" -> DomainError.Conflict(throwable.message ?: "conflict") to "conflict"
                else -> DomainError.Unknown(throwable.code ?: throwable.message) to
                    (throwable.code ?: "unknown")
            }
            else -> {
                val msg = throwable.message ?: "unknown"
                if (msg.contains("network", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("unable to resolve", ignoreCase = true)
                ) {
                    DomainError.Network(msg) to "network"
                } else {
                    DomainError.Unknown(msg) to msg.take(200)
                }
            }
        }
    }
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
