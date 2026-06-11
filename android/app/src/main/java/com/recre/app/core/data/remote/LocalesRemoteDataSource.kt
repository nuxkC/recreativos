package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.ActualizarLocalParams
import com.recre.app.core.data.remote.dto.CrearLocalParams
import com.recre.app.core.data.remote.dto.EliminarLocalParams
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP para el CRUD de Locales en la app del técnico (T-68).
 *
 * La escritura directa a `local` está REVOCADA: todo pasa por las RPCs
 * SECURITY DEFINER `crear/actualizar/eliminar_local` (validan gestor +
 * tenant). Mismo patrón que [LicenciasRemoteDataSource].
 */
@Singleton
class LocalesRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun crear(params: CrearLocalParams): String =
        supabase.postgrest.rpc("crear_local", params).decodeAs<String>()

    suspend fun actualizar(params: ActualizarLocalParams) {
        supabase.postgrest.rpc("actualizar_local", params)
    }

    suspend fun eliminar(params: EliminarLocalParams) {
        supabase.postgrest.rpc("eliminar_local", params)
    }
}
