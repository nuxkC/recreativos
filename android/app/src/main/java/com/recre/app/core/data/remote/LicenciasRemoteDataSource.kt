package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.IdResponseDto
import com.recre.app.core.data.remote.dto.LicenciaInsertDto
import com.recre.app.core.data.remote.dto.LicenciaUpdateDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP para el CRUD de Licencias en la app del técnico (T-66).
 *
 * Operaciones pensadas para roles `gestor+`. La RLS de la tabla
 * `licencia` (T-15) ya restringe `INSERT/UPDATE/DELETE` a esos roles;
 * aquí simplemente lanzamos las llamadas. Los errores se propagan como
 * excepciones (subclases de `RestException` de supabase-kt) y la capa
 * repositorio las normaliza con [clasificarErrorGestion].
 *
 * Multi-tenant: aunque RLS filtra por empresa, todos los UPDATE/DELETE
 * llevan `eq("empresa_id", empresaId)` explícito para minimizar el
 * riesgo de tocar la fila equivocada si por algún motivo viajara un id
 * cruzado.
 */
@Singleton
class LicenciasRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun crear(dto: LicenciaInsertDto): String {
        return supabase
            .from("licencia")
            .insert(dto) {
                select()
            }
            .decodeSingle<IdResponseDto>()
            .id
    }

    suspend fun actualizar(empresaId: String, id: String, dto: LicenciaUpdateDto) {
        supabase
            .from("licencia")
            .update(dto) {
                filter {
                    eq("empresa_id", empresaId)
                    eq("id", id)
                }
            }
    }

    suspend fun eliminar(empresaId: String, id: String) {
        supabase
            .from("licencia")
            .delete {
                filter {
                    eq("empresa_id", empresaId)
                    eq("id", id)
                }
            }
    }
}
