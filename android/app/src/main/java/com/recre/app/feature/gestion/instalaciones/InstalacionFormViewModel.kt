package com.recre.app.feature.gestion.instalaciones

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recre.app.core.data.local.dao.InstalacionDao
import com.recre.app.core.data.local.dao.LicenciaDao
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.local.dao.MaquinaDao
import com.recre.app.core.data.repository.GestionResult
import com.recre.app.core.data.repository.InstalacionInputData
import com.recre.app.core.data.repository.InstalacionUpdateData
import com.recre.app.core.data.repository.InstalacionesGestorRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.ConnectivityRepository
import com.recre.app.feature.gestion.FkOption
import com.recre.app.feature.gestion.esFechaIsoValida
import com.recre.app.feature.gestion.normalizarDecimal
import com.recre.app.feature.gestion.normalizarOpcional
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstalacionFormUiState(
    val cargando: Boolean = true,
    val esEdicion: Boolean = false,
    val instalacionId: String? = null,
    // FKs (inmutables en edición)
    val maquinaId: String? = null,
    val licenciaId: String? = null,
    val localId: String? = null,
    // Cabecera de la fila editada (read-only)
    val cabecera: String = "",
    // Listas para los selects (solo en alta)
    val maquinasDisponibles: List<FkOption> = emptyList(),
    val licenciasDisponibles: List<FkOption> = emptyList(),
    val locales: List<FkOption> = emptyList(),
    // Campos editables
    val fechaInicio: String = LocalDate.now().toString(),
    val tasaSemanal: String = "",
    val porcentajeLocal: String = "",
    // Tolva inicial del local en la máquina (T-215). Solo se teclea en el alta;
    // por defecto 0 (la mayoría de instalaciones no arrancan con tolva).
    val tolva: String = "0",
    val notas: String = "",
    // Validación + estado de envío
    val errores: Map<String, String> = emptyMap(),
    val errorCode: String? = null,
    val guardando: Boolean = false,
    val guardado: Boolean = false,
    val cerrando: Boolean = false,
    val cerrada: Boolean = false,
    val mostrarCerrarDialog: Boolean = false,
    val cerrarFechaFin: String = LocalDate.now().toString(),
    val cerrarNotas: String = "",
    val online: Boolean = true,
)

/**
 * Form de Instalación (T-69).
 *
 * - **Alta**: usuario escoge maquina + licencia + local de listas que ya
 *   filtran las que están ocupadas por instalaciones activas. Si no hay
 *   candidatas, el form muestra un mensaje claro.
 * - **Edición**: FKs deshabilitadas, solo se editan
 *   `fecha_inicio`/`tasa_semanal`/`porcentaje_local`/`notas`. La base de
 *   contadores la deriva el servidor (no se teclea). El **cierre** se hace
 *   con un dialog que llama a la Edge Function `cerrar-instalacion`.
 *
 * No se persisten cambios offline: el [com.recre.app.core.sync.SyncManager]
 * forzado tras cada éxito refresca la cache. PR-J (T-70) añadirá el
 * banner de "sin conexión" cuando aplique.
 */
