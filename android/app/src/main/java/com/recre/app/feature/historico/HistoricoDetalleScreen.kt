package com.recre.app.feature.historico

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.repository.RecaudacionHistorica
import com.recre.app.core.printer.PrintResult
import com.recre.app.core.printer.PrinterError
import com.recre.app.feature.historico.components.TicketRecibo
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreTonalButton
import com.recre.app.ui.theme.RecreShapes
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Detalle de "Mis recaudaciones" (T-63).
 *
 * Muestra cifras + cabecera del local/máquina/licencia y dos acciones
 * de reimpresión:
 *  - **Descargar PDF**: Edge `reimprimir-ticket` → signed URL → abre
 *    en navegador. Solo activa si la fila tiene `pdf_url`.
 *  - **Reimprimir Bluetooth**: reusa el pipeline de T-62 (descarga la
 *    firma, reconstruye el ticket ESC/POS, lo manda a la PT210
 *    vinculada). Si no hay impresora vinculada, el card muestra
 *    "No hay impresora" igual que en el flujo principal.
 *
 * Si la recaudación está anulada, se muestra el motivo en una card
 * `surfaceVariant` y los botones de reimpresión siguen disponibles
 * (la copia archivada del ticket sigue siendo válida como histórico).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoDetalleScreen(
    onBack: () -> Unit,
    viewModel: HistoricoDetalleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Cuando el VM emite signed URL, abrimos el navegador del sistema y
    // limpiamos el state para no reabrir al recomponer.
    LaunchedEffect(state.pdfSignedUrl) {
        val url = state.pdfSignedUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        viewModel.onPdfUrlConsumido()
    }

    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = stringResource(R.string.historico_detalle_titulo),
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            state.cargando -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.errorCarga != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        when (state.errorCarga!!) {
                            HistoricoErrorCode.Network -> R.string.historico_error_network
                            HistoricoErrorCode.Auth -> R.string.historico_error_auth
                            HistoricoErrorCode.Unknown -> R.string.historico_error_generic
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {
                val recaudacion = state.recaudacion ?: return@Scaffold
                Contenido(
                    recaudacion = recaudacion,
                    state = state,
                    onReimprimirPdf = viewModel::reimprimirPdf,
                    onReimprimirBluetooth = viewModel::reimprimirBluetooth,
                    onLimpiarPrintResult = viewModel::limpiarPrintResult,
                    onLimpiarErrorPdf = viewModel::limpiarErrorPdf,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }
    }
}

@Composable
private fun Contenido(
    recaudacion: RecaudacionHistorica,
    state: HistoricoDetalleUiState,
    onReimprimirPdf: () -> Unit,
    onReimprimirBluetooth: () -> Unit,
    onLimpiarPrintResult: () -> Unit,
    onLimpiarErrorPdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        TicketRecibo(
            recaudacion = recaudacion,
            fechaTexto = formatFecha(recaudacion.fecha),
        )
        Spacer(Modifier.height(16.dp))

        // Acciones de reimpresión
        Text(
            text = stringResource(R.string.historico_detalle_reimprimir_titulo),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))

        ImpresionStatusCard(
            imprimiendo = state.imprimiendoBluetooth,
            printResult = state.printResult,
            onLimpiar = onLimpiarPrintResult,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecreTonalButton(
                text = if (state.descargandoPdf) {
                    stringResource(R.string.historico_detalle_pdf_descargando)
                } else {
                    stringResource(R.string.historico_detalle_pdf)
                },
                onClick = onReimprimirPdf,
                enabled = recaudacion.tieneTicketPdf &&
                    !state.descargandoPdf &&
                    !state.imprimiendoBluetooth,
                loading = state.descargandoPdf,
                leadingIcon = Icons.Default.PictureAsPdf,
                fullWidth = true,
                modifier = Modifier.weight(1f),
            )
            RecrePrimaryButton(
                text = if (state.imprimiendoBluetooth) {
                    stringResource(R.string.historico_detalle_imprimiendo)
                } else {
                    stringResource(R.string.historico_detalle_imprimir)
                },
                onClick = onReimprimirBluetooth,
                enabled = !state.descargandoPdf && !state.imprimiendoBluetooth,
                loading = state.imprimiendoBluetooth,
                leadingIcon = Icons.Default.Bluetooth,
                fullWidth = true,
                modifier = Modifier.weight(1f),
            )
        }

        if (!recaudacion.tieneTicketPdf) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.historico_detalle_sin_pdf),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.errorPdf?.let { code ->
            Spacer(Modifier.height(8.dp))
            ErrorCardCompact(
                textRes = when (code) {
                    HistoricoErrorCode.Network -> R.string.historico_error_network
                    HistoricoErrorCode.Auth -> R.string.historico_error_auth
                    HistoricoErrorCode.Unknown -> R.string.historico_error_generic
                },
                onCerrar = onLimpiarErrorPdf,
            )
        }
    }
}

@Composable
private fun ImpresionStatusCard(
    imprimiendo: Boolean,
    printResult: PrintResult?,
    onLimpiar: () -> Unit,
) {
    if (!imprimiendo && printResult == null) return

    val containerColor = when (printResult) {
        is PrintResult.Failure -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contenidoColor = when (printResult) {
        is PrintResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contenidoColor,
        shape = RecreShapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (imprimiendo) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Print, contentDescription = null)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = when {
                        imprimiendo -> stringResource(R.string.recaudacion_impresion_en_curso)
                        printResult is PrintResult.Success ->
                            stringResource(R.string.recaudacion_impresion_ok)
                        else -> stringResource(R.string.recaudacion_impresion_error_titulo)
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (printResult is PrintResult.Failure) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = printerErrorTexto(printResult.error),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RecreTonalButton(
                        text = stringResource(R.string.historico_detalle_cerrar),
                        onClick = onLimpiar,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCardCompact(textRes: Int, onCerrar: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RecreShapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            RecreTonalButton(
                text = stringResource(R.string.historico_detalle_cerrar),
                onClick = onCerrar,
            )
        }
    }
}

@Composable
private fun printerErrorTexto(error: PrinterError): String = when (error) {
    PrinterError.BluetoothNoDisponible ->
        stringResource(R.string.impresora_error_bluetooth)
    PrinterError.SinPermiso ->
        stringResource(R.string.impresora_error_permiso)
    PrinterError.SinImpresora ->
        stringResource(R.string.impresora_error_sin_impresora)
    PrinterError.ModeloNoSoportado ->
        stringResource(R.string.impresora_error_modelo)
    PrinterError.NoEmparejada ->
        stringResource(R.string.impresora_error_no_emparejada)
    is PrinterError.ConexionFallida ->
        stringResource(R.string.impresora_error_conexion)
    is PrinterError.ImpresionFallida ->
        stringResource(R.string.impresora_error_impresion)
}

private fun formatFecha(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

// formatEur migrado al canónico de ui.components (money-safe, agrupación es-ES).
