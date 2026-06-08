package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.CrearCambioPlacaRequest
import com.recre.app.core.data.remote.dto.CrearCambioPlacaResponse
import com.recre.app.core.data.remote.dto.CrearRecaudacionRequest
import com.recre.app.core.data.remote.dto.CrearRecaudacionResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Frontera HTTP del flujo de recaudación: `crear-recaudacion`,
 * `adquirir-lock`, `liberar-lock` y `crear-cambio-placa` (T-61).
 *
 * No conoce nada de Room: solo serializa, hace la llamada y deserializa
 * la respuesta. Los errores HTTP se propagan como [RecaudacionRemoteError]
 * con el código del backend (`conflict`, `validation_error`, `forbidden`,
 * `lock_held`, etc.), que el repositorio mapea a `DomainError`.
 */
@Singleton
class RecaudacionRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    /**
     * Llama a la Edge Function `crear-recaudacion`. Si `idempotency_key`
     * ya existía, el server devuelve `reusada=true` con la fila previa,
     * que tratamos como éxito (la sync ya estaba hecha desde otro
     * intento).
     */
    suspend fun crearRecaudacion(request: CrearRecaudacionRequest): CrearRecaudacionResponse {
        val response = supabase.functions.invoke("crear-recaudacion", request)
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            val raw = runCatching { response.body<String>() }.getOrNull()
            throw RecaudacionRemoteError(
                status = response.status.value,
                code = parseErrorCode(raw),
                message = raw ?: "HTTP ${response.status.value}",
            )
        }
        return response.body()
    }

    /**
     * Registra un cambio de placa (T-22 / T-61). Tras la llamada, la
     * baseline real para esa instalación pasa a ser
     * `(contador_entradas_nuevo, contador_salidas_nuevo)`. El cliente
     * debe forzar un sync para actualizar la cache local.
     */
    suspend fun crearCambioPlaca(request: CrearCambioPlacaRequest): CrearCambioPlacaResponse {
        val response = supabase.functions.invoke("crear-cambio-placa", request)
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            val raw = runCatching { response.body<String>() }.getOrNull()
            throw RecaudacionRemoteError(
                status = response.status.value,
                code = parseErrorCode(raw),
                message = raw ?: "HTTP ${response.status.value}",
            )
        }
        return response.body()
    }

    /**
     * Adquiere un lock optimista para que un segundo técnico no recaude
     * la misma máquina simultáneamente (T-58).
     *
     * @param forzar Si `true`, sobrescribe un lock existente aunque sea
     *   de otro técnico (HU-12 "continuar de todos modos").
     */
    suspend fun adquirirLock(
        instalacionId: String,
        dispositivoId: String?,
        forzar: Boolean,
    ): AdquirirLockResult {
        val payload = AdquirirLockBody(
            instalacionId = instalacionId,
            dispositivoId = dispositivoId,
            forzar = forzar,
        )
        val response = supabase.functions.invoke("adquirir-lock", payload)
        val raw = runCatching { response.body<String>() }.getOrNull().orEmpty()

        return when {
            response.status == HttpStatusCode.OK -> {
                val parsed = JSON.decodeFromString<LockOkBody>(raw)
                AdquirirLockResult.Adquirido(
                    expiresAt = parsed.expiresAt,
                    forzado = parsed.forzado,
                )
            }
            parseErrorCode(raw) == "lock_held" -> {
                val parsed = runCatching { JSON.decodeFromString<LockHeldBody>(raw) }.getOrNull()
                AdquirirLockResult.Ocupado(
                    tecnicoId = parsed?.tecnicoId,
                    expiresAt = parsed?.expiresAt,
                )
            }
            else -> throw RecaudacionRemoteError(
                status = response.status.value,
                code = parseErrorCode(raw),
                message = raw,
            )
        }
    }

    /** Libera el lock al cerrar el flujo. Best-effort: ignoramos errores. */
    suspend fun liberarLock(instalacionId: String) {
        runCatching {
            supabase.functions.invoke(
                "liberar-lock",
                LiberarLockBody(instalacionId = instalacionId),
            )
        }
    }

    private fun parseErrorCode(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val element = JSON.parseToJsonElement(body)
            val obj = element as? JsonObject ?: return@runCatching null
            val codeElement = obj["code"] ?: return@runCatching null
            val primitive = codeElement as? JsonPrimitive ?: return@runCatching null
            if (primitive is JsonNull) null else primitive.content
        }.getOrNull()
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

/** Resultado de `adquirir-lock`. */
sealed interface AdquirirLockResult {
    data class Adquirido(
        val expiresAt: String?,
        val forzado: Boolean,
    ) : AdquirirLockResult

    /** Otro técnico tiene el lock. La UI ofrece "continuar de todos modos". */
    data class Ocupado(
        val tecnicoId: String?,
        val expiresAt: String?,
    ) : AdquirirLockResult
}

/** Excepción tipada del datasource: el repository la mapea a DomainError. */
class RecaudacionRemoteError(
    val status: Int,
    val code: String?,
    message: String,
) : RuntimeException(message)

@Serializable
private data class AdquirirLockBody(
    @SerialName("instalacion_id")
    val instalacionId: String,
    @SerialName("dispositivo_id")
    val dispositivoId: String? = null,
    val forzar: Boolean = false,
)

@Serializable
private data class LiberarLockBody(
    @SerialName("instalacion_id")
    val instalacionId: String,
)

@Serializable
private data class LockOkBody(
    @SerialName("expires_at")
    val expiresAt: String? = null,
    val forzado: Boolean = false,
)

@Serializable
private data class LockHeldBody(
    @SerialName("tecnico_id")
    val tecnicoId: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null,
)
