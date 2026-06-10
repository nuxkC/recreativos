package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.IdResponseDto
import com.recre.app.core.data.remote.dto.MaquinaInsertDto
import com.recre.app.core.data.remote.dto.MaquinaUpdateDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP para el CRUD de Máquinas en la app del técnico (T-67).
 * Mismo patrón que [LicenciasRemoteDataSource].
 */
@Singleton
class MaquinasRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun crear(dto: MaquinaInsertDto): String {
        return supabase
            .from("maquina")
            .insert(dto) {
                select()
            }
            .decodeSingle<IdResponseDto>()
            .id
    }

    suspend fun actualizar(empresaId: String, id: String, dto: MaquinaUpdateDto) {
        supabase
            .from("maquina")
            .update(dto) {
                filter {
                    eq("empresa_id", empresaId)
                    eq("id", id)
                }
            }
    }

    suspend fun eliminar(empresaId: String, id: String) {
        supabase
            .from("maquina")
            .delete {
                filter {
                    eq("empresa_id", empresaId)
                    eq("id", id)
                }
            }
    }
}
