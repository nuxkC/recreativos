package com.recre.app.core.data.repository

import com.recre.app.core.data.remote.MaquinasRemoteDataSource
import com.recre.app.core.data.remote.clasificarErrorGestion
import com.recre.app.core.data.remote.dto.MaquinaInsertDto
import com.recre.app.core.data.remote.dto.MaquinaUpdateDto
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.util.DomainError
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Repositorio del CRUD de Máquinas (T-67). Mismo patrón que
 * [LicenciasGestorRepository].
 *
 * `valorCredito` se mantiene como `String` (`numeric(4,2)` serializa
 * como tal en PostgREST) para preservar la precisión decimal en toda la
 * cadena. Los contadores `bigint` viajan como `Long`.
 */
interface MaquinasGestorRepository {

    suspend fun crear(input: MaquinaInput): GestionResult<String>

    suspend fun actualizar(id: String, input: MaquinaInput): GestionResult<Unit>

    suspend fun eliminar(id: String): GestionResult<Unit>
}

/**
 * Input ya validado y normalizado.
 * - `valorCredito`: cadena `"X.YY"` con dos decimales y punto decimal.
 * - `contador*Inicial`: enteros >= 0.
 * - `estado`: uno de [com.recre.app.feature.gestion.ESTADOS_MAQUINA].
 */
data class MaquinaInput(
    val numeroSerie: String,
    val modelo: String?,
    val fabricante: String?,
    val valorCredito: String,
    val contadorEntradasInicial: Long,
    val contadorSalidasInicial: Long,
    val estado: String,
    val notas: String?,
)

@Singleton
class MaquinasGestorRepositoryImpl @Inject constructor(
    private val remote: MaquinasRemoteDataSource,
    private val sessionRepository: SessionRepository,
    private val syncManager: SyncManager,
) : MaquinasGestorRepository {

    override suspend fun crear(input: MaquinaInput): GestionResult<String> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")

        return runCatching {
            remote.crear(
                MaquinaInsertDto(
                    empresaId = empresaId,
                    numeroSerie = input.numeroSerie,
                    modelo = input.modelo,
                    fabricante = input.fabricante,
                    valorCredito = input.valorCredito,
                    contadorEntradasInicial = input.contadorEntradasInicial,
                    contadorSalidasInicial = input.contadorSalidasInicial,
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

    override suspend fun actualizar(id: String, input: MaquinaInput): GestionResult<Unit> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")

        return runCatching {
            remote.actualizar(
                empresaId = empresaId,
                id = id,
                dto = MaquinaUpdateDto(
                    numeroSerie = input.numeroSerie,
                    modelo = input.modelo,
                    fabricante = input.fabricante,
                    valorCredito = input.valorCredito,
                    contadorEntradasInicial = input.contadorEntradasInicial,
                    contadorSalidasInicial = input.contadorSalidasInicial,
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
        Timber.w(throwable, "Maquinas gestor falló: %s", code)
        return GestionResult.Failure(error, code)
    }

    private fun empresaActivaId(): String? =
        (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
}