@HiltViewModel
class InstalacionFormViewModel @Inject constructor(
    private val instalacionDao: InstalacionDao,
    private val maquinaDao: MaquinaDao,
    private val licenciaDao: LicenciaDao,
    private val localDao: LocalDao,
    private val sessionRepository: SessionRepository,
    private val repository: InstalacionesGestorRepository,
    private val connectivityRepository: ConnectivityRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val instalacionId: String? = savedStateHandle[ARG_INSTALACION_ID]

    private val _state = MutableStateFlow(
        InstalacionFormUiState(
            esEdicion = instalacionId != null,
            instalacionId = instalacionId,
            cargando = true,
        ),
    )
    val state: StateFlow<InstalacionFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            cargarInicial()
        }
        viewModelScope.launch {
            connectivityRepository.online.collect { online ->
                _state.update { it.copy(online = online) }
            }
        }
    }

    private suspend fun cargarInicial() {
        val empresaId = empresaActivaId()
        if (empresaId == null) {
            _state.update { it.copy(cargando = false, errorCode = "auth") }
            return
        }

        if (instalacionId == null) {
            // Alta: cargar listas filtradas
            val instalacionesActivas = instalacionDao.observarActivasPorEmpresa(empresaId).first()
            val maquinas = maquinaDao.observarPorEmpresa(empresaId).first()
            val licencias = licenciaDao.observarPorEmpresa(empresaId).first()
            val locales = localDao.observarPorEmpresa(empresaId).first()

            val maquinasOcupadas = instalacionesActivas.map { it.maquinaId }.toSet()
            val licenciasOcupadas = instalacionesActivas.map { it.licenciaId }.toSet()

            _state.update {
                it.copy(
                    cargando = false,
                    maquinasDisponibles = maquinas
                        .filter { m -> m.estado != "baja" && m.id !in maquinasOcupadas }
                        .map { m ->
                            FkOption(
                                id = m.id,
                                label = listOfNotNull(m.numeroSerie, m.modelo)
                                    .joinToString(" — "),
                            )
                        },
                    licenciasDisponibles = licencias
                        .filter { l -> l.estado != "baja" && l.id !in licenciasOcupadas }
                        .map { FkOption(id = it.id, label = it.numero) },
                    locales = locales.map { FkOption(id = it.id, label = it.nombre) },
                )
            }
        } else {
            // Edición: cargar fila existente
            val ent = instalacionDao.obtener(instalacionId)
            if (ent == null) {
                _state.update { it.copy(cargando = false, errorCode = "not_found") }
                return
            }
            val m = maquinaDao.obtener(ent.maquinaId)
            val lic = licenciaDao.obtener(ent.licenciaId)
            val loc = localDao.obtener(ent.localId)
            val cabecera = listOfNotNull(
                m?.numeroSerie?.let { "Máquina $it" },
                lic?.numero?.let { "Licencia $it" },
                loc?.nombre?.let { "Local $it" },
            ).joinToString(" · ")
            _state.update {
                it.copy(
                    cargando = false,
                    cabecera = cabecera,
                    maquinaId = ent.maquinaId,
                    licenciaId = ent.licenciaId,
                    localId = ent.localId,
                    fechaInicio = ent.fechaInicio,
                    tasaSemanal = ent.tasaSemanal,
                    porcentajeLocal = ent.porcentajeLocal,
                )
            }
        }
    }

    // Setters (solo reflejan en estado).
    fun onMaquinaChange(id: String?) = _state.update { it.copy(maquinaId = id) }
    fun onLicenciaChange(id: String?) = _state.update { it.copy(licenciaId = id) }
    fun onLocalChange(id: String?) = _state.update { it.copy(localId = id) }
    fun onFechaInicioChange(v: String) = _state.update { it.copy(fechaInicio = v) }
    fun onTasaChange(v: String) = _state.update { it.copy(tasaSemanal = v) }
    fun onPorcentajeChange(v: String) = _state.update { it.copy(porcentajeLocal = v) }
    fun onTolvaChange(v: String) = _state.update { it.copy(tolva = v) }
    fun onNotasChange(v: String) = _state.update { it.copy(notas = v) }
    fun consumirError() = _state.update { it.copy(errorCode = null) }

    // Cerrar dialog
    fun pedirCerrar() = _state.update { it.copy(mostrarCerrarDialog = true) }
    fun cancelarCerrar() = _state.update { it.copy(mostrarCerrarDialog = false) }
    fun onCerrarFechaFinChange(v: String) = _state.update { it.copy(cerrarFechaFin = v) }
    fun onCerrarNotasChange(v: String) = _state.update { it.copy(cerrarNotas = v) }

    fun guardar() {
        val s = _state.value
        if (!s.online) {
            _state.update { it.copy(errorCode = "network") }
            return
        }
        val errores = mutableMapOf<String, String>()

        if (!s.esEdicion) {
            if (s.maquinaId == null) errores["maquina"] = "requerido"
            if (s.licenciaId == null) errores["licencia"] = "requerido"
            if (s.localId == null) errores["local"] = "requerido"
        }
        if (!esFechaIsoValida(s.fechaInicio)) errores["fechaInicio"] = "fecha_invalida"
        val tasa = normalizarDecimal(s.tasaSemanal, BigDecimal.ZERO, MAX_TASA, 2)
        if (tasa == null) errores["tasaSemanal"] = "tasa_invalida"
        val pct = normalizarDecimal(s.porcentajeLocal, BigDecimal.ZERO, MAX_PCT, 2)
        if (pct == null) errores["porcentajeLocal"] = "porcentaje_invalido"
        // La tolva solo se teclea en el alta; vacío = 0.
        val tolva = if (s.esEdicion) {
            BigDecimal.ZERO
        } else {
            normalizarDecimal(s.tolva.ifBlank { "0" }, BigDecimal.ZERO, MAX_TOLVA, 2)
                ?: run { errores["tolva"] = "tolva_invalida"; null }
        }

        if (errores.isNotEmpty()) {
            _state.update { it.copy(errores = errores) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(guardando = true, errores = emptyMap(), errorCode = null) }
            val result = if (s.esEdicion && s.instalacionId != null) {
                repository.actualizar(
                    s.instalacionId,
                    InstalacionUpdateData(
                        fechaInicio = s.fechaInicio,
                        tasaSemanal = tasa!!.toPlainString(),
                        porcentajeLocal = pct!!.toPlainString(),
                        notas = s.notas.normalizarOpcional(),
                    ),
                )
            } else {
                repository.crear(
                    InstalacionInputData(
                        maquinaId = s.maquinaId!!,
                        licenciaId = s.licenciaId!!,
                        localId = s.localId!!,
                        fechaInicio = s.fechaInicio,
                        tasaSemanal = tasa!!.toPlainString(),
                        porcentajeLocal = pct!!.toPlainString(),
                        notas = s.notas.normalizarOpcional(),
                        tolva = tolva!!.toPlainString(),
                    ),
                )
            }
            _state.update {
                when (result) {
                    is GestionResult.Success<*> -> it.copy(guardando = false, guardado = true)
                    is GestionResult.Failure -> it.copy(guardando = false, errorCode = result.code)
                }
            }
        }
    }

    fun confirmarCerrar() {
        val s = _state.value
        val id = s.instalacionId ?: return
        if (!s.online) {
            _state.update { it.copy(errorCode = "network", mostrarCerrarDialog = false) }
            return
        }
        if (!esFechaIsoValida(s.cerrarFechaFin)) {
            _state.update { it.copy(errorCode = "cerrar_fecha_fin_invalida") }
            return
        }
        if (LocalDate.parse(s.cerrarFechaFin).isBefore(LocalDate.parse(s.fechaInicio))) {
            _state.update { it.copy(errorCode = "cerrar_fecha_fin_invalida") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(cerrando = true, mostrarCerrarDialog = false) }
            val r = repository.cerrar(id, s.cerrarFechaFin, s.cerrarNotas.normalizarOpcional())
            _state.update {
                when (r) {
                    is GestionResult.Success<*> -> it.copy(cerrando = false, cerrada = true)
                    is GestionResult.Failure -> it.copy(cerrando = false, errorCode = r.code)
                }
            }
        }
    }

    private fun empresaActivaId(): String? =
        (sessionRepository.state.value as? SessionState.Active)?.empresa?.id

    companion object {
        const val ARG_INSTALACION_ID = "instalacionGestorId"
        private val MAX_TASA = BigDecimal("999999.99")
        private val MAX_PCT = BigDecimal("100.00")
        private val MAX_TOLVA = BigDecimal("99999999.99")
    }
}
