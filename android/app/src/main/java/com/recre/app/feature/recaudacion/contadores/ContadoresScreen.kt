package com.recre.app.feature.recaudacion.contadores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.locks.LockState
import com.recre.app.feature.recaudacion.RecaudacionFlowViewModel
import com.recre.app.feature.recaudacion.RecaudacionTestTags
import com.recre.app.feature.recaudacion.components.BaselineCambiadaDialog
import com.recre.app.feature.recaudacion.components.CifrasResumenCard
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.Keypad
import com.recre.app.ui.components.PasoTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.components.RecreTonalButton
import com.recre.app.ui.theme.RecreShapes
import com.recre.app.ui.theme.RecreType

/**
 * Paso 1 del flujo de recaudación (T-54).
 *
 * El técnico introduce los contadores actuales (entradas y salidas). El
 * ViewModel calcula las cifras en vivo con [calcularRecaudacion] y las
 * muestra en una tarjeta [CifrasResumenCard].
 *
 * En este PR (T-58/T-59) la pantalla añade:
 *  - Banner "Sincronización obligatoria" si `state.syncStale` (T-59).
 *  - AlertDialog "Lock ocupado" cuando otro técnico tiene el lock (T-58).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContadoresScreen(
    viewModel: RecaudacionFlowViewModel,
    onContinuar: () -> Unit,
    onLecturaNoRecaudada: () -> Unit,
    onBack: () -> Unit,
    onRehacer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maquina = state.maquina

    BaselineCambiadaDialog(
        visible = state.baselineCambiada && !state.avisoBaselineVisto,
        onMarcarVisto = viewModel::marcarAvisoBaselineVisto,
        onRehacer = onRehacer,
    )
    val cifras = state.cifras

    val lockOcupado = state.lockState is LockState.Ocupado

    // OCR en vivo: el escáner (preview de cámara) se muestra como overlay a
    // pantalla completa sobre esta pantalla. Su visibilidad es estado local; el
    // flujo solo recibe los valores confirmados vía `aplicarLecturaOcr`.
    var mostrarEscaner by remember { mutableStateOf(false) }
    var permisoCamaraDenegado by remember { mutableStateOf(false) }
    // Campo activo que dirige el keypad (entradas o salidas); el IME del sistema nunca aparece.
    var activeCampo by remember { mutableStateOf(CampoContador.Entradas) }

    val cadena = state.cadena
    val tituloPaso =
        if (cadena != null) {
            stringResource(R.string.recaudacion_paso_contadores_cadena, cadena.posicion, cadena.total)
        } else {
            stringResource(R.string.recaudacion_paso_contadores)
        }

    Scaffold(
        topBar = {
            PasoTopBar(
                titulo = tituloPaso,
                pasoActual = 1,
                onBack = {
                    viewModel.liberarLockAlSalir()
                    onBack()
                },
                subtitulo = maquina?.numeroSerie,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.cargando -> Mensaje(stringResource(R.string.local_detalle_cargando))
                state.errorCarga != null || maquina == null ->
                    Mensaje(stringResource(R.string.recaudacion_error_cargar))
                state.syncStale -> {
                    SyncStaleBlocker(onBack = {
                        viewModel.liberarLockAlSalir()
                        onBack()
                    })
                }
                else -> {
                    val entradasError = state.contadorEntradasInput.isNotBlank() &&
                        (state.contadorEntradasInput.toLongOrNull() ?: -1) < maquina.baselineEntradas
                    val salidasError = state.contadorSalidasInput.isNotBlank() &&
                        (state.contadorSalidasInput.toLongOrNull() ?: -1) < maquina.baselineSalidas
                    // Contenido scrolleable; el keypad queda anclado abajo (sticky).
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        BaselineHint(
                            baselineEntradas = maquina.baselineEntradas,
                            baselineSalidas = maquina.baselineSalidas,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.recaudacion_ocr_ayuda),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        ContadorOcrBoton(
                            label = stringResource(R.string.recaudacion_ocr_escanear_contadores),
                            testTag = RecaudacionTestTags.OCR_ESCANEAR,
                            onEscanear = {
                                permisoCamaraDenegado = false
                                mostrarEscaner = true
                            },
                            onPermisoDenegado = { permisoCamaraDenegado = true },
                        )
                        if (permisoCamaraDenegado) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.recaudacion_ocr_error_permiso_camara),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        CeldaContador(
                            label = stringResource(R.string.recaudacion_label_contador_entradas),
                            valor = state.contadorEntradasInput,
                            activa = activeCampo == CampoContador.Entradas,
                            error = entradasError,
                            errorText = stringResource(
                                R.string.recaudacion_error_contador_menor,
                                maquina.baselineEntradas,
                            ),
                            onActivar = { activeCampo = CampoContador.Entradas },
                            testTag = RecaudacionTestTags.CONTADOR_ENTRADAS,
                        )
                        Spacer(Modifier.height(12.dp))
                        CeldaContador(
                            label = stringResource(R.string.recaudacion_label_contador_salidas),
                            valor = state.contadorSalidasInput,
                            activa = activeCampo == CampoContador.Salidas,
                            error = salidasError,
                            errorText = stringResource(
                                R.string.recaudacion_error_contador_menor,
                                maquina.baselineSalidas,
                            ),
                            onActivar = { activeCampo = CampoContador.Salidas },
                            testTag = RecaudacionTestTags.CONTADOR_SALIDAS,
                        )
                        if (cifras != null) {
                            Spacer(Modifier.height(16.dp))
                            CifrasResumenCard(
                                cifras = cifras,
                                recuperacion = state.recuperacion,
                                modifier = Modifier.testTag(RecaudacionTestTags.CIFRAS_RESUMEN),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Acciones(
                            cifrasOk = cifras != null && cifras.procede,
                            cifrasNoProcede = cifras != null && !cifras.procede,
                            // Si el lock está ocupado por otro técnico, deshabilitamos
                            // 'Continuar' hasta que el usuario lo fuerce desde el dialog.
                            bloqueadoPorLock = lockOcupado,
                            onContinuar = onContinuar,
                            onLecturaNoRecaudada = {
                                viewModel.liberarLockAlSalir()
                                onLecturaNoRecaudada()
                            },
                        )
                    }
                    // (R5) Keypad propio anclado abajo: única entrada, sin IME del sistema.
                    Keypad(
                        onDigit = { d ->
                            when (activeCampo) {
                                CampoContador.Entradas ->
                                    viewModel.onContadorEntradasChange(state.contadorEntradasInput + d)
                                CampoContador.Salidas ->
                                    viewModel.onContadorSalidasChange(state.contadorSalidasInput + d)
                            }
                        },
                        onBackspace = {
                            when (activeCampo) {
                                CampoContador.Entradas ->
                                    viewModel.onContadorEntradasChange(
                                        state.contadorEntradasInput.dropLast(1),
                                    )
                                CampoContador.Salidas ->
                                    viewModel.onContadorSalidasChange(
                                        state.contadorSalidasInput.dropLast(1),
                                    )
                            }
                        },
                        onNext = {
                            activeCampo =
                                if (activeCampo == CampoContador.Entradas) {
                                    CampoContador.Salidas
                                } else {
                                    CampoContador.Entradas
                                }
                        },
                        backspaceContentDescription = stringResource(R.string.recaudacion_keypad_borrar),
                        nextContentDescription = stringResource(R.string.recaudacion_keypad_siguiente),
                    )
                }
            }
        }
    }

    if (lockOcupado) {
        LockOcupadoDialog(
            estado = state.lockState as LockState.Ocupado,
            onForzar = viewModel::forzarLock,
            onCancelar = {
                viewModel.liberarLockAlSalir()
                onBack()
            },
        )
    }

    if (mostrarEscaner && maquina != null) {
        EscanerContadoresOverlay(
            baselineEntradas = maquina.baselineEntradas,
            baselineSalidas = maquina.baselineSalidas,
            onUsarLectura = { entradas, salidas ->
                viewModel.aplicarLecturaOcr(entradas, salidas)
                mostrarEscaner = false
            },
            onCerrar = { mostrarEscaner = false },
        )
    }
}

@Composable
private fun BaselineHint(baselineEntradas: Long, baselineSalidas: Long) {
    Text(
        text = stringResource(
            R.string.recaudacion_contadores_baseline,
            baselineEntradas.toString(),
            baselineSalidas.toString(),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CeldaContador(
    label: String,
    valor: String,
    activa: Boolean,
    error: Boolean,
    errorText: String,
    onActivar: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        AppCard(
            onClick = onActivar,
            selected = activa,
            contentDescription = "$label: ${valor.ifBlank { "vacío" }}",
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                // Lectura en grande (Geist Mono tabular). "—" si aún no hay dígitos.
                Text(
                    text = valor.ifBlank { "—" },
                    style = RecreType.importeMedium,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (error) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun Acciones(
    cifrasOk: Boolean,
    cifrasNoProcede: Boolean,
    bloqueadoPorLock: Boolean,
    onContinuar: () -> Unit,
    onLecturaNoRecaudada: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (cifrasNoProcede) {
            RecreTonalButton(
                text = stringResource(R.string.recaudacion_accion_lectura_no_recaudada),
                onClick = onLecturaNoRecaudada,
                fullWidth = true,
                modifier = Modifier.testTag(RecaudacionTestTags.CONTADORES_LECTURA_NO_RECAUDADA),
            )
        } else {
            RecrePrimaryButton(
                text = stringResource(R.string.recaudacion_accion_continuar),
                onClick = onContinuar,
                enabled = cifrasOk && !bloqueadoPorLock,
                modifier = Modifier.testTag(RecaudacionTestTags.CONTADORES_CONTINUAR),
            )
        }
    }
}

@Composable
private fun SyncStaleBlocker(onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RecreShapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.recaudacion_stale_titulo),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.recaudacion_stale_descripcion),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            RecrePrimaryButton(
                text = stringResource(R.string.recaudacion_stale_volver),
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun LockOcupadoDialog(
    estado: LockState.Ocupado,
    onForzar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Text(stringResource(R.string.recaudacion_lock_titulo))
        },
        text = {
            Text(stringResource(R.string.recaudacion_lock_descripcion))
        },
        confirmButton = {
            RecreTextButton(
                text = stringResource(R.string.recaudacion_lock_forzar),
                onClick = onForzar,
            )
        },
        dismissButton = {
            RecreTextButton(
                text = stringResource(R.string.recaudacion_lock_cancelar),
                onClick = onCancelar,
            )
        },
    )
}

@Composable
private fun Mensaje(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

enum class CampoContador { Entradas, Salidas }
