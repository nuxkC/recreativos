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
 * Frontera HTTP del Histórico de recaudaciones (T-63 + Histórico v2, §6.5).
 *
 * Solo lectura: el alta la hace siempre el flujo offline (T-57). Lee la
 * vista `v_recaudacion_historica`, que ya deriva local/máquina del
 * snapshot inmutable y aplica el RBAC por rol vía `security_invoker`.
 * Exponemos:
 *  - [listarVisibles]: todo el histórico que el usuario puede ver por su
 *    rol (técnico → sus locales asignados; gestor/owner/… → toda la
 *    empresa).
 *  - [listarPorLocal] / [listarPorMaquina]: el mismo histórico acotado a
 *    un local o una máquina (drill-down desde sus fichas).
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
     * Todo el histórico visible para el usuario en la empresa activa.
     *
     * No filtra por `tecnico_id`: el RBAC lo resuelve la RLS de la vista
     * (`security_invoker`), de modo que un gestor ve el histórico
     * completo de su empresa y un técnico solo el de sus locales.
     */
    suspend fun listarVisibles(empresaId: String): List<RecaudacionHistoricaRow> =
        consultar(empresaId)

    /** Histórico de un local concreto (drill-down desde su ficha). */
    suspend fun listarPorLocal(
        empresaId: String,
        localId: String,
    ): List<RecaudacionHistoricaRow> =
        consultar(empresaId, localId = localId)

    /** Histórico de una máquina concreta (drill-down desde su ficha). */
    suspend fun listarPorMaquina(
        empresaId: String,
        maquinaId: String,
    ): List<RecaudacionHistoricaRow> =
        consultar(empresaId, maquinaId = maquinaId)

    /**
     * Query común a la vista. El filtro `empresa_id` acota a la empresa
     * activa (un usuario multi-empresa solo ve la suya); `local_id` /
     * `maquina_id` son opcionales para el drill-down. Limita a 200
     * filas, suficiente para el histórico con volumen razonable; cuando
     * crezca, paginamos con cursor.
     */
    private suspend fun consultar(
        empresaId: String,
        localId: String? = null,
        maquinaId: String? = null,
    ): List<RecaudacionHistoricaRow> =
        supabase
            .from("v_recaudacion_historica")
            .select(columns = Columns.raw(SELECT_COLUMNS)) {
                filter {
                    eq("empresa_id", empresaId)
                    if (localId != null) eq("local_id", localId)
                    if (maquinaId != null) eq("maquina_id", maquinaId)
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
         * Columnas planas de `v_recaudacion_historica`. No usamos `*`
         * para evitar columnas que no consume la UI ni las *_recalculado
         * de conflicto (back-office). `local_id`/`maquina_id` y los
         * nombres/series los deriva la vista del snapshot de instalación.
         */
        const val SELECT_COLUMNS = "id," +
            "instalacion_id," +
            "local_id," +
            "maquina_id," +
            "licencia_id," +
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
            "local_nombre," +
            "local_direccion," +
            "maquina_numero_serie," +
            "maquina_modelo," +
            "maquina_fabricante," +
            "licencia_numero"
    }
}
