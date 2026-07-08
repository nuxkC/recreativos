package com.recre.app.feature.recaudacion.denominaciones

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.recre.app.core.calculo.DENOMINACIONES_PERMITIDAS
import com.recre.app.core.calculo.DenominacionItem
import com.recre.app.core.calculo.importesIguales
import com.recre.app.feature.recaudacion.RecaudacionFlowViewModel
import com.recre.app.feature.recaudacion.RecaudacionTestTags
import com.recre.app.feature.recaudacion.components.BaselineCambiadaDialog
import com.recre.app.ui.components.Banda
import com.recre.app.ui.components.BandaTono
import com.recre.app.ui.components.Keypad
import com.recre.app.ui.components.LottieIllustration
import com.recre.app.ui.components.OdometroText
import com.recre.app.ui.components.PasoTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.components.StatusChip
import com.recre.app.ui.components.StatusRole
import com.recre.app.ui.components.formatEur
import com.recre.app.ui.components.successFlash
import com.recre.app.ui.theme.RecreType
import java.math.BigDecimal
import java.math.RoundingMode

// Pantalla de extracto de denominaciones (T-55 · rediseño T-232, F2 · neón N7).
// SSOT del componente: .kiro/specs/recre/fase3-component-specs.md
// (§ Sistema keypad de denominaciones · C-KEYPAD-DENOM-AND).
//
// Regiones de arriba a abajo: (R1) TopBar con confirmación de descarte; (R2, solo
// Local) Banda de deuda con el texto de retención; (R3) LISTA vertical de
// denominaciones (billetes arriba, monedas abajo) con fila readonly dirigida por
// el keypad y auto-scroll a la fila activa; (R4) TotalSticky (odómetro + objetivo
// + chip estado) ENCIMA del keypad; (R5) átomo Keypad anclado abajo, que lleva el
// avance en la tecla ok (D.3-3). El cálculo económico definitivo es SSOT servidor:
// aquí solo se capturan cantidades enteras y se valida suma == objetivo.
//
// Reutilizada en dos modos: Total (objetivo = bruto) y Local (objetivo =
// pagado_local = parte_local − recuperado). La reordenación de imputación de
// deuda ya NO se edita aquí (R2 es solo lectura): se movió a un paso previo.
//
// Adaptación al stack: la fila de cantidad es un destino tappable (no un
// TextField), de modo que el IME del sistema JAMÁS puede aparecer (anti-patrón
// nº1 del spec); el keypad in-app es la única entrada. El resaltado de fila activa
// (borde primary de AppCard) hace de cursor.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DenominacionesScreen(
    viewModel: RecaudacionFlowViewModel,
    modo: ModoDenominaciones,
    onContinuar: () -> Unit,
    onBack: () -> Unit,
    onRehacer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cifras = state.cifras

    BaselineCambiadaDialog(
        visible = state.baselineCambiada && !state.avisoBaselineVisto,
        onMarcarVisto = viewModel::marcarAvisoBaselineVisto,
        onRehacer = onRehacer,
    )
    val target: BigDecimal? =
        when (modo) {
            ModoDenominaciones.Total -> cifras?.bruto
            ModoDenominaciones.Local -> state.recuperacion?.pagadoLocal ?: cifras?.parteLocal
        }
    val map =
        when (modo) {
            ModoDenominaciones.Total -> state.denominacionesTotal
            ModoDenominaciones.Local -> state.denominacionesLocal
        }
    val totalActual = viewModel.sumarDesgloseDe(map)
    val diferencia = target?.subtract(totalActual)?.setScale(2, RoundingMode.HALF_UP)
    val cuadra = target != null && diferencia != null && importesIguales(diferencia, BigDecimal.ZERO)
    val nadaQueEntregar =
        modo == ModoDenominaciones.Local && target != null && importesIguales(target, BigDecimal.ZERO)
    val huboRecuperacion = (state.recuperacion?.recuperadoTotal?.signum() ?: 0) > 0
    val hayPiezas = map.values.any { it > 0 }

    // Orden del mockup: billetes arriba (50,20,10,5), luego monedas (2€…10c). Es
    // DENOMINACIONES_PERMITIDAS (ascendente) invertida.
    val orden = remember { DENOMINACIONES_PERMITIDAS.map { it.toPlainString() }.reversed() }

    // Estado de UI local: fila activa (la dirige el keypad) y diálogo de descarte.
    var activeKey by rememberSaveable(modo) {
        mutableStateOf(orden.firstOrNull())
    }
    var showDiscard by remember { mutableStateOf(false) }

    fun cambiarCantidad(key: String, nueva: Int) {
        when (modo) {
            ModoDenominaciones.Total -> viewModel.onDenominacionTotalChange(key, nueva)
            ModoDenominaciones.Local -> viewModel.onDenominacionLocalChange(key, nueva)
        }
    }

    fun intentarSalir() {
        if (hayPiezas) showDiscard = true else onBack()
    }

    // Back de hardware: misma confirmación que la flecha (no perder el conteo).
    BackHandler(enabled = hayPiezas) { showDiscard = true }

    val tituloPaso =
        when (modo) {
            ModoDenominaciones.Total -> stringResource(R.string.recaudacion_denominaciones_total_titulo)
            ModoDenominaciones.Local -> stringResource(R.string.recaudacion_denominaciones_local_titulo)
        }
    Scaffold(
        topBar = {
            PasoTopBar(
                titulo = tituloPaso,
                pasoActual = 2,
                onBack = ::intentarSalir,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // (R2) Recuperación: banda de deuda con el texto de retención. El
            // desglose íntegro por deuda se ve en Confirmación; aquí basta recordar
            // cuánto se retiene de la parte local y cuánto se lleva el local.
            val plan = state.recuperacion
            if (modo == ModoDenominaciones.Local && plan != null && plan.recuperadoTotal.signum() > 0) {
                BandaDeuda(
                    porcentaje = state.porcentajeRecuperacion,
                    pagadoLocal = plan.pagadoLocal,
                    parteLocal = cifras?.parteLocal ?: plan.pagadoLocal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (nadaQueEntregar) {
                // Estado vacío con animación Lottie (antes era texto plano).
                NadaQueEntregar(
                    huboRecuperacion = huboRecuperacion,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // (R3) Lista vertical de denominaciones; la fila seleccionada (borde
                // petróleo) dirige el keypad. Auto-scroll a la fila activa.
                ListaDenominaciones(
                    orden = orden,
                    cantidades = map,
                    activeKey = activeKey,
                    onSelect = { activeKey = it },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                )
                // (R4) TotalSticky: odómetro + objetivo + chip estado, ENCIMA del keypad.
                TotalSticky(
                    objetivo = target,
                    total = totalActual,
                    diferencia = diferencia,
                    cuadra = cuadra,
                )
            }

            if (nadaQueEntregar) {
                // Sin piezas no hay keypad donde poner el «ok»: un único CTA al pie.
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                        RecrePrimaryButton(
                            text = stringResource(R.string.recaudacion_accion_continuar),
                            onClick = onContinuar,
                            fullWidth = true,
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR),
                        )
                    }
                }
            } else {
                // (R5) Keypad anclado abajo. El avance vive en la tecla ok (D.3-3):
                // «Continuar» cuando cuadra (o no hay nada que entregar) → onContinuar;
                // si no, «Siguiente denominación» → salta a la siguiente fila.
                Keypad(
                    onDigit = { d ->
                        activeKey?.let { k ->
                            val actual = map[k] ?: 0
                            // Concatena el dígito a la derecha; tope 6 cifras (money-safe: solo enteros).
                            val nuevo = (actual.toLong() * 10 + d).coerceAtMost(999_999L).toInt()
                            cambiarCantidad(k, nuevo)
                        }
                    },
                    onBackspace = {
                        activeKey?.let { k -> cambiarCantidad(k, (map[k] ?: 0) / 10) }
                    },
                    onNext = {
                        if (cuadra || nadaQueEntregar) {
                            onContinuar()
                        } else {
                            activeKey = siguienteDenominacion(activeKey, orden)
                        }
                    },
                    okLabel =
                        if (cuadra || nadaQueEntregar) {
                            stringResource(R.string.recaudacion_accion_continuar)
                        } else {
                            stringResource(R.string.recaudacion_keypad_siguiente_denominacion)
                        },
                    backspaceContentDescription = stringResource(R.string.recaudacion_keypad_borrar),
                    nextContentDescription =
                        stringResource(R.string.recaudacion_keypad_siguiente_denominacion),
                    modifier = Modifier.testTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR),
                )
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.recaudacion_denominaciones_descartar_titulo)) },
            text = { Text(stringResource(R.string.recaudacion_denominaciones_descartar_mensaje)) },
            confirmButton = {
                RecreTextButton(
                    text = stringResource(R.string.recaudacion_denominaciones_descartar_confirmar),
                    onClick = {
                        showDiscard = false
                        onBack()
                    },
                )
            },
            dismissButton = {
                RecreTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showDiscard = false },
                )
            },
        )
    }
}

