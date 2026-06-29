package com.recre.app.feature.cuadre.data

import com.recre.app.core.data.local.dao.EmpresaParamsDao
import com.recre.app.core.data.remote.CuadreRemoteDataSource
import com.recre.app.core.data.remote.dto.aCuadreSemanal
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import com.recre.app.feature.cuadre.domain.CuadreSemanal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Carga el cuadre (lado "esperado") de una semana concreta desde la vista.
 * Si la semana no tiene recaudaciones, devuelve un CuadreSemanal vacío (total 0).
 */
@Singleton
class CuadreRepository @Inject constructor(
    private val remote: CuadreRemoteDataSource,
    private val sessionRepository: SessionRepository,
    private val empresaParamsDao: EmpresaParamsDao,
) {
    suspend fun cargarSemana(semanaInicio: LocalDate): DomainResult<CuadreSemanal> =
        try {
            val filas = remote.obtener()
                .filter { it.semanaInicio == semanaInicio.toString() }
            DomainResult.Success(filas.aCuadreSemanal(semanaInicio))
        } catch (e: Exception) {
            Timber.e(e, "Fallo cargando el cuadre semanal")
            DomainResult.Failure(DomainError.Network())
        }

    /** TZ de la empresa para alinear el cálculo de la semana ISO con el servidor. */
    suspend fun zonaHoraria(): String {
        val empresaId = (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
            ?: return "Europe/Madrid"
        return empresaParamsDao.observe(empresaId).first()?.zonaHoraria ?: "Europe/Madrid"
    }
}
