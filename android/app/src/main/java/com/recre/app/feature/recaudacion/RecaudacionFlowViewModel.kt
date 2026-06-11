package com.recre.app.feature.recaudacion

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.recre.app.core.calculo.CalcularInput
import com.recre.app.core.calculo.Cifras
import com.recre.app.core.calculo.DenominacionItem
import com.recre.app.core.calculo.FirmaRenderer
import com.recre.app.core.calculo.calcularRecaudacion
import com.recre.app.core.calculo.semanasIsoEntre
import com.recre.app.core.calculo.sumarDesglose
import com.recre.app.core.data.local.dao.EmpresaParamsDao
import com.recre.app.core.data.local.entity.EmpresaParamsEntity
import com.recre.app.core.data.repository.AuthRepository
import com.recre.app.core.data.repository.EncolarRecaudacionInput
import com.recre.app.core.data.repository.InventoryRepository
import com.recre.app.core.data.repository.MaquinaConInstalacion
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.locks.LockManager
import com.recre.app.core.locks.LockState
import com.recre.app.core.printer.PrintResult
import com.recre.app.core.printer.PrinterRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.sync.RecaudacionUploadManager
import com.recre.app.core.sync.SyncManager
import com.recre.app.core.util.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel compartido por las pantallas del flujo de recaudación.
 *
 * Scoped al NavBackStackEntry del sub-NavGraph `recaudacion/{instalacionId}`,
 * de forma que persiste entre navegaciones (`contadores → denominacionesTotal
 * → denominacionesLocal → confirmación`) y muere cuando el flujo se cierra.
 *
 * Carga la máquina y los parámetros de la empresa observando Room (cache
 * que llenó T-51). Calcula las cifras en vivo cada vez que el técnico
 * cambia los contadores, usando el SSOT en [calcularRecaudacion].
 *
 * En este PR (T-60):
 * - Si la ruta trae `cadenaLocalId`, carga la lista ordenada de
 *   instalaciones activas del local y expone `state.cadena` con
 *   posición/total y siguiente id. Las pantallas saben si están en
 *   modo cadena y navegan al siguiente al cerrar el flujo.
 *
 * Reglas previas (T-57/T-58/T-59) intactas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecaudacionFlowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inventoryRepository: InventoryRepository,
    empresaParamsDao: EmpresaParamsDao,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val recaudacionRepository: RecaudacionRepository,
    private val uploadManager: RecaudacionUploadManager,
    private val lockManager: LockManager,
    private val printerRepository: PrinterRepository,
    syncManager: SyncManager,
) : ViewModel() {

    private val instalacionId: String = checkNotNull(savedStateHandle[ARG_INSTALACION_ID]) {
        "Falta argumento '$ARG_INSTALACION_ID' al abrir el flujo de recaudación"
    }

    /** Modo cadena (T-60): null si entramos desde MaquinaCard, no-null si desde "Recaudar todas". */
    private val cadenaLocalId: String? = savedStateHandle[ARG_CADENA_LOCAL_ID]

    private val _uiState = MutableStateFlow(RecaudacionFlowState())
    val state: StateFlow<RecaudacionFlowState> = _uiState.asStateFlow()

    init {
        val empresaIdFlow = kotlinx.coroutines.flow.flow {
            sessionRepository.state.collect { state ->
                emit((state as? SessionState.Active)?.empresa?.id)
            }
        }
        val empresaParamsFlow = empresaIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(null) else empresaParamsDao.observe(id)
        }
        val syncStaleFlow = empresaIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(false) else syncManager.observarSyncStale(id)
        }

        // Carga reactiva: máquina + empresa + stale flag.
        viewModelScope.launch {
            combine(
                inventoryRepository.observarMaquinaPorInstalacion(instalacionId),
                empresaParamsFlow,
                syncStaleFlow,
            ) { maquina, empresa, stale -> Triple(maquina, empresa, stale) }
                .collect { (maquina, empresa, stale) ->
                    _uiState.update { current ->
                        current.copy(
                            cargando = false,
                            maquina = maquina,
                            empresa = empresa,
                            syncStale = stale,
                            errorCarga = if (maquina == null) "instalacion_no_encontrada" else null,
                            cifras = if (maquina != null && empresa != null) {
                                calcularSiInputsValidos(
                                    current.contadorEntradasInput,
                                    current.contadorSalidasInput,
                                    maquina,
                                    empresa,
                                )
                            } else current.cifras,
                        )
                    }
                }
        }

        // Cadena: cargar el orden de instalaciones del local una sola vez.
        if (cadenaLocalId != null) {
            viewModelScope.launch {
                val detalle = inventoryRepository.observarLocalDetalle(cadenaLocalId).first()
                val orden = detalle?.maquinas?.map { it.instalacionId }.orEmpty()
                _uiState.update {
                    it.copy(
                        cadena = CadenaState(
                            localId = cadenaLocalId,
                            instalacionesOrdenadas = orden,
                            instalacionActualId = instalacionId,
                        ),
                    )
                }
            }
        }

        // Adquirir lock optimista en cuanto haya red.
        intentarAdquirirLock(forzar = false)
    }

    // -------------------------------------------------------------------------
    // T-58 — Lock optimista
    // -------------------------------------------------------------------------

    private fun intentarAdquirirLock(forzar: Boolean) {
        viewModelScope.launch {
            val lock = lockManager.adquirir(
                instalacionId = instalacionId,
                dispositivoId = Build.MODEL,
                forzar = forzar,
            )
            _uiState.update { it.copy(lockState = lock) }
        }
    }

    /** Llamada por el diálogo "continuar de todos modos". */
    fun forzarLock() {
        intentarAdquirirLock(forzar = true)
    }

    /**
     * Libera el lock al cancelar el flujo (botón atrás en cualquier
     * pantalla). Best-effort: no bloqueamos la navegación si falla. El
     * TTL de 30 min del backend (T-24) cubre cualquier despiste.
     *
     * La liberación corre en el scope del [LockManager], no en
     * `viewModelScope`: este último se cancela al destruir el ViewModel
     * durante la navegación atrás, lo que abortaba la petición HTTP.
     */
    fun liberarLockAlSalir() {
        if (_uiState.value.lockState !is LockState.Adquirido) return
        lockManager.liberar(instalacionId)
    }

    /**
     * Red de seguridad: el ViewModel está scoped al sub-graph y muere justo
     * cuando se sale del flujo (back del sistema, gesto, cierre de la app),
     * rutas que NO pasan por [liberarLockAlSalir]. Si se abandona sin guardar
     * con el lock en mano, lo soltamos aquí. La liberación es fire-and-forget
     * en el scope del [LockManager], imprescindible porque `viewModelScope`
     * ya está cancelado cuando `onCleared` se ejecuta.
     */
    override fun onCleared() {
        super.onCleared()
        val estado = _uiState.value
        if (!estado.guardado && estado.lockState is LockState.Adquirido) {
            lockManager.liberar(instalacionId)
        }
    }

    // -------------------------------------------------------------------------
    // Paso 1 — contadores
    // -------------------------------------------------------------------------

    fun onContadorEntradasChange(value: String) {
        val sanitized = value.filter { it.isDigit() }.take(MAX_CONTADOR_DIGITS)
        _uiState.update { current ->
            val cifras = calcularSiInputsValidos(
                sanitized,
                current.contadorSalidasInput,
                current.maquina,
                current.empresa,
            )
            current.copy(contadorEntradasInput = sanitized, cifras = cifras)
        }
    }

    fun onContadorSalidasChange(value: String) {
        val sanitized = value.filter { it.isDigit() }.take(MAX_CONTADOR_DIGITS)
        _uiState.update { current ->
            val cifras = calcularSiInputsValidos(
                current.contadorEntradasInput,
                sanitized,
                current.maquina,
                current.empresa,
            )
            current.copy(contadorSalidasInput = sanitized, cifras = cifras)
        }
    }

    private fun calcularSiInputsValidos(
        entradasInput: String,
        salidasInput: String,
        maquina: MaquinaConInstalacion?,
        empresa: EmpresaParamsEntity?,
    ): Cifras? {
        if (maquina == null || empresa == null) return null
        val entradas = entradasInput.toLongOrNull() ?: return null
        val salidas = salidasInput.toLongOrNull() ?: return null
        if (entradas < maquina.baselineEntradas || salidas < maquina.baselineSalidas) {
            return null
        }
        val zona = runCatching { ZoneId.of(empresa.zonaHoraria) }
            .getOrElse { ZoneId.of("Europe/Madrid") }
        val semanas = semanasIsoEntre(maquina.baselineFecha, Instant.now(), zona)
        return calcularRecaudacion(
            CalcularInput(
                baselineEntradas = maquina.baselineEntradas,
                baselineSalidas = maquina.baselineSalidas,
                contadorEntradasActual = entradas,
                contadorSalidasActual = salidas,
                valorCredito = BigDecimal(maquina.valorCredito),
                tasaSemanal = BigDecimal(maquina.tasaSemanal),
                porcentajeLocal = BigDecimal(maquina.porcentajeLocal),
                semanas = semanas,
            ),
        )
    }

    // -------------------------------------------------------------------------
    // T-100 — OCR de contadores en vivo (asistencia, no fuente de verdad)
    // -------------------------------------------------------------------------

    /**
     * Aplica la lectura que el técnico ha confirmado en el escáner en vivo
     * (`EscanerContadoresOverlay`). El escáner ya identificó ambos contadores
     * con [com.recre.app.core.ocr.ContadorOcrParser.parseAmbos]; aquí solo
     * volcamos los valores a los inputs, pasando por el mismo saneado y
     * recálculo que la entrada manual para que el técnico pueda corregirlos.
     *
     * El OCR no persiste ninguna imagen: solo se analizan los fotogramas de la
     * cámara al vuelo (ver nota de alcance de T-100).
     */
    fun aplicarLecturaOcr(entradas: Long, salidas: Long) {
        Timber.i("OCR contadores en vivo aplicado por el técnico")
        onContadorEntradasChange(entradas.toString())
        onContadorSalidasChange(salidas.toString())
    }

    // -------------------------------------------------------------------------
    // Paso 2 — denominaciones
    // -------------------------------------------------------------------------

    fun onDenominacionTotalChange(denominacionKey: String, cantidad: Int) {
        _uiState.update { current ->
            current.copy(
                denominacionesTotal = current.denominacionesTotal
                    .toMutableMap()
                    .apply { this[denominacionKey] = cantidad.coerceAtLeast(0) }
                    .toMap(),
            )
        }
    }

    fun onDenominacionLocalChange(denominacionKey: String, cantidad: Int) {
        _uiState.update { current ->
            current.copy(
                denominacionesLocal = current.denominacionesLocal
                    .toMutableMap()
                    .apply { this[denominacionKey] = cantidad.coerceAtLeast(0) }
                    .toMap(),
            )
        }
    }

    /** Total acumulado del desglose pasado (con escala 2). */
    fun sumarDesgloseDe(map: Map<String, Int>): BigDecimal {
        val items = map.mapNotNull { (key, cantidad) ->
            if (cantidad <= 0) null else DenominacionItem(BigDecimal(key), cantidad)
        }
        return sumarDesglose(items)
    }

    // -------------------------------------------------------------------------
    // Paso 3 — firma
    // -------------------------------------------------------------------------

    fun onFirmaStrokeAppend(stroke: List<Offset>) {
        if (stroke.isEmpty()) return
        _uiState.update { current ->
            current.copy(firmaStrokes = current.firmaStrokes + listOf(stroke))
        }
    }

    fun onFirmaLimpiar() {
        _uiState.update { it.copy(firmaStrokes = emptyList()) }
    }

    // -------------------------------------------------------------------------
    // Paso 4 — guardar (persistencia real T-57)
    // -------------------------------------------------------------------------

    /**
     * Encola la recaudación en Room, dispara el upload Worker y, si en
     * pocos segundos termina con éxito, marca como subida online; en
     * caso contrario, queda como pendiente (el técnico ve "se subirá
     * cuando haya red"). En ambos casos cerramos el flujo.
     */
    fun onGuardar() {
        val state = _uiState.value
        val cifras = state.cifras ?: return
        val maquina = state.maquina ?: return
        val empresaId = (sessionRepository.state.value as? SessionState.Active)?.empresa?.id
            ?: return
        if (!cifras.procede || state.syncStale || state.firmaStrokes.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true) }

            val tecnicoId = authRepository.currentUserId() ?: run {
                _uiState.update { it.copy(guardando = false, errorCarga = "auth") }
                return@launch
            }

            val firmaPng = FirmaRenderer.renderToPng(state.firmaStrokes)
            val desgloseTotal = mapToItems(state.denominacionesTotal)
            val desgloseLocal = mapToItems(state.denominacionesLocal)
            val fechaRecaudacion = Instant.now()
            val contadorEntradas = state.contadorEntradasInput.toLong()
            val contadorSalidas = state.contadorSalidasInput.toLong()

            val encolarResult = recaudacionRepository.encolar(
                EncolarRecaudacionInput(
                    empresaId = empresaId,
                    instalacionId = instalacionId,
                    tecnicoId = tecnicoId,
                    fecha = fechaRecaudacion,
                    contadorEntradasActual = contadorEntradas,
                    contadorSalidasActual = contadorSalidas,
                    baselineEntradas = maquina.baselineEntradas,
                    baselineSalidas = maquina.baselineSalidas,
                    baselineOrigen = maquina.baselineOrigen,
                    baselineReferenciaId = maquina.baselineReferenciaId,
                    cifras = cifras,
                    desgloseTotal = desgloseTotal,
                    desgloseLocal = desgloseLocal,
                    firmaPng = firmaPng,
                    observaciones = null,
                    dispositivoId = Build.MODEL,
                ),
            )

            if (encolarResult is DomainResult.Failure) {
                Timber.e("No se pudo encolar la recaudacion: %s", encolarResult.error.message)
                _uiState.update { it.copy(guardando = false, errorCarga = "guardar_fallo") }
                return@launch
            }

            // Encola el upload y espera unos segundos por si hay red.
            uploadManager.encolar(empresaId)
            val finalState = uploadManager.esperarFinalizacion(empresaId)
            val subidoOnline = finalState == WorkInfo.State.SUCCEEDED

            // Liberamos el lock siempre — best-effort (fire-and-forget).
            lockManager.liberar(instalacionId)

            // Recaudación persistida; ya se puede declarar guardado=true.
            // La impresión es informativa y NO retiene la persistencia
            // (T-62): si falla, el técnico puede reintentar desde la
            // pantalla de confirmación o continuar sin ticket impreso.
            _uiState.update {
                it.copy(
                    guardando = false,
                    guardado = true,
                    subidoOnline = subidoOnline,
                )
            }

            // Snapshot de los datos necesarios para el ticket impreso.
            // Lo guardamos aparte porque la impresión es una operación
            // I/O larga y no queremos depender de mutaciones del state
            // (firma limpiada, etc.) durante la espera.
            ultimoTicket = SnapshotTicket(
                empresa = state.empresa,
                maquina = maquina,
                tecnicoEmail = authRepository.currentUserEmail(),
                fecha = fechaRecaudacion,
                contadorEntradasActual = contadorEntradas,
                contadorSalidasActual = contadorSalidas,
                cifras = cifras,
                desgloseTotal = desgloseTotal,
                desgloseLocal = desgloseLocal,
                firmaPng = firmaPng,
            )
            imprimirTicket()
        }
    }

    /**
     * Lanza (o re-lanza) la impresión del ticket. Solo tiene efecto
     * cuando hay un snapshot guardado por [onGuardar]. La pantalla la
     * llama desde "Reintentar impresión".
     */
    fun imprimirTicket() {
        val snap = ultimoTicket ?: return
        if (_uiState.value.imprimiendo) return
        viewModelScope.launch {
            _uiState.update { it.copy(imprimiendo = true, printResult = null) }
            val result = printerRepository.imprimirTicketRecaudacion(
                empresa = snap.empresa,
                localNombre = snap.maquina.localNombre,
                localDireccion = snap.maquina.localDireccion,
                maquina = snap.maquina,
                tecnicoEmail = snap.tecnicoEmail,
                fecha = snap.fecha,
                contadorEntradasActual = snap.contadorEntradasActual,
                contadorSalidasActual = snap.contadorSalidasActual,
                cifras = snap.cifras,
                desgloseTotal = snap.desgloseTotal,
                desgloseLocal = snap.desgloseLocal,
                firmaPng = snap.firmaPng,
            )
            if (result is PrintResult.Failure) {
                Timber.w("Impresion del ticket fallo: %s", result.error)
            } else {
                Timber.i("Ticket de recaudacion enviado a la impresora")
            }
            _uiState.update { it.copy(imprimiendo = false, printResult = result) }
        }
    }

    /**
     * Snapshot inmutable del ticket impreso. Se guarda al cerrar la
     * recaudación para poder reintentar la impresión sin recalcular ni
     * leer el state mutado por la UI.
     */
    private data class SnapshotTicket(
        val empresa: EmpresaParamsEntity?,
        val maquina: MaquinaConInstalacion,
        val tecnicoEmail: String?,
        val fecha: Instant,
        val contadorEntradasActual: Long,
        val contadorSalidasActual: Long,
        val cifras: Cifras,
        val desgloseTotal: List<DenominacionItem>,
        val desgloseLocal: List<DenominacionItem>,
        val firmaPng: ByteArray,
    )

    private var ultimoTicket: SnapshotTicket? = null

    private fun mapToItems(map: Map<String, Int>): List<DenominacionItem> =
        map.mapNotNull { (key, cantidad) ->
            if (cantidad <= 0) null else DenominacionItem(BigDecimal(key), cantidad)
        }

    companion object {
        const val ARG_INSTALACION_ID = "instalacionId"
        const val ARG_CADENA_LOCAL_ID = "cadenaLocalId"
        private const val MAX_CONTADOR_DIGITS = 12
    }
}
