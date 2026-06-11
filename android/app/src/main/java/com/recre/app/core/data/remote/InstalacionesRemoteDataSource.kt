package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.ActualizarInstalacionParams
import com.recre.app.core.data.remote.dto.CerrarInstalacionRequest
import com.recre.app.core.data.remote.dto.CrearInstalacionParams
import com.recre.app.core.data.remote.dto.EliminarInstalacionParams
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Frontera HTTP para el CRUD de Instalaciones en la app del técnico (T-69).
 *
 * - Alta/edición/borrado vía RPC SECURITY DEFINER (la escritura directa a la
 *   tabla está revocada). La base de contadores la deriva el servidor.
 * - **Cierre** vía Edge Function `cerrar-instalacion` (T-23): además de
 *   marcar `estado='cerrada'` y `fecha_fin`, libera locks pendientes y
 *   aplica las validaciones de coherencia. NO se hace UPDATE directo.
 * - Borrado solo en altas erróneas sin recaudaciones; en cuanto exista
 *   actividad la FK `recaudacion(instalacion_id) RESTRICT` lo bloquea
 *   (mapeado a `en_uso` por [clasificarErrorGestion]).
 */
@Singleton
class InstalacionesRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun crear(params: CrearInstalacionParams): String =
        supabase.postgrest
            .rpc("crear_instalacion", params)
            .decodeAs<String>()

    suspend fun actualizar(params: ActualizarInstalacionParams) {
        supabase.postgrest.rpc("actualizar_instalacion", params)
    }

    suspend fun eliminar(params: EliminarInstalacionParams) {
        supabase.postgrest.rpc("eliminar_instalacion", params)
    }

    /**
     * Cierra la instalación vía Edge Function `cerrar-instalacion`.
     * Lanza [GestionRemoteError] con el `code` que devuelva el server
     * (`validation_error`, `conflict`, `not_found`, `forbidden`,
     * `unauthorized`).
     */
    suspend fun cerrar(request: CerrarInstalacionRequest) {
        val response = supabase.functions.invoke("cerrar-instalacion", request)
        if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
            return
        }
        val raw = runCatching { response.body<String>() }.getOrNull().orEmpty()
        throw GestionRemoteError(
            code = parseEdgeErrorCode(raw),
            details = raw,
            message = raw.ifBlank { "HTTP ${response.status.value}" },
        )
    }

    private fun parseEdgeErrorCode(body: String): String? {
        if (body.isBlank()) return null
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
