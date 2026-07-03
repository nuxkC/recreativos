package com.recre.app.core.data.repository

import com.recre.app.core.data.local.dao.InstalacionDao
import com.recre.app.core.data.local.dao.LicenciaDao
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.local.dao.MaquinaDao
import com.recre.app.core.data.local.entity.InstalacionEntity
import com.recre.app.core.data.local.entity.LicenciaEntity
import com.recre.app.core.data.local.entity.LocalEntity
import com.recre.app.core.data.local.entity.MaquinaEntity
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Lectura del inventario cacheado en Room, ya joinado en modelos de
 * dominio (`LocalResumen`, `MaquinaConInstalacion`, `LocalDetalle`).
 *
 * Estrategia: combinar los `Flow<List<...>>` de los DAOs y resolver los
 * joins en memoria con maps de lookup. Cantidad de filas por empresa
 * está en cientos, así que no compensa montar vistas SQL ni `@Relation`s
 * anidadas.
 *
 * Multi-tenant: todas las queries se filtran por `empresa_id` activa,
 * leída de [SessionRepository]. Si no hay empresa activa, los flows
 * emiten `emptyList()` o `null` para que la UI muestre estado vacío sin
 * leak entre cuentas.
 */
interface InventoryRepository {

    /** Locales del tenant activo con contador de máquinas activas. */
    fun observarLocalesResumen(): Flow<List<LocalResumen>>

    /**
     * Detalle de un local concreto: cabecera + máquinas activas. Emite
     * `null` cuando el local no existe o pertenece a otra empresa.
     */
    fun observarLocalDetalle(localId: String): Flow<LocalDetalle?>

    /**
     * Una única instalación activa con su máquina y licencia ya joinadas.
     * Emite `null` cuando la instalación no existe (cerrada, borrada o de
     * otra empresa). Lo consume el flujo de recaudación (T-54..T-56).
     */
    fun observarMaquinaPorInstalacion(instalacionId: String): Flow<MaquinaConInstalacion?>
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val localDao: LocalDao,
    private val maquinaDao: MaquinaDao,
    private val licenciaDao: LicenciaDao,
    private val instalacionDao: InstalacionDao,
    private val sessionRepository: SessionRepository,
) : InventoryRepository {

    override fun observarLocalesResumen(): Flow<List<LocalResumen>> =
        empresaActivaIdFlow().flatMapLatest { empresaId ->
            if (empresaId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    localDao.observarPorEmpresa(empresaId),
                    instalacionDao.observarActivasPorEmpresa(empresaId),
                ) { locales, instalaciones ->
                    val activasPorLocal = instalaciones.groupingBy { it.localId }.eachCount()
                    locales.map { it.toResumen(activasPorLocal[it.id] ?: 0) }
                }
            }
        }

    override fun observarLocalDetalle(localId: String): Flow<LocalDetalle?> =
        empresaActivaIdFlow().flatMapLatest { empresaId ->
            if (empresaId == null) {
                flowOf(null)
            } else {
                combine(
                    localDao.observe(localId),
                    instalacionDao.observarActivasPorLocal(empresaId, localId),
                    maquinaDao.observarPorEmpresa(empresaId),
                    licenciaDao.observarPorEmpresa(empresaId),
                ) { local, instalaciones, maquinas, licencias ->
                    if (local == null || local.empresaId != empresaId) {
                        null
                    } else {
                        construirDetalle(local, instalaciones, maquinas, licencias)
                    }
                }
            }
        }

    override fun observarMaquinaPorInstalacion(instalacionId: String): Flow<MaquinaConInstalacion?> =
        empresaActivaIdFlow().flatMapLatest { empresaId ->
            if (empresaId == null) {
                flowOf(null)
            } else {
                combine(
                    instalacionDao.observe(instalacionId),
                    maquinaDao.observarPorEmpresa(empresaId),
                    licenciaDao.observarPorEmpresa(empresaId),
                    localDao.observarPorEmpresa(empresaId),
                ) { instalacion, maquinas, licencias, locales ->
                    if (instalacion == null || instalacion.empresaId != empresaId) {
                        null
                    } else {
                        val maquina = maquinas.firstOrNull { it.id == instalacion.maquinaId }
                            ?: return@combine null
                        val licencia = licencias.firstOrNull { it.id == instalacion.licenciaId }
                            ?: return@combine null
                        val local = locales.firstOrNull { it.id == instalacion.localId }
                            ?: return@combine null
                        toMaquinaConInstalacion(instalacion, maquina, licencia, local)
                    }
                }
            }
        }

    private fun construirDetalle(
        local: LocalEntity,
        instalaciones: List<InstalacionEntity>,
        maquinas: List<MaquinaEntity>,
        licencias: List<LicenciaEntity>,
    ): LocalDetalle {
        val maquinasMap = maquinas.associateBy { it.id }
        val licenciasMap = licencias.associateBy { it.id }

        val maquinasConInstalacion = instalaciones
            .mapNotNull { inst ->
                val maquina = maquinasMap[inst.maquinaId] ?: return@mapNotNull null
                val licencia = licenciasMap[inst.licenciaId] ?: return@mapNotNull null
                toMaquinaConInstalacion(inst, maquina, licencia, local)
            }
            .sortedBy { it.numeroSerie.lowercase() }

        return LocalDetalle(
            local = local.toResumen(maquinasConInstalacion.size),
            maquinas = maquinasConInstalacion,
        )
    }

    private fun toMaquinaConInstalacion(
        inst: InstalacionEntity,
        maquina: MaquinaEntity,
        licencia: LicenciaEntity,
        local: LocalEntity,
    ): MaquinaConInstalacion = MaquinaConInstalacion(
        instalacionId = inst.id,
        maquinaId = maquina.id,
        numeroSerie = maquina.numeroSerie,
        modelo = maquina.modelo,
        fabricante = maquina.fabricante,
        estado = maquina.estado,
        valorCredito = maquina.valorCredito,
        licenciaNumero = licencia.numero,
        tasaSemanal = inst.tasaSemanal,
        porcentajeLocal = inst.porcentajeLocal,
        baselineEntradas = inst.baselineEntradas,
        baselineSalidas = inst.baselineSalidas,
        baselineFecha = inst.baselineFecha,
        baselineOrigen = inst.baselineOrigen,
        baselineReferenciaId = inst.baselineReferenciaId,
        localId = local.id,
        localNombre = local.nombre,
        localDireccion = local.direccion,
        pendienteTolva = inst.pendienteTolva,
    )

    private fun empresaActivaIdFlow(): Flow<String?> =
        kotlinx.coroutines.flow.flow {
            sessionRepository.state.collect { state ->
                emit((state as? SessionState.Active)?.empresa?.id)
            }
        }

    private fun LocalEntity.toResumen(maquinasActivas: Int): LocalResumen = LocalResumen(
        id = id,
        nombre = nombre,
        direccion = direccion,
        calle = calle,
        codigoPostal = codigoPostal,
        comunidadAutonoma = comunidadAutonoma,
        titularNombre = titularNombre,
        telefono = telefono,
        maquinasActivas = maquinasActivas,
    )
}
