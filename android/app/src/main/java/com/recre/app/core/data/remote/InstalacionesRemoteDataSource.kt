package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.CerrarInstalacionRequest
import com.recre.app.core.data.remote.dto.IdResponseDto
import com.recre.app.core.data.remote.dto.InstalacionInsertDto
import com.recre.app.core.data.remote.dto.InstalacionUpdateDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
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
 * - Alta directamente vía PostgREST con [InstalacionInsertDto].
 * - Update parcial sin tocar FKs ni `estado`.
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

    suspend fun crear(dto: InstalacionInsertDto): String {
        return supabase
            .from("instalacion")
            .insert(dto) {
                select()
            }
            .decodeSingle<IdResponseDto>()
            .id
    }

    suspend fun actualizar(empresaId: String, id: String, dto: InstalacionUpdateDto) {
        supabase
            .from("instalacion")
            .update(dto) {
                filter {
                    eq("empresa_id", empresaId)
                    eq("id", id)
                }
            }
    }

    suspend fun eliminar(empresaId: String, id: String) {
        supabase
            .from("instalacion")
            .delete {
                filter {
                    eq("empresa_id", empresaId)
                    eq("id", id)
                }
            }
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
