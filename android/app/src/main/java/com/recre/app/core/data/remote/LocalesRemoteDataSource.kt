package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.IdResponseDto
import com.recre.app.core.data.remote.dto.LocalInsertDto
import com.recre.app.core.data.remote.dto.LocalUpdateDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP para el CRUD de Locales en la app del técnico (T-68).
 * Mismo patrón que [LicenciasRemoteDataSource].
 *
 * Notas:
 * - `local` es palabra reservada en SQLite — pero PostgREST acepta el
 *   nombre sin entrecomillar y la BBDD usa `"local"` con quotes en el
 *   SQL. El SDK gestiona el escape internamente.
 */
@Singleton
class LocalesRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun crear(dto: LocalInsertDto): String {
        return supabase
            .from("local")
            .insert(dto) {
                select()
            }
            .decodeSingle<IdResponseDto>()
            .id
    }

    suspend fun actualizar(empresaId: String, id: String, dto: LocalUpdateDto) {
        supabase
            .from("local")
            .update(dto) {
                filter {
                    eq("empresa_id", empresaId)
                    eq("id", id)
                }
            }
    }

    suspend fun eliminar(empresaId: String, id: String) {
        supabase
            .from("local")
            .delete {
                filter {
                    eq("empresa_id", empresaId)
                    eq("id", id)
                }
            }
    }
}
