package com.recre.app.core.data.repository

import com.recre.app.core.data.remote.LocalesRemoteDataSource
import com.recre.app.core.data.remote.clasificarErrorGestion
import com.recre.app.core.data.remote.dto.LocalInsertDto
import com.recre.app.core.data.remote.dto.LocalUpdateDto
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.util.DomainError
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Repositorio del CRUD de Locales (T-68). Mismo patrón que
 * [LicenciasGestorRepository].
 *
 * Notas de dominio:
 * - La tabla `local` no tiene `estado` ni unique sobre `nombre`, así que
 *   no hay manejo de `23505` para nombres duplicados.
 * - El borrado puede fallar con FK violation (`23503`) si hay
 *   instalaciones activas o cerradas que la referencian — se traduce a
 *   `en_uso`.
 */
interface LocalesGestorRepository {

    suspend fun crear(input: LocalInputData): GestionResult<String>

    suspend fun actualizar(id: String, input: LocalInputData): GestionResult<Unit>

    suspend fun eliminar(id: String): GestionResult<Unit>
}

/**
 * Input ya validado y normalizado. Cualquier campo opcional vacío se ha
 * pasado a `null` antes de invocar.
 */
data class LocalInputData(
    val nombre: String,
    val direccion: String?,
    val cifONif: String?,
    val titularNombre: String?,
    val telefono: String?,
    val email: String?,
    val notas: String?,
)

@Singleton
class LocalesGestorRepositoryImpl @Inject constructor(
    private val remote: LocalesRemoteDataSource,
    private val sessionRepository: SessionRepository,
    private val syncManager: SyncManager,
) : LocalesGestorRepository {

    override suspend fun crear(input: LocalInputData): GestionResult<String> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")

        return runCatching {
            remote.crear(
                LocalInsertDto(
                    empresaId = empresaId,
                    nombre = input.nombre,
                    direccion = input.direccion,
                    cifONif = input.cifONif,
                    titularNombre = input.titularNombre,
                    telefono = input.telefono,
                    email = input.email,
                    notas = input.notas,
                ),
            )
        }.fold(
            onSuccess = { id ->
                syncManager.forzarSincronizacion(empresaId)
                GestionResult.Success(id)
            },
            onFailure = ::failure,
        )
    }

    override suspend fun actualizar(id: String, input: LocalInputData): GestionResult<Unit> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")

        return runCatching {
            remote.actualizar(
                empresaId = empresaId,
                id = id,
                dto = LocalUpdateDto(
                    nombre = input.nombre,
                    direccion = input.direccion,
                    cifONif = input.cifONif,
                    titularNombre = input.titularNombre,
                    telefono = input.telefono,
                    email = input.email,
                    notas = input.notas,
                ),
            )
        }.fold(
            onSuccess = {
                syncManager.forzarSincronizacion(empresaId)
                GestionResult.Success(Unit)
            },
            onFailure = ::failure,
        )
    }

    override suspend fun eliminar(id: String): GestionResult<Unit> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")

        return runCatching { remote.eliminar(empresaId, id) }.fold(
            onSuccess = {
                syncManager.forzarSincronizacion(empresaId)
                GestionResult.Success(Unit)
            },
            onFailure = ::failure,
        )
    }

    private fun <T> failure(throwable: Throwable): GestionResult<T> {
        val (error, code) = clasificarErrorGestion(throwable)
        Timber.w(throwable, "Locales gestor falló: %s", code)
        return GestionResult.Failure(error, code)
    }

    private fun empresaActivaId(): String? =
        (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
}
