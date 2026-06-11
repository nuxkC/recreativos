package com.recre.app.core.data.remote

import com.recre.app.core.data.remote.dto.ActualizarLicenciaParams
import com.recre.app.core.data.remote.dto.CrearLicenciaParams
import com.recre.app.core.data.remote.dto.EliminarLicenciaParams
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frontera HTTP para el CRUD de Licencias en la app del técnico (T-66).
 *
 * La escritura directa a `licencia` está REVOCADA: alta/edición/borrado pasan
 * por las RPCs SECURITY DEFINER `crear/actualizar/eliminar_licencia`, que
 * validan rol (gestor) + tenant server-side. Los errores se propagan como
 * excepciones (subclases de `RestException`) y la capa repositorio las
 * normaliza con [clasificarErrorGestion] (23505 duplicado, 23503 en uso, etc.).
 */
@Singleton
class LicenciasRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun crear(params: CrearLicenciaParams): String =
        supabase.postgrest.rpc("crear_licencia", params).decodeAs<String>()

    suspend fun actualizar(params: ActualizarLicenciaParams) {
        supabase.postgrest.rpc("actualizar_licencia", params)
    }

    suspend fun eliminar(params: EliminarLicenciaParams) {
        supabase.postgrest.rpc("eliminar_licencia", params)
    }
}
