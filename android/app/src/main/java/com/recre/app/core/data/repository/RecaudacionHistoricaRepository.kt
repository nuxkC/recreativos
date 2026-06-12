package com.recre.app.core.data.repository

import com.recre.app.core.calculo.Cifras
import com.recre.app.core.calculo.DenominacionItem
import com.recre.app.core.data.local.dao.EmpresaParamsDao
import com.recre.app.core.data.remote.RecaudacionHistoricaRemoteDataSource
import com.recre.app.core.data.remote.dto.DenominacionItemDto
import com.recre.app.core.data.remote.dto.RecaudacionHistoricaRow
import com.recre.app.core.printer.PrintResult
import com.recre.app.core.printer.PrinterRepository
import com.recre.app.core.session.SessionRepository
import com.recre.app.core.session.SessionState
import com.recre.app.core.util.DomainError
import com.recre.app.core.util.DomainResult
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Repositorio de "Mis recaudaciones" (T-63).
 *
 * Conecta el remote data source ([RecaudacionHistoricaRemoteDataSource]),
 * la sesión activa y la impresora ([PrinterRepository], T-62) para
 * exponer al ViewModel una API ergonómica:
 *
 *  - [listarMias]: top 200 recaudaciones del técnico autenticado,
 *    ordenadas por fecha desc, con instalación/maquina/local/licencia
 *    embebidos.
 *  - [obtenerDetalle]: una sola fila por id (busca en la lista ya
 *    cargada para evitar otra round-trip — el detalle se abre desde
 *    la lista, así que está cacheada).
 *  - [reimprimirPdf]: pide a la Edge Function una signed URL del PDF
 *    archivado para abrirlo en el navegador.
 *  - [reimprimirBluetooth]: reconstruye el ticket ESC/POS desde la fila
 *    persistida (igual que el original), descarga la firma desde
 *    Storage y lo manda a la PT210.
 *
 * Las dependencias remotas se envuelven siempre en [DomainResult] para
 * que la UI muestre copy específico (red, auth, no encontrado).
 */
interface RecaudacionHistoricaRepository {

    suspend fun listarMias(): DomainResult<List<RecaudacionHistorica>>

    /**
     * Recupera una sola recaudación por id del cliente. La estrategia
     * es leer del último listado (in-memory cache); si no está,
     * recarga el listado y vuelve a buscar.
     */
    suspend fun obtenerDetalle(recaudacionId: String): DomainResult<RecaudacionHistorica>

    /** Devuelve la signed URL del PDF de archivo. */
    suspend fun reimprimirPdf(recaudacionId: String): DomainResult<String>

    /**
     * Reconstruye el ticket ESC/POS desde la fila y lo manda a la
     * impresora vinculada. Si no hay impresora vinculada (o falla la
     * conexión), devuelve [DomainResult.Failure] con un código que
     * mapea al mismo copy que en T-62.
     */
    suspend fun reimprimirBluetooth(recaudacionId: String): DomainResult<PrintResult>
}

