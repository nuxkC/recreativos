package com.recre.app.feature.historico

import com.recre.app.ui.components.formatEur

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.repository.EstadoHistorico
import com.recre.app.core.data.repository.RecaudacionHistorica
import com.recre.app.core.printer.PrintResult
import com.recre.app.core.printer.PrinterError
import java.math.BigDecimal
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
            TopAppBar(
                title = { Text(stringResource(R.string.historico_detalle_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
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
        CabeceraCard(recaudacion)
        Spacer(Modifier.height(12.dp))
        if (recaudacion.estado == EstadoHistorico.Anulada) {
            AnulacionCard(motivo = recaudacion.motivoAnulacion)
            Spacer(Modifier.height(12.dp))
        }
        if (recaudacion.conflictoPendiente) {
            ConflictoCard()
            Spacer(Modifier.height(12.dp))
        }
        CifrasCard(recaudacion)
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
            OutlinedButton(
                onClick = onReimprimirPdf,
                modifier = Modifier.weight(1f),
                enabled = recaudacion.tieneTicketPdf &&
                    !state.descargandoPdf &&
                    !state.imprimiendoBluetooth,
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.descargandoPdf) {
                        stringResource(R.string.historico_detalle_pdf_descargando)
                    } else {
                        stringResource(R.string.historico_detalle_pdf)
                    },
                )
            }
            Button(
                onClick = onReimprimirBluetooth,
                modifier = Modifier.weight(1f),
                enabled = !state.descargandoPdf && !state.imprimiendoBluetooth,
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.imprimiendoBluetooth) {
                        stringResource(R.string.historico_detalle_imprimiendo)
                    } else {
                        stringResource(R.string.historico_detalle_imprimir)
                    },
                )
            }
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
private fun CabeceraCard(recaudacion: RecaudacionHistorica) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = formatFecha(recaudacion.fecha),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            KeyValue(
                label = stringResource(R.string.historico_detalle_local),
                value = recaudacion.localNombre,
            )
            KeyValue(
                label = stringResource(R.string.historico_detalle_maquina),
                value = listOfNotNull(
                    recaudacion.maquinaSerie,
                    recaudacion.maquinaModelo,
                ).joinToString(" · "),
            )
            recaudacion.licenciaNumero?.let {
                KeyValue(
                    label = stringResource(R.string.historico_detalle_licencia),
                    value = it,
                )
            }
        }
    }
}

@Composable
private fun AnulacionCard(motivo: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.historico_detalle_anulada_titulo),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = motivo?.ifBlank { null }
                    ?: stringResource(R.string.historico_detalle_anulada_sin_motivo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConflictoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.historico_detalle_conflicto_titulo),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.historico_detalle_conflicto_descripcion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun CifrasCard(recaudacion: RecaudacionHistorica) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.historico_detalle_cifras_titulo),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            CifraRow(stringResource(R.string.historico_label_bruto), recaudacion.bruto)
            CifraRow(stringResource(R.string.historico_label_neto), recaudacion.neto)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            CifraRow(stringResource(R.string.historico_label_parte_local), recaudacion.parteLocal)
            CifraRow(
                stringResource(R.string.historico_label_parte_empresa),
                recaudacion.parteEmpresa,
            )
        }
    }
}

@Composable
private fun CifraRow(label: String, value: BigDecimal) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatEur(value),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
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
                    OutlinedButton(onClick = onLimpiar) {
                        Text(stringResource(R.string.historico_detalle_cerrar))
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCardCompact(textRes: Int, onCerrar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onCerrar) {
                Text(stringResource(R.string.historico_detalle_cerrar))
            }
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
