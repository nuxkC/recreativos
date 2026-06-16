package com.recre.app.feature.recaudacion.confirmacion

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.printer.PrintResult
import com.recre.app.core.printer.PrinterError
import com.recre.app.feature.recaudacion.RecaudacionFlowState
import com.recre.app.feature.recaudacion.RecaudacionFlowViewModel
import com.recre.app.feature.recaudacion.RecaudacionTestTags
import com.recre.app.feature.recaudacion.components.BaselineCambiadaDialog
import com.recre.app.feature.recaudacion.components.CifrasResumenCard
import com.recre.app.feature.recaudacion.components.RecuperacionResumenCard
import com.recre.app.feature.recaudacion.components.SignaturePad

/**
 * Paso final del flujo (T-56). Antes de "Guardar" pinta el resumen +
 * firma. Tras "Guardar" pinta una segunda vista compacta con:
 *
 *  - Confirmación de persistencia (online o pendiente).
 *  - Estado de la impresión Bluetooth (T-62): "Imprimiendo…" /
 *    "Ticket impreso" / "No se pudo imprimir: <motivo>".
 *  - Botón "Reintentar impresión" cuando el envío falló.
 *  - Botón "Continuar" que cierra el flujo (o salta al siguiente en
 *    modo cadena, T-60).
 *
 * No auto-navega: el técnico decide cuándo cerrar para tener tiempo
 * de leer el resultado y reintentar la impresión si hace falta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmacionScreen(
    viewModel: RecaudacionFlowViewModel,
    onFinalizar: () -> Unit,
    onBack: () -> Unit,
    onRehacer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BaselineCambiadaDialog(
        visible = state.baselineCambiada && !state.avisoBaselineVisto,
        onMarcarVisto = viewModel::marcarAvisoBaselineVisto,
        onRehacer = onRehacer,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recaudacion_paso_confirmacion)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!state.guardado) viewModel.liberarLockAlSalir()
                            onBack()
                        },
                        enabled = !state.guardando && !state.imprimiendo,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.guardado) {
                PostGuardadoBlock(
                    state = state,
                    onReintentarImpresion = viewModel::imprimirTicket,
                    onContinuar = onFinalizar,
                )
            } else {
                FormularioBlock(state = state, viewModel = viewModel, onRehacer = onRehacer)
            }
        }
    }
}

@Composable
private fun FormularioBlock(
    state: RecaudacionFlowState,
    viewModel: RecaudacionFlowViewModel,
    onRehacer: () -> Unit,
) {
    val cifras = state.cifras
    if (cifras == null) {
        Text(
            text = stringResource(R.string.recaudacion_error_cargar),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    CifrasResumenCard(cifras = cifras)

    // Recuperación de deuda (T-215): si esta recaudación amortiza deuda del
    // local, mostramos cuánto se retiene y cuánto se le entrega.
    val plan = state.recuperacion
    if (plan != null && plan.recuperadoTotal.signum() > 0) {
        Spacer(Modifier.height(16.dp))
        RecuperacionResumenCard(
            creditos = state.creditosAbiertos,
            plan = plan,
            ordenManual = state.ordenManual,
            reordenable = false,
            onSubir = {},
            onBajar = {},
        )
    }

    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.recaudacion_firma_titulo),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.recaudacion_firma_descripcion),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    SignaturePad(
        strokes = state.firmaStrokes,
        onStrokeAppend = viewModel::onFirmaStrokeAppend,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(
            onClick = viewModel::onFirmaLimpiar,
            enabled = state.firmaStrokes.isNotEmpty() && !state.guardando,
        ) {
            Text(stringResource(R.string.recaudacion_firma_limpiar))
        }
    }

    if (state.baselineCambiada) {
        Spacer(Modifier.height(16.dp))
        BaselineCambiadaAviso(onRehacer = onRehacer)
    }

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = viewModel::onGuardar,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RecaudacionTestTags.CONFIRMACION_GUARDAR),
        enabled = state.firmaStrokes.isNotEmpty() && !state.guardando &&
            !state.syncStale && !state.baselineCambiada,
    ) {
        if (state.guardando) {
            Box(modifier = Modifier.size(20.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = if (state.guardando) {
                stringResource(R.string.recaudacion_accion_guardando)
            } else {
                stringResource(R.string.recaudacion_accion_guardar)
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.recaudacion_persistencia_offline),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PostGuardadoBlock(
    state: RecaudacionFlowState,
    onReintentarImpresion: () -> Unit,
    onContinuar: () -> Unit,
) {
    // Si tras guardar todavía no hubo intento (printResult == null) y
    // tampoco está imprimiendo, re-disparamos para cubrir el caso del
    // proceso muerto entre `onGuardar` y la composición.
    LaunchedEffect(state.guardado) {
        if (state.guardado && state.printResult == null && !state.imprimiendo) {
            onReintentarImpresion()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.recaudacion_post_guardado_titulo),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (state.subidoOnline) {
                        stringResource(R.string.recaudacion_post_guardado_online)
                    } else {
                        stringResource(R.string.recaudacion_post_guardado_pendiente)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    ImpresionStatusCard(
        imprimiendo = state.imprimiendo,
        printResult = state.printResult,
        onReintentar = onReintentarImpresion,
    )

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onContinuar,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.imprimiendo,
    ) {
        Text(stringResource(R.string.recaudacion_post_guardado_continuar))
    }
}

@Composable
private fun ImpresionStatusCard(
    imprimiendo: Boolean,
    printResult: PrintResult?,
    onReintentar: () -> Unit,
) {
    val containerColor = when {
        printResult is PrintResult.Failure -> MaterialTheme.colorScheme.errorContainer
        printResult is PrintResult.Success -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when {
        printResult is PrintResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    imprimiendo -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )

                    printResult is PrintResult.Success -> Icon(
                        Icons.Default.Print,
                        contentDescription = null,
                        tint = onContainer,
                    )

                    printResult is PrintResult.Failure -> Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = onContainer,
                    )

                    else -> Icon(
                        Icons.Default.Print,
                        contentDescription = null,
                        tint = onContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = when {
                        imprimiendo -> stringResource(R.string.recaudacion_impresion_en_curso)
                        printResult is PrintResult.Success ->
                            stringResource(R.string.recaudacion_impresion_ok)
                        printResult is PrintResult.Failure ->
                            stringResource(R.string.recaudacion_impresion_error_titulo)
                        else -> stringResource(R.string.recaudacion_impresion_pendiente)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = onContainer,
                )
            }

            if (printResult is PrintResult.Failure) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = printerErrorTexto(printResult.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onReintentar, enabled = !imprimiendo) {
                        Text(stringResource(R.string.recaudacion_impresion_reintentar))
                    }
                }
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

/**
 * (A) Aviso bloqueante cuando la baseline cambió a mitad del flujo. Explica el
 * porqué y ofrece rehacer la lectura desde el paso de contadores —única salida
 * segura: guardar con un desglose obsoleto haría que el server lo rechazara—.
 */
@Composable
private fun BaselineCambiadaAviso(onRehacer: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.recaudacion_baseline_cambiada_titulo),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.recaudacion_baseline_cambiada_mensaje),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRehacer, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recaudacion_baseline_cambiada_accion))
            }
        }
    }
}
