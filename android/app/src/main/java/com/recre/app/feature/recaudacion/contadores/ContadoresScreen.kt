package com.recre.app.feature.recaudacion.contadores

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.locks.LockState
import com.recre.app.feature.recaudacion.RecaudacionFlowViewModel
import com.recre.app.feature.recaudacion.RecaudacionTestTags
import com.recre.app.feature.recaudacion.components.BaselineCambiadaDialog
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.Banda
import com.recre.app.ui.components.BandaTono
import com.recre.app.ui.components.FilaHairline
import com.recre.app.ui.components.Keypad
import com.recre.app.ui.components.PasoTopBar
import com.recre.app.ui.components.RecreGhostButton
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.components.formatEur
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreShapes
import com.recre.app.ui.theme.RecreType

/**
 * Paso 1 del flujo de recaudación (T-54).
 *
 * El técnico introduce los contadores actuales (entradas y salidas). El
 * ViewModel calcula las cifras en vivo y la pantalla muestra el desglose
 * (créditos y bruto estimado) como filas hairline siempre visibles.
 *
 * En este PR (T-58/T-59) la pantalla añade:
 *  - Banner "Sincronización obligatoria" si `state.syncStale` (T-59).
 *  - AlertDialog "Lock ocupado" cuando otro técnico tiene el lock (T-58).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
                    val scrollState = rememberScrollState()
                    val cifrasRequester = remember { BringIntoViewRequester() }
                    // Al calcular (las cifras pasan a estar disponibles) llevamos el
                    // resultado a la vista: hacer scroll a mano era engorroso.
                    LaunchedEffect(cifras != null) {
                        if (cifras != null) cifrasRequester.bringIntoView()
                    }
                    // Contenido scrolleable; el keypad queda anclado abajo (sticky).
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(scrollState),
                    ) {
                        BaselineHint(
                            baselineEntradas = maquina.baselineEntradas,
                            baselineSalidas = maquina.baselineSalidas,
                        )
                        Spacer(Modifier.height(12.dp))
                        // Escanear como acción fantasma full-width (mockup N7): píldora
                        // transparente con icono de cámara, entre la banda y las lecturas.
                        // Conserva el manejo de permiso de cámara existente.
                        ContadorOcrBoton(
                            label = stringResource(R.string.recaudacion_ocr_escanear_corto),
                            testTag = RecaudacionTestTags.OCR_ESCANEAR,
                            fullWidth = true,
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
                            baseline = maquina.baselineEntradas,
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
                            baseline = maquina.baselineSalidas,
                            error = salidasError,
                            errorText = stringResource(
                                R.string.recaudacion_error_contador_menor,
                                maquina.baselineSalidas,
                            ),
                            onActivar = { activeCampo = CampoContador.Salidas },
                            testTag = RecaudacionTestTags.CONTADOR_SALIDAS,
                        )
                        // Desglose siempre visible como filas hairline (mockup N7): «—»
                        // cuando aún no hay cifras. El bringIntoView se ancla al bloque.
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(RecaudacionTestTags.CIFRAS_RESUMEN)
                                .bringIntoViewRequester(cifrasRequester),
                        ) {
                            FilaHairline(
                                label = stringResource(R.string.recaudacion_label_creditos),
                                value = cifras?.let { "+${it.creditos}" } ?: "—",
                            )
                            FilaHairline(
                                label = stringResource(R.string.recaudacion_contadores_bruto_estimado),
                                value = cifras?.let { formatEur(it.bruto.toPlainString()) } ?: "—",
                                hairline = false,
                                emphasis = true,
                            )
                        }
                        // El aviso «lectura no recaudada» sigue siendo la señal clave:
                        // se conserva como banda de deuda cuando el bruto no cubre la tasa.
                        if (cifras != null && !cifras.procede) {
                            Spacer(Modifier.height(12.dp))
                            Banda(
                                texto = buildAnnotatedString {
                                    append(stringResource(R.string.recaudacion_no_procede_descripcion))
                                },
                                icon = Icons.Filled.Info,
                                tono = BandaTono.DEUDA,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Acciones(
                            cifrasNoProcede = cifras != null && !cifras.procede,
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
                            // En salidas con cifras que proceden, la tecla ok confirma y
                            // avanza de paso; en el resto solo togglea el campo activo.
                            if (activeCampo == CampoContador.Salidas && cifras != null && cifras.procede && !lockOcupado) {
                                onContinuar()
                            } else {
                                activeCampo =
                                    if (activeCampo == CampoContador.Entradas) {
                                        CampoContador.Salidas
                                    } else {
                                        CampoContador.Entradas
                                    }
                            }
                        },
                        // La tecla ok muestra «Continuar» solo cuando pulsarla confirma el
                        // paso (salidas + cifras que proceden y sin lock ajeno); si no, avanza
                        // al otro campo. La condición coincide con la de `onNext` para que la
                        // etiqueta nunca prometa un avance que el gate va a bloquear.
                        okLabel =
                            if (activeCampo == CampoContador.Salidas && cifras != null && cifras.procede && !lockOcupado) {
                                stringResource(R.string.recaudacion_accion_continuar)
                            } else {
                                stringResource(R.string.recaudacion_keypad_siguiente)
                            },
                        backspaceContentDescription = stringResource(R.string.recaudacion_keypad_borrar),
                        nextContentDescription = stringResource(R.string.recaudacion_keypad_siguiente_campo),
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
    // Valores con separador de miles ES (punto), resaltados en negrita dentro de la banda.
    val e = "%,d".format(baselineEntradas).replace(',', '.')
    val s = "%,d".format(baselineSalidas).replace(',', '.')
    val prefijoE = stringResource(R.string.recaudacion_contadores_ultima_lectura_entradas)
    val prefijoS = stringResource(R.string.recaudacion_contadores_ultima_lectura_salidas)
    val texto = buildAnnotatedString {
        append("$prefijoE ")
        withStyle(SpanStyle(fontWeight = FontWeight.W600)) { append(e) }
        append(" · $prefijoS ")
        withStyle(SpanStyle(fontWeight = FontWeight.W600)) { append(s) }
    }
    Banda(texto = texto, icon = Icons.Filled.Info)
}

@Composable
private fun CeldaContador(
    label: String,
    valor: String,
    activa: Boolean,
    baseline: Long,
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
                // Lectura en grande (Geist Mono tabular) con cursor parpadeante en la
                // celda activa; "—" si aún no hay dígitos.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = valor.ifBlank { "—" },
                        style = RecreType.importeMedium,
                        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    if (activa) {
                        CursorParpadeante()
                    }
                }
            }
        }
        // Pista de delta cuando el valor teclado supera el baseline: solo presentación
        // (valorActual − baseline), no es cálculo económico. Si hay error, gana el error.
        val valorActual = valor.toLongOrNull()
        if (error) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        } else if (valorActual != null && valorActual > baseline) {
            Spacer(Modifier.height(4.dp))
            val delta = "%,d".format(valorActual - baseline).replace(',', '.')
            Text(
                text = stringResource(R.string.recaudacion_contadores_delta, delta),
                style = RecreType.eyebrow,
                color = RecreColors.current.mutedStrong,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Cursor «|» del campo activo: parpadeo alpha 1→0 en `accentBright`. Con las
 * animaciones del sistema desactivadas (reduced-motion) queda fijo y visible.
 */
