package com.recre.app.core.data.repository

import com.recre.app.core.data.remote.LicenciasRemoteDataSource
import com.recre.app.core.data.remote.clasificarErrorGestion
import com.recre.app.core.data.remote.dto.ActualizarLicenciaParams
import com.recre.app.core.data.remote.dto.CrearLicenciaParams
import com.recre.app.core.data.remote.dto.EliminarLicenciaParams
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.util.DomainError
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Repositorio del CRUD de Licencias (T-66) en la app del técnico.
 *
 * Resuelve la empresa activa desde [SessionRepository] (multi-tenant),
 * delega la operación HTTP en [LicenciasRemoteDataSource] y, tras un
 * éxito, dispara un sync forzado para que la cache Room (T-51) refleje
 * la nueva fila y el resto de pantallas no muestren datos viejos.
 *
 * Errores: cualquier excepción se clasifica con [clasificarErrorGestion]
 * y se devuelve como [GestionResult.Failure] con el código i18n para
 * que el ViewModel pinte el snackbar adecuado sin duplicar el switch.
 */
interface LicenciasGestorRepository {

    /** @return id de la nueva licencia. */
    suspend fun crear(input: LicenciaInput): GestionResult<String>

    suspend fun actualizar(id: String, input: LicenciaInput): GestionResult<Unit>

    suspend fun eliminar(id: String): GestionResult<Unit>
}

/**
 * Input plano y ya validado para crear/actualizar una licencia. La
 * validación lexica vive en el ViewModel (igual que en la web hace zod).
 * Las cadenas vacías se han normalizado a `null` antes de llamar.
 */
data class LicenciaInput(
    val numero: String,
    val fechaExpedicion: String?,
    val fechaCaducidad: String?,
    val comunidadAutonoma: String?,
    val estado: String,
    val notas: String?,
)

@Singleton
class LicenciasGestorRepositoryImpl @Inject constructor(
    private val remote: LicenciasRemoteDataSource,
    private val sessionRepository: SessionRepository,
    private val syncManager: SyncManager,
) : LicenciasGestorRepository {

    override suspend fun crear(input: LicenciaInput): GestionResult<String> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")

        return runCatching {
            remote.crear(
                CrearLicenciaParams(
                    empresaId = empresaId,
                    numero = input.numero,
                    fechaExpedicion = input.fechaExpedicion,
                    fechaCaducidad = input.fechaCaducidad,
                    comunidadAutonoma = input.comunidadAutonoma,
                    estado = input.estado,
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

    override suspend fun actualizar(id: String, input: LicenciaInput): GestionResult<Unit> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")

        return runCatching {
            remote.actualizar(
                ActualizarLicenciaParams(
                    id = id,
                    numero = input.numero,
                    fechaExpedicion = input.fechaExpedicion,
                    fechaCaducidad = input.fechaCaducidad,
                    comunidadAutonoma = input.comunidadAutonoma,
                    estado = input.estado,
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

        return runCatching { remote.eliminar(EliminarLicenciaParams(id)) }.fold(
            onSuccess = {
                syncManager.forzarSincronizacion(empresaId)
                GestionResult.Success(Unit)
            },
            onFailure = ::failure,
        )
    }

    private fun <T> failure(throwable: Throwable): GestionResult<T> {
        val (error, code) = clasificarErrorGestion(throwable)
        Timber.w(throwable, "Licencias gestor falló: %s", code)
        return GestionResult.Failure(error, code)
    }

    private fun empresaActivaId(): String? =
        (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
}
