package com.recre.app.feature.cuadre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.CuadreRecuentoDao
import com.recre.app.core.data.local.dao.RecaudacionPendienteDao
import com.recre.app.core.data.local.entity.CuadreRecuentoEntity
import com.recre.app.core.data.repository.AuthRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.RealtimeManager
import com.recre.app.core.sync.RecaudacionUploadManager
import com.recre.app.core.util.DomainResult
import com.recre.app.feature.cuadre.data.CuadreRecuentoStore
import com.recre.app.feature.cuadre.data.CuadreRepository
import com.recre.app.feature.cuadre.domain.CuadreSemanal
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orquesta el cuadre semanal: combina el "esperado" del servidor
 * ([CuadreRepository]), el recuento físico persistido ([CuadreRecuentoDao] +
 * [CuadreRecuentoStore]), el contador de la cola de subida
 * ([RecaudacionPendienteDao]) y realtime ([RealtimeManager]) en un
 * [CuadreUiState].
 *
 * Toda la decisión de estado vive en la función pura [construirEstado]; el VM
 * solo cablea fuentes y recompone. El recuento que el técnico teclea se mantiene
 * en memoria ([contado]) y se persiste a cada cambio para sobrevivir a cierres.
 */
@HiltViewModel
class CuadreViewModel @Inject constructor(
    private val repository: CuadreRepository,
    private val recuentoDao: CuadreRecuentoDao,
    private val recuentoStore: CuadreRecuentoStore,
    private val pendientesDao: RecaudacionPendienteDao,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val realtimeManager: RealtimeManager,
    private val uploadManager: RecaudacionUploadManager,
) : ViewModel() {

    private val _state = MutableStateFlow<CuadreUiState>(CuadreUiState.Cargando)
    val state: StateFlow<CuadreUiState> = _state.asStateFlow()

    private lateinit var empresaId: String
    private lateinit var tecnicoId: String

    private var semanaInicio: LocalDate = LocalDate.now()
    private var cuadre: CuadreSemanal? = null
    private var contado: Map<BigDecimal, Long> = emptyMap()
    private var pendientes: Int = 0
    private var fallidas: Int = 0

    init {
        empresaId = (sessionRepository.state.value as? SessionState.Active)?.empresa?.id.orEmpty()
        tecnicoId = authRepository.currentUserId().orEmpty()

        viewModelScope.launch {
            // La semana ISO se alinea con la TZ de la empresa (misma base que el
            // servidor): lunes anterior o igual a hoy en esa zona.
            val tz = ZoneId.of(repository.zonaHoraria())
            semanaInicio = LocalDate.now(tz)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            cargarRecuento()
            cargar()
        }

        // Cola de subida: pendientes/fallidas deciden el bloqueo del cuadre.
        viewModelScope.launch {
            combine(
                pendientesDao.observarContadorPendientes(empresaId),
                pendientesDao.observarContadorFallidas(empresaId),
            ) { p, f -> p to f }.collect { (p, f) ->
                pendientes = p
                fallidas = f
                recomputar()
            }
        }

        // Ante cualquier cambio del servidor, recargar el esperado.
        viewModelScope.launch {
            realtimeManager.revision.drop(1).collect { cargar() }
        }
    }

    /** Avanza/retrocede de semana (delta en semanas) y recarga todo. */
    fun onCambiarSemana(delta: Long) {
        semanaInicio = semanaInicio.plusWeeks(delta)
        viewModelScope.launch {
            cargarRecuento()
            cargar()
        }
    }

    /** El técnico teclea una cantidad de una denominación; persiste y recompone. */
    fun onContarChange(denominacion: BigDecimal, cantidad: Long) {
        contado = if (cantidad <= 0L) {
            contado - denominacion
        } else {
            contado + (denominacion to cantidad)
        }
        viewModelScope.launch { persistirRecuento() }
        recomputar()
    }

    /** Encola el drenado de la cola de subida para desbloquear el cuadre. */
    fun onSubirPendientes() {
        if (empresaId.isNotEmpty()) uploadManager.encolar(empresaId)
    }

    private suspend fun cargar() {
        when (val result = repository.cargarSemana(semanaInicio)) {
            is DomainResult.Success -> {
                cuadre = result.value
                recomputar()
            }
            is DomainResult.Failure -> {
                // Sin esperado y sin dato previo: sin conexión. Si ya hay uno
                // cargado, conserva el estado en vez de pisar con un error.
                if (cuadre == null) _state.update { CuadreUiState.SinConexion }
            }
        }
    }

    private suspend fun cargarRecuento() {
        val entity = recuentoDao
            .observar(empresaId, tecnicoId, semanaInicio.toString())
            .first()
        contado = entity?.let { recuentoStore.deserializar(it.recuentoJson) } ?: emptyMap()
    }

    private suspend fun persistirRecuento() {
        recuentoDao.upsert(
            CuadreRecuentoEntity(
                empresaId = empresaId,
                tecnicoId = tecnicoId,
                semanaInicio = semanaInicio.toString(),
                recuentoJson = recuentoStore.serializar(contado),
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun recomputar() {
        _state.update { construirEstado(cuadre, contado, pendientes, fallidas, semanaInicio) }
    }
}