@Singleton
class RecaudacionHistoricaRepositoryImpl @Inject constructor(
    private val remote: RecaudacionHistoricaRemoteDataSource,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val empresaParamsDao: EmpresaParamsDao,
    private val printerRepository: PrinterRepository,
) : RecaudacionHistoricaRepository {

    /**
     * Cache in-memory del último listado descargado. Solo lo usa el
     * detalle: para abrir la pantalla de detalle el técnico viene
     * siempre desde la lista, así que la fila está cacheada.
     */
    @Volatile
    private var ultimaListaCache: List<RecaudacionHistoricaRow> = emptyList()

    override suspend fun listarMias(): DomainResult<List<RecaudacionHistorica>> {
        val empresaId = empresaActiva() ?: return DomainResult.Failure(DomainError.Auth())
        val tecnicoId = authRepository.currentUserId()
            ?: return DomainResult.Failure(DomainError.Auth())
        return runCatching { remote.listarMias(empresaId, tecnicoId) }.fold(
            onSuccess = { rows ->
                ultimaListaCache = rows
                DomainResult.Success(rows.map(::mapToDominio))
            },
            onFailure = ::mapErrorToDomain,
        )
    }

    override suspend fun obtenerDetalle(recaudacionId: String): DomainResult<RecaudacionHistorica> {
        ultimaListaCache.firstOrNull { it.id == recaudacionId }?.let {
            return DomainResult.Success(mapToDominio(it))
        }
        // Cache miss (reinicio de proceso, deep-link, etc.): recarga.
        return when (val result = listarMias()) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> {
                val match = ultimaListaCache.firstOrNull { it.id == recaudacionId }
                if (match == null) DomainResult.Failure(DomainError.NotFound())
                else DomainResult.Success(mapToDominio(match))
            }
        }
    }

    override suspend fun reimprimirPdf(recaudacionId: String): DomainResult<String> {
        return runCatching { remote.reimprimirSignedUrl(recaudacionId) }.fold(
            onSuccess = { DomainResult.Success(it) },
            onFailure = ::mapErrorToDomain,
        )
    }

    override suspend fun reimprimirBluetooth(recaudacionId: String): DomainResult<PrintResult> {
        val empresaId = empresaActiva() ?: return DomainResult.Failure(DomainError.Auth())
        val row = ultimaListaCache.firstOrNull { it.id == recaudacionId }
            ?: return when (val list = listarMias()) {
                is DomainResult.Failure -> list
                is DomainResult.Success -> {
                    val match = ultimaListaCache.firstOrNull { it.id == recaudacionId }
                        ?: return DomainResult.Failure(DomainError.NotFound())
                    reimprimirBluetoothInternal(empresaId, match)
                }
            }
        return reimprimirBluetoothInternal(empresaId, row)
    }

    private suspend fun reimprimirBluetoothInternal(
        empresaId: String,
        row: RecaudacionHistoricaRow,
    ): DomainResult<PrintResult> {
        // 1. Empresa: lo necesita la cabecera/pie del ticket.
        val empresa = empresaParamsDao.observe(empresaId).first()

        // 2. Reconstruir el "MaquinaConInstalacion" sintético: solo
        //    necesitamos los campos visibles del ticket. Los campos no
        //    usados (estado, valorCredito, tasaSemanal, porcentajeLocal,
        //    baselineFecha, baselineOrigen) llevan placeholders seguros.
        val instalacion = row.instalacion
        val maquinaSnap = MaquinaConInstalacion(
            instalacionId = row.instalacionId,
            maquinaId = instalacion?.maquina?.id ?: "",
            numeroSerie = instalacion?.maquina?.numeroSerie ?: "—",
            modelo = instalacion?.maquina?.modelo,
            fabricante = instalacion?.maquina?.fabricante,
            estado = "instalada",
            valorCredito = row.valorCreditoAplicado,
            licenciaNumero = instalacion?.licencia?.numero ?: "—",
            tasaSemanal = row.tasaSemanalAplicada,
            porcentajeLocal = row.porcentajeLocalAplicado,
            baselineEntradas = row.contadorEntradasAnterior,
            baselineSalidas = row.contadorSalidasAnterior,
            baselineFecha = parseInstantOrNow(row.fecha),
            baselineOrigen = "recaudacion_anterior",
            baselineReferenciaId = null,
            localId = instalacion?.local?.id ?: "",
            localNombre = instalacion?.local?.nombre ?: "—",
            localDireccion = instalacion?.local?.direccion,
        )

        // 3. Cifras desde los campos persistidos (escala 2 ya en server).
        val cifras = Cifras(
            procede = row.estado != "anulada",
            bruto = BigDecimal(row.recaudacionBruta),
            semanas = row.semanasAplicadas,
            tasaSemanal = BigDecimal(row.tasaSemanalAplicada),
            tasaTotal = BigDecimal(row.tasaTotalAplicada),
            neto = BigDecimal(row.recaudacionNeta),
            porcentajeLocal = BigDecimal(row.porcentajeLocalAplicado),
            // Reposición de tolva del servidor (T-225); base_reparto no se persiste:
            // = parte_local + parte_empresa (exacto, = neto − reposición).
            reposicionTolva = BigDecimal(row.reposicionTolva),
            baseReparto = BigDecimal(row.parteLocal).add(BigDecimal(row.parteEmpresa)),
            parteLocal = BigDecimal(row.parteLocal),
            parteEmpresa = BigDecimal(row.parteEmpresa),
            valorCredito = BigDecimal(row.valorCreditoAplicado),
            baselineEntradas = row.contadorEntradasAnterior,
            baselineSalidas = row.contadorSalidasAnterior,
            deltaEntradas = row.contadorEntradasActual - row.contadorEntradasAnterior,
            deltaSalidas = row.contadorSalidasActual - row.contadorSalidasAnterior,
            creditos = (row.contadorEntradasActual - row.contadorEntradasAnterior) -
                (row.contadorSalidasActual - row.contadorSalidasAnterior),
        )

        // 4. Firma original desde Storage. Si no se puede descargar
        //    (ej. recaudación muy antigua sin firma), reimprimimos
        //    sin firma — mejor que bloquear.
        val firmaPng = row.firmaUrl?.let { remote.descargarFirma(it) } ?: ByteArray(0)

        // 5. Manda el ticket. PrinterRepository ya valida permisos +
        //    Bluetooth + impresora vinculada y devuelve PrintResult.
        val result = printerRepository.imprimirTicketRecaudacion(
            empresa = empresa,
            localNombre = maquinaSnap.localNombre,
            localDireccion = maquinaSnap.localDireccion,
            maquina = maquinaSnap,
            tecnicoEmail = authRepository.currentUserEmail(),
            fecha = parseInstantOrNow(row.fecha),
            contadorEntradasActual = row.contadorEntradasActual,
            contadorSalidasActual = row.contadorSalidasActual,
            cifras = cifras,
            desgloseTotal = row.desgloseTotal.map(::toDomainDenom),
            desgloseLocal = row.desgloseLocal.map(::toDomainDenom),
            firmaPng = firmaPng,
        )
        return DomainResult.Success(result)
    }

    // -------------------------------------------------------------------- helpers

    private suspend fun empresaActiva(): String? =
        (sessionRepository.state.value as? SessionState.Active)?.empresa?.id

    private fun parseInstantOrNow(value: String): Instant =
        runCatching { Instant.parse(value) }.getOrElse { Instant.now() }

    private fun toDomainDenom(dto: DenominacionItemDto): DenominacionItem =
        DenominacionItem(
            denominacion = BigDecimal(dto.denominacion.toString()),
            cantidad = dto.cantidad,
        )

    private fun mapToDominio(row: RecaudacionHistoricaRow): RecaudacionHistorica =
        RecaudacionHistorica(
            id = row.id,
            fecha = parseInstantOrNow(row.fecha),
            estado = when (row.estado) {
                "anulada" -> EstadoHistorico.Anulada
                else -> EstadoHistorico.Firme
            },
            conflictoPendiente = row.conflicto && row.revisadoEn == null,
            localNombre = row.instalacion?.local?.nombre ?: "—",
            maquinaSerie = row.instalacion?.maquina?.numeroSerie ?: "—",
            maquinaModelo = row.instalacion?.maquina?.modelo,
            licenciaNumero = row.instalacion?.licencia?.numero,
            bruto = BigDecimal(row.recaudacionBruta),
            neto = BigDecimal(row.recaudacionNeta),
            parteLocal = BigDecimal(row.parteLocal),
            parteEmpresa = BigDecimal(row.parteEmpresa),
            tieneTicketPdf = !row.pdfUrl.isNullOrBlank(),
            motivoAnulacion = row.motivoAnulacion,
        )

    private fun <T> mapErrorToDomain(throwable: Throwable): DomainResult<T> {
        Timber.w(throwable, "RecaudacionHistoricaRepository error")
        val message = throwable.message.orEmpty()
        return when {
            message.contains("network", ignoreCase = true) ||
                message.contains("connect", ignoreCase = true) ||
                message.contains("unable to resolve", ignoreCase = true) ->
                DomainResult.Failure(DomainError.Network(message))

            message.contains("not_found", ignoreCase = true) ->
                DomainResult.Failure(DomainError.NotFound(message))

            message.contains("forbidden", ignoreCase = true) ->
                DomainResult.Failure(DomainError.Auth(message))

            else -> DomainResult.Failure(DomainError.Unknown(message))
        }
    }
}

/**
 * Modelo de dominio compacto que consume la lista. El detalle reusa el
 * mismo, complementado con los desgloses cuando hagan falta (por ahora
 * la lista basta).
 */
data class RecaudacionHistorica(
    val id: String,
    val fecha: Instant,
    val estado: EstadoHistorico,
    val conflictoPendiente: Boolean,
    val localNombre: String,
    val maquinaSerie: String,
    val maquinaModelo: String?,
    val licenciaNumero: String?,
    val bruto: BigDecimal,
    val neto: BigDecimal,
    val parteLocal: BigDecimal,
    val parteEmpresa: BigDecimal,
    val tieneTicketPdf: Boolean,
    val motivoAnulacion: String?,
)

enum class EstadoHistorico { Firme, Anulada }
