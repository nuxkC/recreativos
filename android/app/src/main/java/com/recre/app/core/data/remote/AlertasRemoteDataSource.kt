package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.AlertaDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Frontera HTTP de la pantalla "Alertas" (T-64).
 *
 * Solo LEE y marca como leídas. La escritura directa a `alerta` está REVOCADA:
 * el marcado pasa por las RPCs SECURITY DEFINER `marcar_alerta_leida` /
 * `marcar_alertas_leidas_empresa`, que validan pertenencia a la empresa de la
 * alerta. La inserción la hace el backend automáticamente (service_role) cuando
 * T-21 detecta conflicto, T-26a registra una anulación o T-26b cierra un
 * conflicto.
 *
 * Al ser tan simple, no se cachea en Room: refresco bajo demanda. Eso
 * mantiene el estado siempre fresco al volver a la pantalla principal,
 * que es donde está el badge de conteo.
 */
@Singleton
class AlertasRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    @Serializable
    private data class MarcarAlertaParams(
        @SerialName("p_alerta_id") val alertaId: String,
    )

    @Serializable
    private data class MarcarEmpresaParams(
        @SerialName("p_empresa_id") val empresaId: String,
    )

    /**
     * Lista alertas pendientes de la empresa activa. Limita a 50; lo
     * habitual es que el técnico tenga 0 o 1-2 a la vez. Si crece,
     * paginamos con cursor.
     */
    suspend fun listarPendientes(empresaId: String): List<AlertaDto> =
        supabase
            .from("alerta")
            .select {
                filter {
                    eq("empresa_id", empresaId)
                    eq("leida", false)
                }
                order("creada_en", Order.DESCENDING)
                limit(count = 50L)
            }
            .decodeList()

    /**
     * Cuenta alertas pendientes para mostrar el badge en el menú de
     * Locales. Pide `head=true` + `Count.EXACT` para no traer las
     * filas — solo el contador.
     */
    suspend fun contarPendientes(empresaId: String): Long {
        val result = supabase
            .from("alerta")
            .select {
                count(Count.EXACT)
                head = true
                filter {
                    eq("empresa_id", empresaId)
                    eq("leida", false)
                }
            }
        return result.countOrNull() ?: 0L
    }

    /** Marca una alerta como leída vía RPC (valida pertenencia server-side). */
    suspend fun marcarLeida(alertaId: String) {
        supabase.postgrest.rpc("marcar_alerta_leida", MarcarAlertaParams(alertaId))
    }

    /** Marca todas las pendientes de una empresa como leídas vía RPC. */
    suspend fun marcarTodasLeidas(empresaId: String) {
        supabase.postgrest.rpc(
            "marcar_alertas_leidas_empresa",
            MarcarEmpresaParams(empresaId),
        )
    }
}