/**
 * (R3) Lista vertical de denominaciones en el orden del mockup (billetes arriba,
 * monedas abajo). Cada fila es una [DenominacionCard] con el subtotal formateado;
 * la fila seleccionada dirige el keypad. Auto-scroll a la fila activa al cambiarla.
 */
@Composable
private fun ListaDenominaciones(
    orden: List<String>,
    cantidades: Map<String, Int>,
    activeKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeKey) {
        val i = orden.indexOf(activeKey)
        if (i >= 0) listState.animateScrollToItem(i)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(orden, key = { it }) { key ->
            val cantidad = cantidades[key] ?: 0
            val subtotal =
                formatEur(DenominacionItem(BigDecimal(key), cantidad).subtotal.toPlainString())
            DenominacionCard(
                key = key,
                cantidad = cantidad,
                subtotal = subtotal,
                selected = key == activeKey,
                onSelect = { onSelect(key) },
            )
        }
    }
}

/**
 * (R4) Total-sticky ENCIMA del keypad: odómetro del total + «Objetivo · X €» a la
 * izquierda, chip de estado a la derecha. Flash verde al cuadrar. Separado del
 * keypad por un divisor superior.
 */
@Composable
private fun TotalSticky(
    objetivo: BigDecimal?,
    total: BigDecimal,
    diferencia: BigDecimal?,
    cuadra: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .successFlash(trigger = if (cuadra) true else null)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    OdometroText(
                        texto = formatEur(total),
                        style = RecreType.importeMedium,
                    )
                    if (objetivo != null) {
                        Text(
                            text =
                                stringResource(
                                    R.string.recaudacion_denominaciones_objetivo_sticky,
                                    formatEur(objetivo),
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (objetivo != null && diferencia != null) {
                    EstadoChip(diferencia = diferencia, cuadra = cuadra)
                }
            }
        }
    }
}

