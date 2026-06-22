package com.recre.app.feature.recaudacion.denominaciones

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.calculo.DENOMINACIONES_PERMITIDAS
import com.recre.app.core.calculo.importesIguales
import com.recre.app.ui.components.CountUpText
import com.recre.app.ui.components.successFlash
import com.recre.app.feature.recaudacion.RecaudacionFlowViewModel
import com.recre.app.feature.recaudacion.RecaudacionTestTags
import com.recre.app.feature.recaudacion.components.BaselineCambiadaDialog
import com.recre.app.ui.components.Keypad
import com.recre.app.ui.components.MoneyText
import com.recre.app.ui.components.MoneyTextSize
import com.recre.app.ui.components.PasoTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.components.StatusChip
import com.recre.app.ui.components.StatusRole
import com.recre.app.ui.components.formatEur
import com.recre.app.ui.theme.RecreShapes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import java.math.BigDecimal
import java.math.RoundingMode

// Pantalla de extracto de denominaciones (T-55 · rediseño T-232, F2).
// SSOT del componente: .kiro/specs/recre/fase3-component-specs.md
// (§ Sistema keypad de denominaciones · C-KEYPAD-DENOM-AND).
//
// 5 regiones de arriba a abajo: (R1) TopBar con confirmación de descarte; (R2,
// solo Local) RecuperacionResumenCard fija solo-lectura; (R3) lista de
// denominaciones agrupada Billetes/Monedas con celda readonly dirigida por el
// keypad y auto-scroll a la fila activa; (R4) bloque de progreso STICKY
// (Objetivo/Total/estado + CTA) que nunca queda tapado; (R5) átomo Keypad
// anclado abajo. El cálculo económico definitivo es SSOT servidor: aquí solo se
// capturan cantidades enteras y se valida suma == objetivo (importesIguales).
//
// Reutilizada en dos modos: Total (objetivo = bruto) y Local (objetivo =
// pagado_local = parte_local − recuperado). La reordenación de imputación de
// deuda ya NO se edita aquí (R2 es solo lectura): se movió a un paso previo.
//
// Adaptación al stack: la celda de cantidad es un Box tappable (no un TextField),
// de modo que el IME del sistema JAMÁS puede aparecer (anti-patrón nº1 del spec);
// el keypad in-app es la única entrada. El resaltado de fila activa
// (secondaryContainer + anillo primary) hace de cursor.

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

    // Estado de UI local: fila activa (la dirige el keypad) y diálogo de descarte.
    var activeKey by rememberSaveable(modo) {
        mutableStateOf(DENOMINACIONES_PERMITIDAS.firstOrNull()?.toPlainString())
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
    val subtituloObjetivo =
        target?.let {
            stringResource(R.string.recaudacion_denominaciones_objetivo, formatEur(it.toPlainString()))
        }

    Scaffold(
        topBar = {
            PasoTopBar(
                titulo = tituloPaso,
                pasoActual = 2,
                onBack = ::intentarSalir,
                subtitulo = subtituloObjetivo,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // (R2) Recuperación: banner COMPACTO fijo. La card completa (con el
            // detalle por deuda) desbordaba este Column sin scroll y empujaba la
            // rejilla + keypad fuera de pantalla; el desglose íntegro se ve en
            // Confirmación. Aquí basta recordar cuánto se retiene de la parte local.
            val plan = state.recuperacion
            if (modo == ModoDenominaciones.Local && plan != null && plan.recuperadoTotal.signum() > 0) {
                RecuperacionBannerCompacto(
                    recuperadoTotal = plan.recuperadoTotal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (nadaQueEntregar) {
                // "Nada que entregar": ni lista ni keypad; solo el texto y Continuar.
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
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
                    )
                }
            } else {
                // (R3) Rejilla 3×3 sin scroll: monedas arriba, billetes abajo en orden
                // ascendente. La tarjeta seleccionada (borde petróleo) dirige el keypad.
                RejillaDenominaciones(
                    cantidades = map,
                    activeKey = activeKey,
                    onSelect = { activeKey = it },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // (R4) Bloque de progreso STICKY: nunca tapado por el keypad.
            BloqueProgreso(
                objetivo = if (nadaQueEntregar) null else target,
                total = totalActual,
                diferencia = if (nadaQueEntregar) null else diferencia,
                cuadra = cuadra,
                puedeContinuar = cuadra || nadaQueEntregar,
                onContinuar = onContinuar,
            )

            // (R5) Keypad anclado abajo (oculto si no hay nada que contar).
            if (!nadaQueEntregar) {
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
                    onNext = { activeKey = siguienteDenominacion(activeKey) },
                    backspaceContentDescription = stringResource(R.string.recaudacion_keypad_borrar),
                    nextContentDescription =
                        stringResource(R.string.recaudacion_keypad_siguiente_denominacion),
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
 * (R3) Rejilla 3×3 de denominaciones, en orden ascendente (monedas arriba, billetes
 * abajo). Sin scroll: las filas se reparten el alto disponible (weight) para que las 9
 * tarjetas entren siempre. La tarjeta seleccionada dirige el keypad.
 */
@Composable
private fun RejillaDenominaciones(
    cantidades: Map<String, Int>,
    activeKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DENOMINACIONES_PERMITIDAS.chunked(3).forEach { fila ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                fila.forEach { den ->
                    val key = den.toPlainString()
                    DenominacionCard(
                        etiqueta = etiquetaFacialDenominacion(key),
                        cantidad = cantidades[key] ?: 0,
                        selected = key == activeKey,
                        onSelect = { onSelect(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * (R4) Bloque de progreso persistente: Objetivo, Total (héroe) + estado
 * (Cuadra/Faltan/Sobran con icono+texto+color) y CTA Continuar. Sticky sobre el
 * keypad (vive fuera del LazyColumn), separado por divisor outline para que el
 * límite se vea.
 */
@Composable
private fun BloqueProgreso(
    objetivo: BigDecimal?,
    total: BigDecimal,
    diferencia: BigDecimal?,
    cuadra: Boolean,
    puedeContinuar: Boolean,
    onContinuar: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .successFlash(trigger = if (cuadra) true else null)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (objetivo != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recaudacion_denominaciones_objetivo, ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MoneyText(amount = objetivo, size = MoneyTextSize.Medium)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.recaudacion_denominaciones_total),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CountUpText(
                            importe = total.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                            size = MoneyTextSize.Hero,
                        )
                    }
                    if (objetivo != null && diferencia != null) {
                        EstadoChip(diferencia = diferencia, cuadra = cuadra)
                    }
                }
                RecrePrimaryButton(
                    text = stringResource(R.string.recaudacion_accion_continuar),
                    onClick = onContinuar,
                    enabled = puedeContinuar,
                    fullWidth = true,
                    modifier = Modifier.testTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR),
                )
            }
        }
    }
}

/** Chip de estado del cuadre: ✓ Cuadra (success) o ⚠ Faltan/Sobran X € (danger). */
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
            role = StatusRole.DANGER,
            label =
                stringResource(
                    if (faltan) {
                        R.string.recaudacion_denominaciones_faltan
                    } else {
                        R.string.recaudacion_denominaciones_sobran
                    },
                    importe,
                ),
            icon = Icons.Outlined.Warning,
            modifier = Modifier.testTag(RecaudacionTestTags.DENOMINACIONES_DIFERENCIA),
        )
    }
}

/** Siguiente denominación (clamp en la última); null-safe sobre la activa. */
private fun siguienteDenominacion(activa: String?): String? {
    val claves = DENOMINACIONES_PERMITIDAS.map { it.toPlainString() }
    val i = claves.indexOf(activa)
    return when {
        i < 0 -> claves.firstOrNull()
        i >= claves.lastIndex -> claves.lastOrNull()
        else -> claves[i + 1]
    }
}

/**
 * (R2) Banner compacto de recuperación de deuda para la pantalla de
 * denominaciones (sin scroll). Solo recuerda cuánto se retiene de la parte del
 * local; el reparto por deuda se ve en Confirmación. Ocupa ~64dp en vez de los
 * ~220dp de [com.recre.app.feature.recaudacion.components.RecuperacionResumenCard],
 * de modo que la rejilla y el keypad caben en pantalla.
 */
@Composable
private fun RecuperacionBannerCompacto(
    recuperadoTotal: BigDecimal,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RecreShapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.recaudacion_recuperacion_titulo),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.recaudacion_recuperacion_compacta_sub),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "− " + formatEur(recuperadoTotal.toPlainString()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

enum class ModoDenominaciones {
    Total,
    Local,
}
