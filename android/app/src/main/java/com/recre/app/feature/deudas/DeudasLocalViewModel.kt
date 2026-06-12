package com.recre.app.feature.deudas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.auth.ROLES_ADMIN
import com.recre.app.core.auth.ROLES_GESTION
import com.recre.app.core.auth.rolCumple
import com.recre.app.core.data.local.dao.EmpresaParamsDao
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.local.entity.CreditoLocalEntity
import com.recre.app.core.data.repository.DeudasRepository
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.data.repository.RecuperacionLedger
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.ConnectivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tipo de diálogo abierto en la ficha de deudas. */
enum class DeudaDialog { NINGUNO, NUEVO_PRESTAMO, PAGO, CONDONAR, PORCENTAJE }

data class DeudasLocalUiState(
    val cargado: Boolean = false,
    val online: Boolean = true,
    val esGestor: Boolean = false,
    val esAdmin: Boolean = false,
    val nombreLocal: String = "",
    // Saldo agregado (derivado de las deudas abiertas cacheadas).
    val saldoTotal: BigDecimal = BigDecimal.ZERO,
    val saldoTolva: BigDecimal = BigDecimal.ZERO,
    val saldoPrestamo: BigDecimal = BigDecimal.ZERO,
    val creditos: List<CreditoLocalEntity> = emptyList(),
    // % de recuperación.
    val porcentajeEmpresa: Int = 0,
    val porcentajeLocal: Int? = null,
    // Libro mayor (en línea).
    val ledger: List<RecuperacionLedger> = emptyList(),
    val cargandoLedger: Boolean = false,
    // Operación en curso / feedback.
    val operando: Boolean = false,
    val errorCode: String? = null,
    val mensajeOkRes: Int? = null,
    // Diálogos.
    val dialog: DeudaDialog = DeudaDialog.NINGUNO,
    /** Deuda objetivo del diálogo de pago/condonación. */
    val creditoSeleccionado: CreditoLocalEntity? = null,
) {
    /** % efectivo: override del local o, si no, el de la empresa. */
    val porcentajeEfectivo: Int get() = porcentajeLocal ?: porcentajeEmpresa
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeudasLocalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DeudasRepository,
    empresaParamsDao: EmpresaParamsDao,
    localDao: LocalDao,
    private val sessionRepository: SessionRepository,
    connectivityRepository: ConnectivityRepository,
) : ViewModel() {

    private val localId: String = checkNotNull(savedStateHandle[ARG_LOCAL_ID]) {
        "Falta argumento '$ARG_LOCAL_ID' al abrir DeudasLocalScreen"
    }

    private val _state = MutableStateFlow(DeudasLocalUiState())
    val state: StateFlow<DeudasLocalUiState> = _state.asStateFlow()

    private val empresaIdFlow = sessionRepository.state.map {
        (it as? SessionState.Active)?.empresa?.id
    }

    init {
        val empresaParamsFlow = empresaIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(null) else empresaParamsDao.observe(id)
        }

        viewModelScope.launch {
            combine(
                repository.observarCreditos(localId),
                empresaParamsFlow,
                localDao.observe(localId),
                sessionRepository.state,
                connectivityRepository.online,
            ) { creditos, empresa, local, session, online ->
                val rol = (session as? SessionState.Active)?.membresia?.rol
                Datos(
                    creditos = creditos,
                    porcentajeEmpresa = empresa?.porcentajeRecuperacion ?: 0,
                    porcentajeLocal = local?.porcentajeRecuperacion,
                    nombreLocal = local?.nombre.orEmpty(),
                    esGestor = rolCumple(rol, ROLES_GESTION),
                    esAdmin = rolCumple(rol, ROLES_ADMIN),
                    online = online,
                )
            }.collect { d ->
                _state.update { current ->
                    current.copy(
                        cargado = true,
                        creditos = d.creditos,
                        saldoTotal = d.creditos.sumImporte { it.saldo },
                        saldoTolva = d.creditos.filter { it.tipo == "tolva" }.sumImporte { it.saldo },
                        saldoPrestamo = d.creditos.filter { it.tipo == "prestamo" }
                            .sumImporte { it.saldo },
                        porcentajeEmpresa = d.porcentajeEmpresa,
                        porcentajeLocal = d.porcentajeLocal,
                        nombreLocal = d.nombreLocal,
                        esGestor = d.esGestor,
                        esAdmin = d.esAdmin,
                        online = d.online,
                    )
                }
            }
        }

        refrescarLedger()
    }

    fun refrescarLedger() {
        viewModelScope.launch {
            _state.update { it.copy(cargandoLedger = true) }
            when (val r = repository.obtenerLedger(localId)) {
                is GestionResult.Success -> _state.update {
                    it.copy(cargandoLedger = false, ledger = r.value)
                }
                is GestionResult.Failure -> _state.update {
                    // El histórico es secundario: si no hay red, simplemente no
                    // se muestra (no es un error que bloquee la ficha).
                    it.copy(cargandoLedger = false)
                }
            }
        }
    }

    // -- Diálogos ------------------------------------------------------------

    fun abrirNuevoPrestamo() = _state.update { it.copy(dialog = DeudaDialog.NUEVO_PRESTAMO) }
    fun abrirPago(credito: CreditoLocalEntity) =
        _state.update { it.copy(dialog = DeudaDialog.PAGO, creditoSeleccionado = credito) }
    fun abrirCondonar(credito: CreditoLocalEntity) =
        _state.update { it.copy(dialog = DeudaDialog.CONDONAR, creditoSeleccionado = credito) }
    fun abrirPorcentaje() = _state.update { it.copy(dialog = DeudaDialog.PORCENTAJE) }
    fun cerrarDialog() =
        _state.update { it.copy(dialog = DeudaDialog.NINGUNO, creditoSeleccionado = null) }

    fun consumirError() = _state.update { it.copy(errorCode = null) }
    fun consumirMensaje() = _state.update { it.copy(mensajeOkRes = null) }

    // -- Acciones ------------------------------------------------------------

    fun crearPrestamo(principal: String, notas: String?) = ejecutar(
        okRes = com.recre.app.R.string.deudas_prestamo_ok,
    ) { repository.crearPrestamo(localId, principal, notas) }

    fun registrarPago(creditoId: String, importe: String, notas: String?) = ejecutar(
        okRes = com.recre.app.R.string.deudas_pago_ok,
    ) { repository.registrarPago(creditoId, importe, notas) }

    fun condonar(creditoId: String, notas: String?) = ejecutar(
        okRes = com.recre.app.R.string.deudas_condonar_ok,
    ) { repository.condonar(creditoId, notas) }

    fun setPorcentaje(porcentaje: Int?) = ejecutar(
        okRes = com.recre.app.R.string.deudas_porcentaje_ok,
    ) { repository.setPorcentaje(localId, porcentaje) }

    private fun ejecutar(okRes: Int, accion: suspend () -> GestionResult<Unit>) {
        if (!_state.value.online) {
            _state.update { it.copy(errorCode = "network", dialog = DeudaDialog.NINGUNO) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(operando = true, dialog = DeudaDialog.NINGUNO) }
            when (val r = accion()) {
                is GestionResult.Success -> {
                    _state.update {
                        it.copy(operando = false, mensajeOkRes = okRes, creditoSeleccionado = null)
                    }
                    refrescarLedger()
                }
                is GestionResult.Failure -> _state.update {
                    it.copy(operando = false, errorCode = r.code, creditoSeleccionado = null)
                }
            }
        }
    }

    private inline fun List<CreditoLocalEntity>.sumImporte(
        selector: (CreditoLocalEntity) -> String,
    ): BigDecimal = fold(BigDecimal.ZERO) { acc, c -> acc.add(BigDecimal(selector(c))) }

    private data class Datos(
        val creditos: List<CreditoLocalEntity>,
        val porcentajeEmpresa: Int,
        val porcentajeLocal: Int?,
        val nombreLocal: String,
        val esGestor: Boolean,
        val esAdmin: Boolean,
        val online: Boolean,
    )

    companion object {
        const val ARG_LOCAL_ID = "localId"
    }
}
