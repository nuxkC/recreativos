package com.recre.app.feature.deudas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.CreditoLocalDao
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.local.entity.CreditoLocalEntity
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
import kotlinx.coroutines.launch

/** Un local con su saldo de deuda, para el índice de Deudas en gestión. */
data class LocalSaldoItem(
    val localId: String,
    val nombre: String,
    val saldoTotal: BigDecimal,
    val saldoTolva: BigDecimal,
    val saldoPrestamo: BigDecimal,
    val numDeudas: Int,
)

data class DeudasGestorUiState(
    val cargado: Boolean = false,
    val online: Boolean = true,
    val capitalTotal: BigDecimal = BigDecimal.ZERO,
    val locales: List<LocalSaldoItem> = emptyList(),
)

/**
 * Índice de la sección Deudas en gestión (T-219): lista todos los locales con
 * su saldo de tolva/préstamos (offline, desde la cache de `credito_local`), los
 * que más deben primero. Entrar en uno lleva a su ficha de deudas. Incluye
 * locales sin deuda para poder darles un préstamo desde aquí.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeudasGestorViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    localDao: LocalDao,
    creditoLocalDao: CreditoLocalDao,
    connectivityRepository: ConnectivityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DeudasGestorUiState())
    val state: StateFlow<DeudasGestorUiState> = _state.asStateFlow()

    init {
        val empresaIdFlow = sessionRepository.state.map {
            (it as? SessionState.Active)?.empresa?.id
        }
        viewModelScope.launch {
            empresaIdFlow.flatMapLatest { empresaId ->
                if (empresaId == null) {
                    flowOf(DeudasGestorUiState(cargado = true))
                } else {
                    combine(
                        localDao.observarPorEmpresa(empresaId),
                        creditoLocalDao.observarPorEmpresa(empresaId),
                        connectivityRepository.online,
                    ) { locales, creditos, online ->
                        val porLocal = creditos.groupBy { it.localId }
                        val items = locales.map { local ->
                            val deudas = porLocal[local.id].orEmpty()
                            LocalSaldoItem(
                                localId = local.id,
                                nombre = local.nombre,
                                saldoTotal = deudas.sumImporte { it.saldo },
                                saldoTolva = deudas.filter { it.tipo == "tolva" }.sumImporte { it.saldo },
                                saldoPrestamo = deudas.filter { it.tipo == "prestamo" }
                                    .sumImporte { it.saldo },
                                numDeudas = deudas.size,
                            )
                        }.sortedWith(
                            compareByDescending<LocalSaldoItem> { it.saldoTotal }
                                .thenBy { it.nombre.lowercase() },
                        )
                        DeudasGestorUiState(
                            cargado = true,
                            online = online,
                            capitalTotal = items.fold(BigDecimal.ZERO) { acc, i -> acc.add(i.saldoTotal) },
                            locales = items,
                        )
                    }
                }
            }.collect { _state.value = it }
        }
    }

    private inline fun List<CreditoLocalEntity>.sumImporte(
        selector: (CreditoLocalEntity) -> String,
    ): BigDecimal = fold(BigDecimal.ZERO) { acc, c -> acc.add(BigDecimal(selector(c))) }
}
