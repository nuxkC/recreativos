package com.recre.app.core.data.repository

import com.recre.app.core.data.remote.AlertasRemoteDataSource
import com.recre.app.core.data.remote.dto.AlertaDto
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Repositorio de alertas in-app (T-64).
 *
 * Wrapper sobre [AlertasRemoteDataSource] que (1) inyecta la empresa
 * activa de [SessionRepository] y (2) mapea Throwables a [DomainError]
 * para que la UI muestre copy específico.
 *
 * No mantiene cache: las alertas son pocas y la pantalla las refresca
 * al entrar. El badge en LocalesScreen se refrescará a través del
 * conteo, que también va contra el backend.
 */
interface AlertasRepository {

    suspend fun listarPendientes(): DomainResult<List<Alerta>>

    suspend fun contarPendientes(): DomainResult<Long>

    suspend fun marcarLeida(alertaId: String): DomainResult<Unit>

    suspend fun marcarTodasLeidas(): DomainResult<Unit>
}

@Singleton
class AlertasRepositoryImpl @Inject constructor(
    private val remote: AlertasRemoteDataSource,
    private val sessionRepository: SessionRepository,
) : AlertasRepository {

    override suspend fun listarPendientes(): DomainResult<List<Alerta>> {
        val empresaId = empresaActiva() ?: return DomainResult.Failure(DomainError.Auth())
        return runCatching { remote.listarPendientes(empresaId) }.fold(
            onSuccess = { rows -> DomainResult.Success(rows.map(::mapToDomain)) },
            onFailure = ::mapErrorToDomain,
        )
    }

    override suspend fun contarPendientes(): DomainResult<Long> {
        val empresaId = empresaActiva() ?: return DomainResult.Failure(DomainError.Auth())
        return runCatching { remote.contarPendientes(empresaId) }.fold(
            onSuccess = { DomainResult.Success(it) },
            onFailure = ::mapErrorToDomain,
        )
    }

    override suspend fun marcarLeida(alertaId: String): DomainResult<Unit> {
        val empresaId = empresaActiva() ?: return DomainResult.Failure(DomainError.Auth())
        return runCatching { remote.marcarLeida(empresaId, alertaId) }.fold(
            onSuccess = { DomainResult.Success(Unit) },
            onFailure = ::mapErrorToDomain,
        )
    }

    override suspend fun marcarTodasLeidas(): DomainResult<Unit> {
        val empresaId = empresaActiva() ?: return DomainResult.Failure(DomainError.Auth())
        return runCatching { remote.marcarTodasLeidas(empresaId) }.fold(
            onSuccess = { DomainResult.Success(Unit) },
            onFailure = ::mapErrorToDomain,
        )
    }

    private suspend fun empresaActiva(): String? =
        (sessionRepository.state.value as? SessionState.Active)?.empresa?.id

    private fun mapToDomain(dto: AlertaDto): Alerta = Alerta(
        id = dto.id,
        tipo = TipoAlerta.fromString(dto.tipo),
        mensaje = dto.mensaje,
        referenciaId = dto.referenciaId,
        creadaEn = runCatching { Instant.parse(dto.creadaEn) }.getOrElse { Instant.now() },
        leida = dto.leida,
    )

    private fun <T> mapErrorToDomain(throwable: Throwable): DomainResult<T> {
        Timber.w(throwable, "AlertasRepository error")
        val message = throwable.message.orEmpty()
        return when {
            message.contains("network", ignoreCase = true) ||
                message.contains("connect", ignoreCase = true) ->
                DomainResult.Failure(DomainError.Network(message))

            message.contains("forbidden", ignoreCase = true) ->
                DomainResult.Failure(DomainError.Auth(message))

            else -> DomainResult.Failure(DomainError.Unknown(message))
        }
    }
}

/**
 * Modelo de dominio de una alerta in-app.
 *
 * `referenciaId` apunta a la fila origen — interpretarlo según `tipo`:
 *  - `recaudacion_conflicto` / `recaudacion_anulada` → id de la
 *    recaudación. La pantalla puede deep-linkar al detalle (T-63).
 *  - `licencia_caducidad` → id de la licencia (no usado en este PR
 *    porque el CRUD de licencias en la app llega en T-66).
 *  - `local_sin_recaudar` → id del local.
 */
data class Alerta(
    val id: String,
    val tipo: TipoAlerta,
    val mensaje: String,
    val referenciaId: String?,
    val creadaEn: Instant,
    val leida: Boolean,
)

enum class TipoAlerta {
    RecaudacionConflicto,
    RecaudacionAnulada,
    LicenciaCaducidad,
    LocalSinRecaudar,
    Otro,
    ;

    companion object {
        fun fromString(value: String): TipoAlerta = when (value) {
            "recaudacion_conflicto" -> RecaudacionConflicto
            "recaudacion_anulada" -> RecaudacionAnulada
            "licencia_caducidad" -> LicenciaCaducidad
            "local_sin_recaudar" -> LocalSinRecaudar
            else -> Otro
        }
    }
}
