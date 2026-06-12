package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.AveriaConRecambiosDto
import com.recre.app.core.data.remote.dto.CrearAveriaParams
import com.recre.app.core.data.remote.dto.CrearRecambioParams
import com.recre.app.core.data.remote.dto.ResolverAveriaParams
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP del sistema de averías (T-222).
 *
 * - Historial por máquina: SELECT sobre `averia` con `averia_recambio` y
 *   `local` embebidos (RLS solo-lectura acota por tenant). Es la lectura de
 *   gestión, en línea, igual que el libro mayor de deudas.
 * - Alta/recambios/resolución vía RPC `SECURITY DEFINER`: la escritura directa
 *   está revocada y cada función valida rol operativo (técnico+) + tenant.
 */
@Singleton
class AveriasRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun crearAveria(params: CrearAveriaParams): String =
        supabase.postgrest.rpc("crear_averia", params).decodeAs<String>()

    suspend fun crearRecambio(params: CrearRecambioParams): String =
        supabase.postgrest.rpc("crear_recambio", params).decodeAs<String>()

    suspend fun resolverAveria(params: ResolverAveriaParams) {
        supabase.postgrest.rpc("resolver_averia", params)
    }

    /**
     * Historial de averías de una máquina (hoja de vida: atraviesa
     * instalaciones, con su `local` snapshot). Ordenado por fecha de reporte
     * descendente. La RLS ya acota por empresa; filtramos también explícito.
     */
    suspend fun fetchHistorial(
        empresaId: String,
        maquinaId: String,
    ): List<AveriaConRecambiosDto> =
        supabase
            .from("averia")
            .select(columns = Columns.raw(SELECT_COLUMNS)) {
                filter {
                    eq("empresa_id", empresaId)
                    eq("maquina_id", maquinaId)
                }
                order("fecha_reporte", Order.DESCENDING)
            }
            .decodeList()

    private companion object {
        const val SELECT_COLUMNS = "id," +
            "maquina_id," +
            "instalacion_id," +
            "local_id," +
            "categoria," +
            "descripcion," +
            "estado," +
            "pone_maquina_fuera_servicio," +
            "fecha_reporte," +
            "fecha_resolucion," +
            "notas," +
            "recambios:averia_recambio ( id, pieza, cantidad, coste, notas )," +
            "local:local_id ( nombre )"
    }
}
