package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.AlertaDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

/**
 * Frontera HTTP de la pantalla "Alertas" (T-64).
 *
 * Solo lee y marca como leídas. La inserción la hace el backend
 * automáticamente cuando T-21 detecta conflicto (alerta tipo
 * `recaudacion_conflicto`), T-26a registra una anulación
 * (`recaudacion_anulada`) o T-26b cierra un conflicto (la propia
 * alerta original se marca leída desde el resolver).
 *
 * Al ser tan simple, no se cachea en Room: refresco bajo demanda. Eso
 * mantiene el estado siempre fresco al volver a la pantalla principal,
 * que es donde está el badge de conteo.
 */
@Singleton
class AlertasRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    /** Update parcial: solo el flag `leida`. Va como objeto serializable
     *  para que supabase-kt lo convierta a `{ "leida": true }` sin
     *  ambigüedades de tipos. */
    @Serializable
    private data class LeidaUpdate(val leida: Boolean = true)

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

    /**
     * Marca una alerta como leída. La RLS sobre `alerta` permite
     * UPDATE a cualquier miembro de la empresa.
     */
    suspend fun marcarLeida(empresaId: String, alertaId: String) {
        supabase
            .from("alerta")
            .update(LeidaUpdate()) {
                filter {
                    eq("id", alertaId)
                    eq("empresa_id", empresaId)
                }
            }
    }

    /** Marca todas las pendientes de una empresa como leídas. */
    suspend fun marcarTodasLeidas(empresaId: String) {
        supabase
            .from("alerta")
            .update(LeidaUpdate()) {
                filter {
                    eq("empresa_id", empresaId)
                    eq("leida", false)
                }
            }
    }
}
