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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.locks.LockState
import com.recre.app.feature.recaudacion.RecaudacionFlowViewModel
import com.recre.app.feature.recaudacion.RecaudacionTestTags
import com.recre.app.feature.recaudacion.components.CifrasResumenCard

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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maquina = state.maquina
    val cifras = state.cifras

    val lockOcupado = state.lockState is LockState.Ocupado

    // OCR en vivo: el escáner (preview de cámara) se muestra como overlay a
    // pantalla completa sobre esta pantalla. Su visibilidad es estado local; el
    // flujo solo recibe los valores confirmados vía `aplicarLecturaOcr`.
    var mostrarEscaner by remember { mutableStateOf(false) }
    var permisoCamaraDenegado by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val cadena = state.cadena
                        Text(
                            text = if (cadena != null) {
                                stringResource(
                                    R.string.recaudacion_paso_contadores_cadena,
                                    cadena.posicion,
                                    cadena.total,
                                )
                            } else {
                                stringResource(R.string.recaudacion_paso_contadores)
                            },
                        )
                        if (maquina != null) {
                            Text(
                                text = maquina.numeroSerie,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.liberarLockAlSalir()
                        onBack()
                    }) {
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
                    BaselineHint(
                        baselineEntradas = maquina.baselineEntradas,
                        baselineSalidas = maquina.baselineSalidas,
                    )
                    Spacer(Modifier.height(16.dp))
                    CamposContadores(
                        entradas = state.contadorEntradasInput,
                        salidas = state.contadorSalidasInput,
                        baselineEntradas = maquina.baselineEntradas,
                        baselineSalidas = maquina.baselineSalidas,
                        onEntradasChange = viewModel::onContadorEntradasChange,
                        onSalidasChange = viewModel::onContadorSalidasChange,
                        permisoCamaraDenegado = permisoCamaraDenegado,
                        onEscanear = {
                            permisoCamaraDenegado = false
                            mostrarEscaner = true
                        },
                        onPermisoCamaraDenegado = { permisoCamaraDenegado = true },
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
private fun CamposContadores(
    entradas: String,
    salidas: String,
    baselineEntradas: Long,
    baselineSalidas: Long,
    onEntradasChange: (String) -> Unit,
    onSalidasChange: (String) -> Unit,
    permisoCamaraDenegado: Boolean,
    onEscanear: () -> Unit,
    onPermisoCamaraDenegado: () -> Unit,
) {
    val entradasError = entradas.isNotBlank() &&
        (entradas.toLongOrNull() ?: -1) < baselineEntradas
    val salidasError = salidas.isNotBlank() &&
        (salidas.toLongOrNull() ?: -1) < baselineSalidas

    Text(
        text = stringResource(R.string.recaudacion_ocr_ayuda),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))

    // Un único botón abre el escáner en vivo, que detecta ambos contadores.
    ContadorOcrBoton(
        label = stringResource(R.string.recaudacion_ocr_escanear_contadores),
        testTag = RecaudacionTestTags.OCR_ESCANEAR,
        onEscanear = onEscanear,
        onPermisoDenegado = onPermisoCamaraDenegado,
    )
    if (permisoCamaraDenegado) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recaudacion_ocr_error_permiso_camara),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = entradas,
        onValueChange = onEntradasChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RecaudacionTestTags.CONTADOR_ENTRADAS),
        label = { Text(stringResource(R.string.recaudacion_label_contador_entradas)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = entradasError,
        supportingText = {
            if (entradasError) {
                Text(
                    stringResource(
                        R.string.recaudacion_error_contador_menor,
                        baselineEntradas,
                    ),
                )
            }
        },
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = salidas,
        onValueChange = onSalidasChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RecaudacionTestTags.CONTADOR_SALIDAS),
        label = { Text(stringResource(R.string.recaudacion_label_contador_salidas)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = salidasError,
        supportingText = {
            if (salidasError) {
                Text(
                    stringResource(
                        R.string.recaudacion_error_contador_menor,
                        baselineSalidas,
                    ),
                )
            }
        },
    )
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
            OutlinedButton(
                onClick = onLecturaNoRecaudada,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RecaudacionTestTags.CONTADORES_LECTURA_NO_RECAUDADA),
            ) {
                Text(stringResource(R.string.recaudacion_accion_lectura_no_recaudada))
            }
        } else {
            Button(
                onClick = onContinuar,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RecaudacionTestTags.CONTADORES_CONTINUAR),
                enabled = cifrasOk && !bloqueadoPorLock,
            ) {
                Text(stringResource(R.string.recaudacion_accion_continuar))
            }
        }
    }
}

@Composable
private fun SyncStaleBlocker(onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
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
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.recaudacion_stale_volver))
            }
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
            Button(onClick = onForzar) {
                Text(stringResource(R.string.recaudacion_lock_forzar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(stringResource(R.string.recaudacion_lock_cancelar))
            }
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
