package com.recre.app.core.data.repository

import androidx.room.withTransaction
import com.recre.app.core.data.local.RecreDatabase
import com.recre.app.core.data.local.dao.CreditoLocalDao
import com.recre.app.core.data.local.dao.EmpresaParamsDao
import com.recre.app.core.data.local.dao.InstalacionDao
import com.recre.app.core.data.local.dao.LicenciaDao
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.local.dao.MaquinaDao
import com.recre.app.core.data.local.dao.SyncMetaDao
import com.recre.app.core.data.local.entity.CreditoLocalEntity
import com.recre.app.core.data.local.entity.EmpresaParamsEntity
import com.recre.app.core.data.local.entity.InstalacionEntity
import com.recre.app.core.data.local.entity.LicenciaEntity
import com.recre.app.core.data.local.entity.LocalEntity
import com.recre.app.core.data.local.entity.MaquinaEntity
import com.recre.app.core.data.local.entity.SyncMetaEntity
import com.recre.app.core.data.remote.SyncRemoteDataSource
import com.recre.app.core.data.remote.dto.CreditoLocalSaldoDto
import com.recre.app.core.data.remote.dto.EmpresaFullDto
import com.recre.app.core.data.remote.dto.InstalacionActivaDto
import com.recre.app.core.data.remote.dto.LicenciaDto
import com.recre.app.core.data.remote.dto.LocalDto
import com.recre.app.core.data.remote.dto.MaquinaDto
import com.recre.app.core.data.remote.dto.TolvaPendienteDto
import com.recre.app.core.sync.SyncSummary
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Sincronización inicial del inventario (T-51).
 *
 * Estrategia:
 * 1. Descarga en paralelo (5 endpoints) con `coroutineScope { async { ... } }`.
 *    Si cualquiera falla, el resto se cancela y se devuelve `Failure`.
 * 2. Transacción Room atómica: borra las filas previas de `empresa_id` y
 *    re-inserta las nuevas. No mezclamos snapshots de fechas distintas.
 * 3. Actualiza `sync_meta` con el `Instant` actual + resultado.
 *
 * Decisiones:
 * - **Borrado por empresa**, no global: si el técnico cambia de empresa
 *   nos quedamos con la cache de ambas (típicamente solo 1-2). Cuando
 *   T-65 añada "limpiar caché" la borraremos toda explícitamente.
 * - **Sin paginación**: el inventario de una empresa de máquinas
 *   recreativas tiene como mucho cientos de filas por tabla. PostgREST
 *   por defecto limita a 1000 — si llegamos a esa frontera el primer
 *   síntoma será que `fetchMaquinas().size == 1000`, y entonces se
 *   añadirá range/`order by` con cursor en una mejora futura.
 * - **Mappers DTO -> entity** privados, no contaminan el resto del módulo.
 */
interface SyncRepository {
    suspend fun sincronizar(empresaId: String): DomainResult<SyncSummary>

