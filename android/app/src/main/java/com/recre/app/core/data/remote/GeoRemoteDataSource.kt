package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.MunicipioDto
import com.recre.app.core.data.remote.dto.ProvinciaDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP de la referencia geográfica INE (provincia/municipio). SOLO
 * LECTURA: la RLS permite SELECT a `authenticated`; nadie escribe estas tablas
 * desde el cliente. Global → sin filtro `empresa_id`.
 *
 * En móvil NO se precargan los 8132 municipios: las provincias enteras (52) al
 * abrir el formulario y los municipios por provincia bajo demanda al elegirla.
 */
@Singleton
class GeoRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun fetchProvincias(): List<ProvinciaDto> =
        supabase.from("provincia").select().decodeList()

    suspend fun fetchMunicipios(provinciaCodigo: String): List<MunicipioDto> =
        supabase
            .from("municipio")
            .select {
                filter { eq("provincia_codigo", provinciaCodigo) }
            }
            .decodeList()
}