@Composable
private fun CursorParpadeante() {
    val reducedMotion = rememberReducedMotion()
    val alpha =
        if (reducedMotion) {
            1f
        } else {
            val transition = rememberInfiniteTransition(label = "cursor")
            transition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "cursor-alpha",
            ).value
        }
    Text(
        text = "|",
        style = RecreType.importeMedium,
        color = RecreColors.current.accentBright.copy(alpha = alpha),
        modifier = Modifier.padding(start = 2.dp),
    )
}

/**
 * true cuando el usuario ha desactivado/reducido las animaciones del sistema
 * (ANIMATOR_DURATION_SCALE == 0). Equivale a prefers-reduced-motion.
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        val escala = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        escala == 0f
    }
}

@Composable
private fun Acciones(
    cifrasNoProcede: Boolean,
    onLecturaNoRecaudada: () -> Unit,
) {
    // El «Continuar» vive ahora en la tecla ok del keypad; aquí solo queda la
    // salida «lectura no recaudada» cuando el bruto no cubre la tasa.
    if (cifrasNoProcede) {
        Row(modifier = Modifier.fillMaxWidth()) {
            RecreGhostButton(
                text = stringResource(R.string.recaudacion_accion_lectura_no_recaudada),
                onClick = onLecturaNoRecaudada,
                fullWidth = true,
                mini = true,
                modifier = Modifier.testTag(RecaudacionTestTags.CONTADORES_LECTURA_NO_RECAUDADA),
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
