package com.recre.app.feature.recaudacion.confirmacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
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
import com.recre.app.ui.components.CountUpText
import com.recre.app.ui.components.MoneyTextSize
import com.recre.app.ui.components.PasoTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.components.RecreTonalButton
import com.recre.app.ui.theme.RecreShapes

/**
 * Paso final del flujo (T-56). La pantalla muestra el resumen + la firma y un
 * botón "Guardar e imprimir".
 *
 * Al pulsarlo se abre un **modal de estados no descartable** (rediseño UX) que
 * hace legible lo que antes era un spinner opaco: acompaña el proceso real
 * paso a paso —guardando → subiendo → imprimiendo → éxito / reintentar—, con
 * una animación Lottie por estado. El técnico solo sale del modal con
 * "Continuar" (o "Reintentar impresión" si el ticket falló).
 *
 * No auto-navega: el técnico decide cuándo cerrar para leer el resultado.
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
            PasoTopBar(
                titulo = stringResource(R.string.recaudacion_paso_confirmacion),
                pasoActual = 3,
                onBack = {
                    if (!state.guardado) viewModel.liberarLockAlSalir()
                    onBack()
                },
                backEnabled = !state.guardando && !state.imprimiendo,
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
            FormularioBlock(state = state, viewModel = viewModel, onRehacer = onRehacer)
        }
    }

    // Tras pulsar Guardar, el modal acompaña el proceso real hasta el final.
    if (state.guardando || state.guardado) {
        GuardadoModal(
            state = state,
            onReintentarImpresion = viewModel::imprimirTicket,
            onContinuar = onFinalizar,
        )
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

    NetoHero(neto = cifras.neto)
    Spacer(Modifier.height(16.dp))
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
        modifier = Modifier.clip(RecreShapes.medium),
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        RecreTextButton(
            text = stringResource(R.string.recaudacion_firma_limpiar),
            onClick = viewModel::onFirmaLimpiar,
            enabled = state.firmaStrokes.isNotEmpty() && !state.guardando,
        )
    }

    if (state.baselineCambiada) {
        Spacer(Modifier.height(16.dp))
        BaselineCambiadaAviso(onRehacer = onRehacer)
    }

    Spacer(Modifier.height(24.dp))
    RecrePrimaryButton(
        text = stringResource(R.string.recaudacion_accion_guardar),
        onClick = viewModel::onGuardar,
        loading = state.guardando,
        enabled = state.firmaStrokes.isNotEmpty() && !state.guardando &&
            !state.guardado && !state.syncStale && !state.baselineCambiada,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RecaudacionTestTags.CONFIRMACION_GUARDAR),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.recaudacion_persistencia_offline),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// -----------------------------------------------------------------------------
// Modal de estados de guardado/impresión
// -----------------------------------------------------------------------------

/** Fase visible del proceso, derivada del estado del flujo. */
private enum class FaseGuardado { GUARDANDO, SUBIENDO, IMPRIMIENDO, EXITO, ERROR_IMPRESION }

