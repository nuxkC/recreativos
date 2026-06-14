package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreTheme

// =====================================================================
// SegmentedControl · C-SEGMENTED-CONTROL — átomo del design system
// "Confianza Industrial".
//
// Conmutador binario o ternario de MODO sobre un mismo conjunto de datos
// (NO una acción, NO un filtro multiselección, NO Tabs de navegación).
// Selección EXCLUSIVA: siempre hay UNA opción activa → selectableGroup +
// Role.RadioButton (radiogroup). Usos canónicos: Total/Local en el keypad
// de denominaciones; Gráfica/Tabla en Informes.
//
// Anatomía: track NEUTRO (surface-2) que envuelve N segmentos de ancho
// igual; un thumb tonal (secondary container) se desliza bajo el segmento
// activo. El estado nunca es solo-color: además del thumb tonal, el label
// activo va en foreground con peso 600 (regla 2: daltonismo). Borde del
// track sólo en light (muted ≥3:1 sobre surface-1, WCAG 1.4.11).
//
// NO usar success/danger/warning aquí: no comunica dinero, error ni
// pendiente; el inactivo es 'muted', NUNCA 'secondary'. Máx 3 opciones
// (con >3 los segmentos bajan de 48dp o se truncan los labels → usar
// Select/Tabs scrollables).
//
// SSOT: .kiro/specs/recre/fase3-component-specs.md (SegmentedControl).
// =====================================================================

/**
 * Una opción del conmutador. [label] es texto de UI ya resuelto por el
 * llamador (stringResource); aquí NO se hardcodea. [icon] opcional decorativo
 * (el label ya nombra el modo).
 */
data class SegmentOption(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
)

/**
 * Conmutador de modo de 2-3 opciones con thumb deslizante.
 *
 * @param options 2-3 opciones (modo, no acción). Con 1 no tiene sentido; con
 *   >3 los labels se truncan → usar Select/Tabs.
 * @param selectedIndex índice de la opción activa (selección exclusiva). El
 *   estado vive fuera (el llamador); este átomo es controlado.
 * @param onSelect callback con el índice elegido. No se invoca al re-tocar el
 *   segmento ya activo.
 * @param groupLabel etiqueta del grupo para a11y (p.ej. "Objetivo del conteo"),
 *   resuelta por el llamador vía stringResource. Anuncia el propósito del
 *   radiogroup; NO se pinta.
 * @param enabled si false, todo el control se atenúa (≈0.5) y no recibe foco
 *   ni toques. Caso raro: no hay datos que conmutar o el modo está bloqueado.
 * @param modifier modificador externo (último parámetro, convención del repo).
 */
@Composable
fun SegmentedControl(
    options: List<SegmentOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    groupLabel: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return

    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val isLight = !androidx.compose.foundation.isSystemInDarkTheme()
    val reducedMotion = rememberSegmentReducedMotion()

    // Índice activo saneado: nunca fuera de rango (el estado es del llamador).
    val activeIndex = selectedIndex.coerceIn(0, options.size - 1)

    // Borde del track SOLO en light: muted (onSurfaceVariant) llega a ≥3:1
    // sobre surface-1 (WCAG 1.4.11); 'border'/outlineVariant (#E3E6EA) da
    // 1.25:1 y es invisible. En dark el delta surface-2/surface-1 ya separa.
    val trackBorder =
        if (isLight) {
            Modifier.border(1.dp, cs.onSurfaceVariant, RoundedCornerShape(50))
        } else {
            Modifier
        }

    BoxWithConstraints(
        modifier =
            modifier
                // Padding del track eleva el target visual de 36dp a ≥48dp por
                // segmento sin fijar height pequeño en el clickable.
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(cs.surfaceVariant) // track NEUTRO surface-2, no acento
                .then(trackBorder)
                .padding(4.dp)
                // El radiogroup se anuncia con su propósito; selectableGroup hace
                // que el lector trate los segmentos como un grupo de selección única.
                .semantics { contentDescription = groupLabel }
                .selectableGroup()
                .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.CenterStart,
    ) {
        val segmentWidth = maxWidth / options.size

        // Thumb deslizante: anima translateX; reduced-motion → salto instantáneo
        // (el estado nunca se pierde, sólo desaparece el desliz).
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * activeIndex,
            animationSpec =
                if (reducedMotion) {
                    snap()
                } else {
                    tween(durationMillis = 160, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                },
            label = "segmentThumbOffset",
        )

        // Thumb tonal (secondary container). Sólo se pinta si el control está
        // activo: en disabled el carril queda neutro, sin acento.
        if (enabled) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.secondaryContainer),
            )
        }

        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                val isActive = i == activeIndex
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(
                                selected = isActive,
                                enabled = enabled,
                                role =
                                    androidx.compose.ui.semantics.Role.RadioButton,
                                indication = ripple(color = cs.primary),
                                interactionSource =
                                    remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                onClick = {
                                    if (!isActive) {
                                        // Tick háptico de confirmación al cambiar de modo.
                                        // SegmentTick no existe en este BOM → LongPress.
                                        haptics.performHapticFeedback(
                                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                                        )
                                        onSelect(i)
                                    }
                                },
                            )
                            .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SegmentLabel(
                        option = option,
                        isActive = isActive,
                        activeColor = cs.onSecondaryContainer, // foreground sobre thumb (≥7:1)
                        inactiveColor = cs.onSurfaceVariant, // muted sobre track (AA)
                    )
                }
            }
        }
    }
}

