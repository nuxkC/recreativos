package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.CuadreSemanalRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lee `v_cuadre_semanal_tecnico`. La vista ya filtra por tecnico_id=auth.uid()
 * y la RLS por empresa, así que no hace falta pasar filtros: traemos todas las
 * semanas (acotadas por técnico) ordenadas y el VM agrupa/navega.
 */
@Singleton
class CuadreRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun obtener(): List<CuadreSemanalRow> =
        supabase
            .from("v_cuadre_semanal_tecnico")
            .select {
                order("semana_inicio", Order.DESCENDING)
            }
            .decodeList()
}