private fun faseGuardado(state: RecaudacionFlowState): FaseGuardado = when {
    state.guardado && state.printResult is PrintResult.Success -> FaseGuardado.EXITO
    state.guardado && state.printResult is PrintResult.Failure -> FaseGuardado.ERROR_IMPRESION
    state.guardado -> FaseGuardado.IMPRIMIENDO
    state.subiendo -> FaseGuardado.SUBIENDO
    else -> FaseGuardado.GUARDANDO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardadoModal(
    state: RecaudacionFlowState,
    onReintentarImpresion: () -> Unit,
    onContinuar: () -> Unit,
) {
    // Re-dispara la impresión si el proceso murió entre `onGuardar` y la
    // composición (printResult quedó null sin estar imprimiendo).
    LaunchedEffect(state.guardado) {
        if (state.guardado && state.printResult == null && !state.imprimiendo) {
            onReintentarImpresion()
        }
    }

    val fase = faseGuardado(state)
    val terminal = fase == FaseGuardado.EXITO || fase == FaseGuardado.ERROR_IMPRESION
    // `rememberUpdatedState` para que el bloqueo de swipe lea SIEMPRE el valor
    // actual (el lambda de `rememberModalBottomSheetState` se fija una vez).
    val terminalActual = rememberUpdatedState(terminal)

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // Mientras hay trabajo en curso, no se puede descartar el modal.
        confirmValueChange = { value -> value != SheetValue.Hidden || terminalActual.value },
    )

    ModalBottomSheet(
        onDismissRequest = { if (terminal) onContinuar() },
        sheetState = sheetState,
        shape = RecreShapes.extraLarge,
        dragHandle = { if (terminal) BottomSheetDefaults.DragHandle() },
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EstadoAnim(fase = fase, modifier = Modifier.size(128.dp))
            Spacer(Modifier.height(12.dp))

            when (fase) {
                FaseGuardado.GUARDANDO -> EstadoTexto(
                    titulo = stringResource(R.string.recaudacion_accion_guardando),
                    subtitulo = stringResource(R.string.recaudacion_guardado_guardando_sub),
                )

                FaseGuardado.SUBIENDO -> EstadoTexto(
                    titulo = stringResource(R.string.recaudacion_guardado_subiendo_titulo),
                    subtitulo = stringResource(R.string.recaudacion_guardado_subiendo_sub),
                )

                FaseGuardado.IMPRIMIENDO -> EstadoTexto(
                    titulo = stringResource(R.string.recaudacion_impresion_en_curso),
                    subtitulo = stringResource(R.string.recaudacion_guardado_imprimiendo_sub),
                )

                FaseGuardado.EXITO -> {
                    EstadoTexto(
                        titulo = stringResource(R.string.recaudacion_post_guardado_titulo),
                        subtitulo = if (state.subidoOnline) {
                            stringResource(R.string.recaudacion_post_guardado_online)
                        } else {
                            stringResource(R.string.recaudacion_post_guardado_pendiente)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.recaudacion_impresion_ok),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(24.dp))
                    RecrePrimaryButton(
                        text = stringResource(R.string.recaudacion_post_guardado_continuar),
                        onClick = onContinuar,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                FaseGuardado.ERROR_IMPRESION -> {
                    val error = (state.printResult as? PrintResult.Failure)?.error
                    EstadoTexto(
                        titulo = stringResource(R.string.recaudacion_impresion_error_titulo),
                        subtitulo = error?.let { printerErrorTexto(it) } ?: "",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.recaudacion_guardado_error_nota),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    RecreTonalButton(
                        text = stringResource(R.string.recaudacion_impresion_reintentar),
                        onClick = onReintentarImpresion,
                        fullWidth = true,
                    )
                    Spacer(Modifier.height(4.dp))
                    RecreTextButton(
                        text = stringResource(R.string.recaudacion_post_guardado_continuar),
                        onClick = onContinuar,
                    )
                }
            }
        }
    }
}

@Composable
private fun EstadoTexto(titulo: String, subtitulo: String) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (subtitulo.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitulo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Animación Lottie del estado (loop en progreso, one-shot al terminar). Si el
 * asset no parsea en algún dispositivo, cae a un icono/spinner equivalente: el
 * modal nunca queda vacío.
 */
@Composable
private fun EstadoAnim(fase: FaseGuardado, modifier: Modifier = Modifier) {
    val rawRes = when (fase) {
        FaseGuardado.EXITO -> R.raw.recaudacion_exito
        FaseGuardado.ERROR_IMPRESION -> R.raw.recaudacion_error
        else -> R.raw.recaudacion_progreso
    }
    val terminal = fase == FaseGuardado.EXITO || fase == FaseGuardado.ERROR_IMPRESION
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val iterations = if (terminal) 1 else LottieConstants.IterateForever
    val progress by animateLottieCompositionAsState(composition, iterations = iterations)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (composition != null) {
            LottieAnimation(composition = composition, progress = { progress })
        } else {
            FallbackEstado(fase)
        }
    }
}

@Composable
private fun FallbackEstado(fase: FaseGuardado) {
    when (fase) {
        FaseGuardado.EXITO -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )

        FaseGuardado.ERROR_IMPRESION -> Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp),
        )

        else -> CircularProgressIndicator(strokeWidth = 4.dp)
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RecreShapes.medium,
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
            RecreTonalButton(
                text = stringResource(R.string.recaudacion_baseline_cambiada_accion),
                onClick = onRehacer,
                fullWidth = true,
            )
        }
    }
}

@Composable
private fun NetoHero(neto: java.math.BigDecimal) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.recaudacion_label_neto),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        CountUpText(
            importe = neto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
            size = MoneyTextSize.Hero,
        )
    }
}