/**
 * Contenido de un segmento: icono opcional (decorativo) + label. El activo va
 * en peso 600 y foreground; el inactivo en muted con peso normal. mergeDescendants
 * funde icono+texto en un único nodo (sin doble anuncio); el icono no aporta
 * etiqueta porque el label ya nombra el modo.
 */
@Composable
private fun SegmentLabel(
    option: SegmentOption,
    isActive: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    inactiveColor: androidx.compose.ui.graphics.Color,
) {
    val color = if (isActive) activeColor else inactiveColor
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        option.icon?.let { icon ->
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null, // decorativo: el label ya nombra el modo
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelLarge,
            // Activo en negrita: el estado no depende solo del color (regla 2).
            fontWeight = if (isActive) FontWeight.W600 else FontWeight.W500,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Señal REAL de reduced-motion en Android: el usuario ha reducido/quitado las
 * animaciones del sistema → ANIMATOR_DURATION_SCALE == 0. Apaga el desliz del
 * thumb (salta instantáneo, sin perder el estado). En @Preview/inspección no
 * hay ajuste fiable → se asume movimiento permitido.
 */
@Composable
private fun rememberSegmentReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        val scale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        scale == 0f
    }
}

// ---------------------------------------------------------------------
// Previews — light y dark. 2 opciones (Total/Local), 3 opciones con icono
// (Gráfica/Tabla), y estado disabled.
// ---------------------------------------------------------------------

@Composable
private fun SegmentedControlShowcase() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
    ) {
        var binario by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        SegmentedControl(
            options = listOf(SegmentOption("Total"), SegmentOption("Local")),
            selectedIndex = binario,
            onSelect = { binario = it },
            groupLabel = "Objetivo del conteo",
            modifier = Modifier.fillMaxWidth(),
        )

        var ternario by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(1) }
        SegmentedControl(
            options =
                listOf(
                    SegmentOption("Gráfica", Icons.Filled.BarChart),
                    SegmentOption("Tabla", Icons.Filled.TableChart),
                    SegmentOption("Mapa"),
                ),
            selectedIndex = ternario,
            onSelect = { ternario = it },
            groupLabel = "Representación del informe",
            modifier = Modifier.fillMaxWidth(),
        )

        SegmentedControl(
            options = listOf(SegmentOption("Total"), SegmentOption("Local")),
            selectedIndex = 0,
            onSelect = {},
            groupLabel = "Objetivo del conteo",
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "SegmentedControl · Light", showBackground = true, widthDp = 360)
@Composable
private fun SegmentedControlLightPreview() {
    RecreTheme(darkTheme = false) { SegmentedControlShowcase() }
}

@Preview(name = "SegmentedControl · Dark", showBackground = true, widthDp = 360)
@Composable
private fun SegmentedControlDarkPreview() {
    RecreTheme(darkTheme = true) { SegmentedControlShowcase() }
}
