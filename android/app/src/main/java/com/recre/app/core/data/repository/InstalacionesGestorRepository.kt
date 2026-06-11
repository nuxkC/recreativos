package com.recre.app.core.data.repository

import com.recre.app.core.data.remote.InstalacionesRemoteDataSource
import com.recre.app.core.data.remote.clasificarErrorGestion
import com.recre.app.core.data.remote.dto.CerrarInstalacionRequest
import com.recre.app.core.data.remote.dto.ActualizarInstalacionParams
import com.recre.app.core.data.remote.dto.CrearInstalacionParams
import com.recre.app.core.data.remote.dto.EliminarInstalacionParams
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.util.DomainError
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Repositorio del CRUD de Instalaciones (T-69) en la app del técnico.
 *
 * Diferencias respecto a los otros tres CRUDs:
 *  - El **cierre** pasa por la Edge Function `cerrar-instalacion` (T-23)
 *    en vez de UPDATE directo. El form de la app llama a [cerrar].
 *  - El **estado** no se edita desde la pantalla; siempre se crea
 *    `activa` y la única transición permitida es `activa → cerrada` vía
 *    [cerrar].
 *  - Las **FKs** (`maquina_id`, `licencia_id`, `local_id`) son
 *    inmutables después del alta — cambiarlas rompería la baseline.
 */
interface InstalacionesGestorRepository {

    suspend fun crear(input: InstalacionInputData): GestionResult<String>

    suspend fun actualizar(id: String, input: InstalacionUpdateData): GestionResult<Unit>

    suspend fun eliminar(id: String): GestionResult<Unit>

    /**
     * Llama a `cerrar-instalacion` (T-23). Tras el éxito, refresca la
     * cache para que la instalación desaparezca del listado de máquinas
     * activas y los locks se hayan liberado.
     */
    suspend fun cerrar(id: String, fechaFin: String, notas: String?): GestionResult<Unit>
}

/** Datos completos para el alta de una instalación. */
data class InstalacionInputData(
    val maquinaId: String,
    val licenciaId: String,
    val localId: String,
    val fechaInicio: String,
    val tasaSemanal: String,
    val porcentajeLocal: String,
    val notas: String?,
)

/** Datos editables después del alta (FKs y estado son inmutables). */
data class InstalacionUpdateData(
    val fechaInicio: String,
    val tasaSemanal: String,
    val porcentajeLocal: String,
    val notas: String?,
)

@Singleton
class InstalacionesGestorRepositoryImpl @Inject constructor(
    private val remote: InstalacionesRemoteDataSource,
    private val sessionRepository: SessionRepository,
    private val syncManager: SyncManager,
) : InstalacionesGestorRepository {

    override suspend fun crear(input: InstalacionInputData): GestionResult<String> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")

        return runCatching {
            remote.crear(
                CrearInstalacionParams(
                    empresaId = empresaId,
                    maquinaId = input.maquinaId,
                    licenciaId = input.licenciaId,
                    localId = input.localId,
                    fechaInicio = input.fechaInicio,
                    tasaSemanal = input.tasaSemanal,
                    porcentajeLocal = input.porcentajeLocal,
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

    override suspend fun actualizar(id: String, input: InstalacionUpdateData): GestionResult<Unit> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")

        return runCatching {
            remote.actualizar(
                ActualizarInstalacionParams(
                    id = id,
                    fechaInicio = input.fechaInicio,
                    tasaSemanal = input.tasaSemanal,
                    porcentajeLocal = input.porcentajeLocal,
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

        return runCatching { remote.eliminar(EliminarInstalacionParams(id)) }.fold(
            onSuccess = {
                syncManager.forzarSincronizacion(empresaId)
                GestionResult.Success(Unit)
            },
            onFailure = ::failure,
        )
    }

    override suspend fun cerrar(
        id: String,
        fechaFin: String,
        notas: String?,
    ): GestionResult<Unit> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")

        return runCatching {
            remote.cerrar(
                CerrarInstalacionRequest(
                    instalacionId = id,
                    fechaFin = fechaFin,
                    notas = notas,
                ),
            )
        }.fold(
            onSuccess = {
                syncManager.forzarSincronizacion(empresaId)
                GestionResult.Success(Unit)
            },
            onFailure = ::failureCerrar,
        )
    }

    private fun <T> failure(throwable: Throwable): GestionResult<T> {
        val (error, code) = clasificarErrorGestion(throwable)
        Timber.w(throwable, "Instalaciones gestor falló: %s", code)
        return GestionResult.Failure(error, code)
    }

    /**
     * El cierre tiene códigos i18n distintos al CRUD genérico (vienen de
     * la Edge Function `cerrar-instalacion`):
     *   - `validation_error` -> fechaFin no coherente o body inválido.
     *   - `conflict`         -> ya está cerrada.
     *   - `not_found`        -> id desconocido.
     *   - `forbidden`/`unauthorized` -> sin rol/sesión.
     *   - resto -> `cerrar_fallido`.
     *
     * El código viaja en el body JSON de la Edge Function. La excepción
     * que llega aquí ya pasó por [clasificarErrorGestion]; usamos su
     * mensaje (que contiene el JSON original) para discriminar.
     */
    private fun <T> failureCerrar(throwable: Throwable): GestionResult<T> {
        val (error, codeBase) = clasificarErrorGestion(throwable)
        val msg = (throwable.message ?: "").lowercase()
        val code = when {
            codeBase == "validation_error" -> "cerrar_fecha_fin_invalida"
            msg.contains("\"code\":\"conflict\"") || msg.contains("conflict") &&
                !msg.contains("23") -> "cerrar_ya_cerrada"
            msg.contains("\"code\":\"not_found\"") -> "cerrar_no_encontrada"
            msg.contains("\"code\":\"forbidden\"") ||
                msg.contains("\"code\":\"unauthorized\"") -> "cerrar_sin_permiso"
            codeBase == "network" -> "network"
            codeBase == "auth" -> "auth"
            else -> "cerrar_fallido"
        }
        Timber.w(throwable, "Cerrar instalación falló: %s", code)
        return GestionResult.Failure(error, code)
    }

    private fun empresaActivaId(): String? =
        (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
}
