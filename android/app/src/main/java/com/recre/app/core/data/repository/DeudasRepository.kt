package com.recre.app.core.data.repository

import com.recre.app.core.data.local.dao.CreditoLocalDao
import com.recre.app.core.data.local.entity.CreditoLocalEntity
import com.recre.app.core.data.remote.DeudasRemoteDataSource
import com.recre.app.core.data.remote.clasificarErrorGestion
import com.recre.app.core.data.remote.dto.CondonarCreditoParams
import com.recre.app.core.data.remote.dto.CrearPrestamoParams
import com.recre.app.core.data.remote.dto.RegistrarRecuperacionEfectivoParams
import com.recre.app.core.data.remote.dto.SetPorcentajeRecuperacionLocalParams
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.util.DomainError
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/** Una entrada del libro mayor de abonos del local (vista de UI). */
data class RecuperacionLedger(
    val id: String,
    val creditoId: String,
    /** `efectivo` | `recaudacion`. */
    val origen: String,
    val importe: BigDecimal,
    /** Fecha operativa (ISO con offset). */
    val fecha: String,
    val notas: String?,
)

/**
 * Ficha de deudas del local (T-215).
 *
 * - Lectura de deudas abiertas: OFFLINE desde Room (las llenó el sync). Sirven
 *   para el saldo y la lista de deudas aunque no haya red.
 * - Lectura del libro mayor de abonos: EN LÍNEA (histórico, no se cachea).
 * - Escrituras (préstamo, abono, condonación, %): EN LÍNEA vía RPC; tras el
 *   éxito se fuerza un sync para refrescar los saldos cacheados.
 */
interface DeudasRepository {

    fun observarCreditos(localId: String): Flow<List<CreditoLocalEntity>>

    suspend fun obtenerLedger(localId: String): GestionResult<List<RecuperacionLedger>>

    suspend fun crearPrestamo(localId: String, principal: String, notas: String?): GestionResult<Unit>

    suspend fun registrarPago(creditoId: String, importe: String, notas: String?): GestionResult<Unit>

    suspend fun condonar(creditoId: String, notas: String?): GestionResult<Unit>

    /** Fija el override del % del local. `null` = heredar el de la empresa. */
    suspend fun setPorcentaje(localId: String, porcentaje: Int?): GestionResult<Unit>
}

@Singleton
class DeudasRepositoryImpl @Inject constructor(
    private val remote: DeudasRemoteDataSource,
    private val creditoLocalDao: CreditoLocalDao,
    private val sessionRepository: SessionRepository,
    private val syncManager: SyncManager,
) : DeudasRepository {

    override fun observarCreditos(localId: String): Flow<List<CreditoLocalEntity>> =
        creditoLocalDao.observarPorLocal(localId)

    override suspend fun obtenerLedger(localId: String): GestionResult<List<RecuperacionLedger>> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")
        return runCatching { remote.fetchLedger(empresaId, localId) }.fold(
            onSuccess = { filas ->
                val ledger = filas
                    .map {
                        RecuperacionLedger(
                            id = it.id,
                            creditoId = it.creditoId,
                            origen = it.origen,
                            importe = BigDecimal(it.importe),
                            fecha = it.fecha,
                            notas = it.notas,
                        )
                    }
                    .sortedByDescending { it.fecha }
                    .take(LIMITE_LEDGER)
                GestionResult.Success(ledger)
            },
            onFailure = ::failure,
        )
    }

    override suspend fun crearPrestamo(
        localId: String,
        principal: String,
        notas: String?,
    ): GestionResult<Unit> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")
        return runCatching {
            remote.crearPrestamo(
                CrearPrestamoParams(
                    empresaId = empresaId,
                    localId = localId,
                    principal = principal,
                    notas = notas,
                ),
            )
        }.fold(onSuccess = { tras(empresaId) }, onFailure = ::failure)
    }

    override suspend fun registrarPago(
        creditoId: String,
        importe: String,
        notas: String?,
    ): GestionResult<Unit> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")
        return runCatching {
            remote.registrarRecuperacionEfectivo(
                RegistrarRecuperacionEfectivoParams(
                    creditoId = creditoId,
                    importe = importe,
                    notas = notas,
                ),
            )
        }.fold(onSuccess = { tras(empresaId) }, onFailure = ::failure)
    }

    override suspend fun condonar(creditoId: String, notas: String?): GestionResult<Unit> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")
        return runCatching {
            remote.condonarCredito(CondonarCreditoParams(creditoId = creditoId, notas = notas))
        }.fold(onSuccess = { tras(empresaId) }, onFailure = ::failure)
    }

    override suspend fun setPorcentaje(localId: String, porcentaje: Int?): GestionResult<Unit> {
        val empresaId = empresaActivaId()
            ?: return GestionResult.Failure(DomainError.Auth("Sin empresa activa"), "auth")
        return runCatching {
            remote.setPorcentajeRecuperacionLocal(
                SetPorcentajeRecuperacionLocalParams(localId = localId, porcentaje = porcentaje),
            )
        }.fold(onSuccess = { tras(empresaId) }, onFailure = ::failure)
    }

    /** Refresca la cache tras una escritura y devuelve éxito. */
    private fun tras(empresaId: String): GestionResult<Unit> {
        syncManager.forzarSincronizacion(empresaId)
        return GestionResult.Success(Unit)
    }

    private fun <T> failure(throwable: Throwable): GestionResult<T> {
        val (error, code) = clasificarErrorDeuda(throwable)
        Timber.w(throwable, "Deudas falló: %s", code)
        return GestionResult.Failure(error, code)
    }

    private fun empresaActivaId(): String? =
        (sessionRepository.state.value as? SessionState.Active)?.empresa?.id

    private companion object {
        const val LIMITE_LEDGER = 50
    }
}

/**
 * Mapea los errores de las RPCs de deuda a códigos i18n propios. Los códigos
 * SQLSTATE de estas funciones (`42501`, `22023`, `no_data_found`) no encajan en
 * el clasificador genérico de gestión, así que los detectamos por código/texto
 * y delegamos el resto (red, auth, desconocido) en [clasificarErrorGestion].
 */
fun clasificarErrorDeuda(throwable: Throwable): Pair<DomainError, String> {
    val msg = throwable.message ?: ""
    val lower = msg.lowercase()
    return when {
        msg.contains("42501") ||
            lower.contains("sin permiso") ||
            lower.contains("administrador puede condonar") ->
            DomainError.Auth(msg) to "deuda_sin_permiso"
        msg.contains("23514") || lower.contains("supera el saldo") ->
            DomainError.Validation(msg) to "deuda_importe_supera_saldo"
        msg.contains("22023") ||
            lower.contains("no está abierta") ||
            lower.contains("debe ser > 0") ||
            lower.contains("fuera de rango") ->
            DomainError.Validation(msg) to "deuda_operacion_invalida"
        msg.contains("no_data_found") ||
            msg.contains("P0002") ||
            lower.contains("no encontrad") ->
            DomainError.NotFound(msg) to "deuda_no_encontrada"
        else -> clasificarErrorGestion(throwable)
    }
}
