package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.ActualizarMaquinaParams
import com.recre.app.core.data.remote.dto.CrearMaquinaParams
import com.recre.app.core.data.remote.dto.EliminarMaquinaParams
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP para el CRUD de Máquinas en la app del técnico (T-67).
 *
 * La escritura directa a `maquina` está REVOCADA: todo pasa por las RPCs
 * SECURITY DEFINER `crear/actualizar/eliminar_maquina` (validan gestor +
 * tenant). Mismo patrón que [LicenciasRemoteDataSource].
 */
@Singleton
class MaquinasRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun crear(params: CrearMaquinaParams): String =
        supabase.postgrest.rpc("crear_maquina", params).decodeAs<String>()

    suspend fun actualizar(params: ActualizarMaquinaParams) {
        supabase.postgrest.rpc("actualizar_maquina", params)
    }

    suspend fun eliminar(params: EliminarMaquinaParams) {
        supabase.postgrest.rpc("eliminar_maquina", params)
    }
}
