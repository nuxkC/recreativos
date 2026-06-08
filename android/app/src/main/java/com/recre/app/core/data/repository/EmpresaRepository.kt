package com.recre.app.core.data.repository

import com.recre.app.core.auth.Rol
import com.recre.app.core.data.remote.MembresiaDto
import com.recre.app.core.session.EmpresaResumen
import com.recre.app.core.session.Membresia
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Acceso a las membresías del usuario autenticado.
 *
 * RLS ya filtra a las empresas del usuario; aquí solo se transforma la
 * fila al modelo de dominio [Membresia] y se filtran las desactivadas.
 */
interface EmpresaRepository {
    /** Lista las membresías activas del usuario autenticado, ordenadas alfabéticamente. */
    suspend fun listarMembresiasActivas(): DomainResult<List<Membresia>>
}

@Singleton
class SupabaseEmpresaRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : EmpresaRepository {

    override suspend fun listarMembresiasActivas(): DomainResult<List<Membresia>> = runCatching {
        supabase
            .from("empresa_usuario")
            .select(columns = COLUMNAS) {
                filter { eq("activo", true) }
            }
            .decodeList<MembresiaDto>()
    }.fold(
        onSuccess = { dtos ->
            val membresias = dtos.mapNotNull { it.toMembresia() }
                .sortedBy { it.empresa.nombre.lowercase() }
            DomainResult.Success(membresias)
        },
        onFailure = { throwable ->
            val message = throwable.message
            val error = when {
                message?.contains("network", ignoreCase = true) == true ->
                    DomainError.Network(message)
                else -> DomainError.Unknown(message)
            }
            DomainResult.Failure(error)
        },
    )

    private fun MembresiaDto.toMembresia(): Membresia? {
        val rolEnum = Rol.fromRaw(rol) ?: return null
        return Membresia(
            empresa = EmpresaResumen(
                id = empresa.id,
                nombre = empresa.nombre,
                zonaHoraria = empresa.zonaHoraria,
            ),
            rol = rolEnum,
        )
    }

    private companion object {
        // Solo nos interesan estas columnas; PostgREST resuelve el join
        // empresa:empresa_id(...) en una única round-trip.
        val COLUMNAS = Columns.raw("rol, activo, empresa:empresa_id ( id, nombre, zona_horaria )")
    }
}
