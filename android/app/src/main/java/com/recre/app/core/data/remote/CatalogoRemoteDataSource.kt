package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.FabricanteDto
import com.recre.app.core.data.remote.dto.ModeloDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP del catálogo global de máquinas (fabricante/modelo). SOLO
 * LECTURA: la RLS permite SELECT a `authenticated`; el alta ocurre dentro de la
 * RPC `crear_maquina` al guardar (find-or-create), no desde aquí. Global → sin
 * filtro `empresa_id`.
 */
@Singleton
class CatalogoRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun fetchFabricantes(): List<FabricanteDto> =
        supabase.from("fabricante").select().decodeList()

    suspend fun fetchModelos(): List<ModeloDto> =
        supabase.from("modelo").select().decodeList()
}