/**
 * Estado vacío de la parte local: no hay nada que entregar (parte_local = 0, con
 * o sin deuda recuperada). Animación Lottie + el texto explicativo, en lugar de
 * texto plano. Si el asset no carga, queda el texto (la animación es decorativa).
 */
@Composable
private fun NadaQueEntregar(
    huboRecuperacion: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LottieIllustration(
            rawRes = R.raw.recaudacion_sin_parte,
            modifier = Modifier.size(140.dp),
            iterations = 1,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text =
                stringResource(
                    if (huboRecuperacion) {
                        R.string.recaudacion_local_nada_deuda
                    } else {
                        R.string.recaudacion_local_nada
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Chip de estado del cuadre: ✓ Cuadra (success) o ⚠ Faltan/Sobran X € (warning, ámbar — no rojo). */
@Composable
private fun EstadoChip(diferencia: BigDecimal, cuadra: Boolean) {
    if (cuadra) {
        StatusChip(
            role = StatusRole.SUCCESS,
            label = stringResource(R.string.recaudacion_denominaciones_cuadra),
            icon = Icons.Filled.Check,
            modifier = Modifier.testTag(RecaudacionTestTags.DENOMINACIONES_DIFERENCIA),
        )
    } else {
        // diferencia = objetivo − total: >0 ⇒ faltan; <0 ⇒ sobran.
        val faltan = diferencia.signum() > 0
        val importe = formatEur(diferencia.abs().toPlainString())
        StatusChip(
            role = StatusRole.WARNING,
            label =
                stringResource(
                    if (faltan) {
                        R.string.recaudacion_denominaciones_faltan
                    } else {
                        R.string.recaudacion_denominaciones_sobran
                    },
                    importe,
                ),
            icon = if (faltan) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            modifier = Modifier.testTag(RecaudacionTestTags.DENOMINACIONES_DIFERENCIA),
        )
    }
}

/** Siguiente denominación en el orden dado (clamp en la última); null-safe sobre la activa. */
private fun siguienteDenominacion(activa: String?, orden: List<String>): String? {
    val i = orden.indexOf(activa)
    return when {
        i < 0 -> orden.firstOrNull()
        i >= orden.lastIndex -> orden.lastOrNull()
        else -> orden[i + 1]
    }
}

/**
 * (R2) Banda de deuda (D.3-2): recuerda cuánto se retiene de la parte del local
 * para saldar su deuda. Texto rico con las cifras en negrita. El reparto por deuda
 * se ve en Confirmación; esta banda sustituye al banner compacto anterior.
 */
@Composable
private fun BandaDeuda(
    porcentaje: Int,
    pagadoLocal: BigDecimal,
    parteLocal: BigDecimal,
    modifier: Modifier = Modifier,
) {
    val pagadoStr = formatEur(pagadoLocal)
    val parteStr = formatEur(parteLocal)
    val plantilla =
        stringResource(R.string.recaudacion_denominaciones_banda_deuda, porcentaje, pagadoStr, parteStr)
    // Resalta en negrita las cifras (pagado/parte) dentro del texto plano.
    val texto =
        buildAnnotatedString {
            var resto = plantilla
            for (cifra in listOf(pagadoStr, parteStr)) {
                val idx = resto.indexOf(cifra)
                if (idx < 0) {
                    append(resto)
                    resto = ""
                    break
                }
                append(resto.substring(0, idx))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(cifra) }
                resto = resto.substring(idx + cifra.length)
            }
            if (resto.isNotEmpty()) append(resto)
        }
    Banda(
        texto = texto,
        icon = Icons.Filled.Warning,
        tono = BandaTono.DEUDA,
        modifier = modifier,
    )
}

enum class ModoDenominaciones {
    Total,
    Local,
}