    /** Última vez que se sincronizó la empresa, observable. */
    fun observarUltimaSync(empresaId: String): Flow<Instant?>
}

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val remote: SyncRemoteDataSource,
    private val database: RecreDatabase,
    private val empresaParamsDao: EmpresaParamsDao,
    private val localDao: LocalDao,
    private val maquinaDao: MaquinaDao,
    private val licenciaDao: LicenciaDao,
    private val instalacionDao: InstalacionDao,
    private val syncMetaDao: SyncMetaDao,
    private val creditoLocalDao: CreditoLocalDao,
) : SyncRepository {

    override suspend fun sincronizar(empresaId: String): DomainResult<SyncSummary> {
        val descargaResult = runCatching {
            coroutineScope {
                val empresaJob = async { remote.fetchEmpresa(empresaId) }
                val localesJob = async { remote.fetchLocales(empresaId) }
                val maquinasJob = async { remote.fetchMaquinas(empresaId) }
                val licenciasJob = async { remote.fetchLicencias(empresaId) }
                val instalacionesJob = async { remote.fetchInstalacionesActivas(empresaId) }
                val creditosJob = async { remote.fetchCreditosAbiertos(empresaId) }
                val tolvaPendientesJob = async { remote.fetchTolvaPendientes(empresaId) }
                Descarga(
                    empresa = empresaJob.await(),
                    locales = localesJob.await(),
                    maquinas = maquinasJob.await(),
                    licencias = licenciasJob.await(),
                    instalaciones = instalacionesJob.await(),
                    creditos = creditosJob.await(),
                    tolvaPendientes = tolvaPendientesJob.await(),
                )
            }
        }

        val descarga = descargaResult.fold(
            onSuccess = { it },
            onFailure = { throwable ->
                Timber.w(throwable, "Sync %s — descarga fallida", empresaId)
                return DomainResult.Failure(throwable.toDomainError())
            },
        )

        val now = Instant.now()
        val persistResult = runCatching {
            database.withTransaction {
                empresaParamsDao.borrarPorEmpresa(empresaId)
                empresaParamsDao.upsert(descarga.empresa.toEntity())

                localDao.borrarPorEmpresa(empresaId)
                localDao.upsertAll(descarga.locales.map { it.toEntity() })

                maquinaDao.borrarPorEmpresa(empresaId)
                maquinaDao.upsertAll(descarga.maquinas.map { it.toEntity() })

                licenciaDao.borrarPorEmpresa(empresaId)
                licenciaDao.upsertAll(descarga.licencias.map { it.toEntity() })

                val pendientesPorInstalacion =
                    descarga.tolvaPendientes.associate { it.instalacionId to it.pendiente }
                instalacionDao.borrarPorEmpresa(empresaId)
                instalacionDao.upsertAll(
                    descarga.instalaciones.map {
                        it.toEntity(pendientesPorInstalacion[it.instalacionId] ?: "0")
                    },
                )

                creditoLocalDao.borrarPorEmpresa(empresaId)
                creditoLocalDao.upsertAll(descarga.creditos.map { it.toEntity() })

                syncMetaDao.upsert(
                    SyncMetaEntity(
                        empresaId = empresaId,
                        lastSyncedAt = now,
                        lastResult = "success",
                    ),
                )
            }
        }

        return persistResult.fold(
            onSuccess = {
                DomainResult.Success(
                    SyncSummary(
                        empresaId = empresaId,
                        locales = descarga.locales.size,
                        maquinas = descarga.maquinas.size,
                        licencias = descarga.licencias.size,
                        instalacionesActivas = descarga.instalaciones.size,
                        syncedAt = now,
                    ),
                )
            },
            onFailure = { throwable ->
                Timber.e(throwable, "Sync %s — persist fallido", empresaId)
                DomainResult.Failure(DomainError.Unknown(throwable.message))
            },
        )
    }

    override fun observarUltimaSync(empresaId: String): Flow<Instant?> =
        syncMetaDao.observe(empresaId).map { it?.lastSyncedAt }

    // -------------------------------------------------------------------------
    // Mappers DTO -> Entity
    // -------------------------------------------------------------------------

    private fun EmpresaFullDto.toEntity(): EmpresaParamsEntity = EmpresaParamsEntity(
        empresaId = id,
        nombre = nombre,
        cif = cif,
        direccion = direccion,
        telefono = telefono,
        email = email,
        logoUrl = logoUrl,
        zonaHoraria = zonaHoraria,
        ticketCabecera = ticketCabecera,
        ticketPie = ticketPie,
        redondeoRecaudacion = redondeoRecaudacion,
        porcentajeRecuperacion = porcentajeRecuperacion,
        updatedAt = parseTimestamp(updatedAt),
    )

    private fun LocalDto.toEntity(): LocalEntity = LocalEntity(
        id = id,
        empresaId = empresaId,
        nombre = nombre,
        direccion = direccion,
        cifONif = cifONif,
        titularNombre = titularNombre,
        telefono = telefono,
        email = email,
        notas = notas,
        comunidadAutonoma = comunidadAutonoma,
        provinciaCodigo = provinciaCodigo,
        municipioCodigo = municipioCodigo,
        calle = calle,
        codigoPostal = codigoPostal,
        porcentajeRecuperacion = porcentajeRecuperacion,
        updatedAt = parseTimestamp(updatedAt),
    )

    private fun CreditoLocalSaldoDto.toEntity(): CreditoLocalEntity = CreditoLocalEntity(
        creditoId = creditoId,
        empresaId = empresaId,
        localId = localId,
        tipo = tipo,
        instalacionId = instalacionId,
        principal = principal,
        tipoInteres = tipoInteres,
        fecha = fecha,
        estado = estado,
        notas = notas,
        recuperado = recuperado,
        saldo = saldo,
    )

    private fun MaquinaDto.toEntity(): MaquinaEntity = MaquinaEntity(
        id = id,
        empresaId = empresaId,
        numeroSerie = numeroSerie,
        modelo = modelo,
        fabricante = fabricante,
        valorCredito = valorCredito,
        contadorEntradasInicial = contadorEntradasInicial,
        contadorSalidasInicial = contadorSalidasInicial,
        estado = estado,
        notas = notas,
        updatedAt = parseTimestamp(updatedAt),
    )

    private fun LicenciaDto.toEntity(): LicenciaEntity = LicenciaEntity(
        id = id,
        empresaId = empresaId,
        numero = numero,
        tipo = tipo,
        fechaExpedicion = fechaExpedicion,
        fechaCaducidad = fechaCaducidad,
        comunidadAutonoma = comunidadAutonoma,
        estado = estado,
        notas = notas,
        updatedAt = parseTimestamp(updatedAt),
    )

    private fun InstalacionActivaDto.toEntity(pendienteTolva: String): InstalacionEntity = InstalacionEntity(
        id = instalacionId,
        empresaId = empresaId,
        maquinaId = maquinaId,
        licenciaId = licenciaId,
        localId = localId,
        fechaInicio = fechaInicio,
        tasaSemanal = tasaSemanal,
        porcentajeLocal = porcentajeLocal,
        contadorEntradasBase = contadorEntradasBase,
        contadorSalidasBase = contadorSalidasBase,
        estado = estado,
        baselineEntradas = baselineEntradas,
        baselineSalidas = baselineSalidas,
        baselineFecha = parseTimestamp(baselineFecha),
        baselineOrigen = baselineOrigen,
        baselineReferenciaId = baselineReferenciaId,
        pendienteTolva = pendienteTolva,
    )

    /**
     * PostgREST devuelve `timestamptz` como ISO 8601 con offset (típicamente
     * `2026-05-20T10:30:00+00:00`). `OffsetDateTime.parse` acepta tanto
     * `+00:00` como `Z`. Si por algún motivo viene sin offset, caemos al
     * fallback de `Instant.parse` añadiendo Z.
     */
    private fun parseTimestamp(raw: String): Instant {
        val iso = raw.replace(' ', 'T')
        return runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }
            .getOrElse {
                Instant.parse(if (iso.endsWith("Z")) iso else "${iso}Z")
            }
    }

    private fun Throwable.toDomainError(): DomainError {
        val msg = message
        return when {
            msg?.contains("network", ignoreCase = true) == true -> DomainError.Network(msg)
            msg?.contains("jwt", ignoreCase = true) == true -> DomainError.Auth(msg)
            else -> DomainError.Unknown(msg)
        }
    }

    private data class Descarga(
        val empresa: EmpresaFullDto,
        val locales: List<LocalDto>,
        val maquinas: List<MaquinaDto>,
        val licencias: List<LicenciaDto>,
        val instalaciones: List<InstalacionActivaDto>,
        val creditos: List<CreditoLocalSaldoDto>,
        val tolvaPendientes: List<TolvaPendienteDto>,
    )
}
