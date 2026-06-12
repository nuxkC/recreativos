package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.RecaudacionHistoricaRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Frontera HTTP de la pantalla "Mis recaudaciones" (T-63).
 *
 * Solo lectura: el alta la hace siempre el flujo offline (T-57). Aquí
 * exponemos:
 *  - [listarMias]: las recaudaciones del técnico autenticado, con
 *    instalación + máquina + local + licencia joinados en una sola
 *    round-trip vía PostgREST embedding.
 *  - [reimprimirSignedUrl]: llama a la Edge Function `reimprimir-ticket`
 *    (T-27b) para obtener una signed URL del PDF de archivo.
 *  - [descargarFirma]: descarga el PNG de la firma desde Storage
 *    (bucket `firmas`) para reusar el ticket Bluetooth (T-62) en
 *    "Reimprimir ESC/POS".
 */
@Singleton
class RecaudacionHistoricaRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    /**
     * Lista las recaudaciones del técnico actual en la empresa activa.
     *
     * La RLS sobre `recaudacion` ya restringe a su `empresa_id`. El
     * filtro `tecnico_id = uid` lo añadimos en cliente (lo expone
     * `AuthRepository.currentUserId`) para mostrar solo las jornadas
     * propias.
     *
     * Limita a 200 filas, suficiente para el histórico personal con
     * volumen de jornadas razonable. Cuando crezca, paginamos con
     * cursor o pasamos a una RPC.
     */
    suspend fun listarMias(
        empresaId: String,
        tecnicoId: String,
    ): List<RecaudacionHistoricaRow> =
        supabase
            .from("recaudacion")
            .select(columns = Columns.raw(SELECT_COLUMNS)) {
                filter {
                    eq("empresa_id", empresaId)
                    eq("tecnico_id", tecnicoId)
                }
                order("fecha", Order.DESCENDING)
                limit(count = 200L)
            }
            .decodeList()

    /**
     * Llama a la Edge Function `reimprimir-ticket` (T-27b) y devuelve
     * la signed URL del PDF archivado en Storage. Si la recaudación no
     * tiene PDF (recaudación muy antigua de antes del T-25, o subida
     * desde una fuente sin PDF), el server responde `not_found`.
     */
    suspend fun reimprimirSignedUrl(recaudacionId: String): String {
        val payload: JsonObject = buildJsonObject {
            put("recaudacion_id", JsonPrimitive(recaudacionId))
        }
        val response = supabase.functions.invoke("reimprimir-ticket", payload)
        if (response.status != HttpStatusCode.OK) {
            val raw = runCatching { response.body<String>() }.getOrNull()
            throw RecaudacionRemoteError(
                status = response.status.value,
                code = parseErrorCode(raw),
                message = raw ?: "HTTP ${response.status.value}",
            )
        }
        return response.body<ReimprimirResponse>().pdfSignedUrl
    }

    /**
     * Descarga el PNG de la firma desde el bucket privado `firmas`. La
     * RLS de Storage ya valida que el path empieza por una empresa del
     * usuario.
     *
     * Usado por "Reimprimir ESC/POS" (T-62 + T-63) cuando el técnico
     * pide reenviar el ticket por Bluetooth: necesitamos el PNG
     * original para que la firma del local se imprima igual que en el
     * ticket inicial.
     *
     * Devuelve `null` si la firma no existe o falla la descarga
     * (defensivo: mejor reimprimir sin firma que abortar la operación).
     */
    suspend fun descargarFirma(firmaUrl: String): ByteArray? {
        if (firmaUrl.isBlank()) return null
        return runCatching {
            supabase.storage.from("firmas").downloadAuthenticated(firmaUrl)
        }.getOrNull()
    }

    @Serializable
    private data class ReimprimirResponse(
        @SerialName("pdf_signed_url")
        val pdfSignedUrl: String,
    )

    private companion object {
        /**
         * Embedding PostgREST: trae la instalación con sus FK en una
         * sola round-trip. No usamos `*` para evitar columnas que no
         * consume la UI ni las refeercias *_recalculado de conflicto
         * (no se muestran al técnico, eso vive en el back-office).
         */
        const val SELECT_COLUMNS = "id," +
            "instalacion_id," +
            "tecnico_id," +
            "fecha," +
            "contador_entradas_anterior," +
            "contador_salidas_anterior," +
            "contador_entradas_actual," +
            "contador_salidas_actual," +
            "valor_credito_aplicado," +
            "recaudacion_bruta," +
            "semanas_aplicadas," +
            "tasa_semanal_aplicada," +
            "tasa_total_aplicada," +
            "recaudacion_neta," +
            "porcentaje_local_aplicado," +
            "parte_local," +
            "parte_empresa," +
            "reposicion_tolva," +
            "desglose_total," +
            "desglose_local," +
            "firma_url," +
            "pdf_url," +
            "conflicto," +
            "revisado_en," +
            "estado," +
            "motivo_anulacion," +
            "anulada_en," +
            "instalacion:instalacion_id (" +
            "id," +
            "licencia:licencia_id ( id, numero )," +
            "maquina:maquina_id ( id, numero_serie, modelo, fabricante )," +
            "local:local_id ( id, nombre, direccion )" +
            ")"
    }
}
