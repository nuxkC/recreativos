package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme
import com.recre.app.ui.theme.RecreType

// Design System "Confianza Industrial" — atom F3-FilterChip (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// Chip TOGGLE de filtro de listado (multi-selección): es CONTROL, no indicador,
// por eso es interactivo, focusable y se anuncia como Role.Switch (aria-pressed).
// CLAVE: es DISTINTO de StatusChip — NO usa roles de estado (success/warning/
// danger/info). En reposo es NEUTRO (state-neutral: surface-2 + border 1px +
// texto foreground / icono muted); seleccionado adopta la MARCA primary en modo
// OUTLINE 1.5px + texto primary + tinte primary MUY tenue opaco + icono Check.
// Así un filtro "Avería" activo se ve "marca activa", no "alarma roja": el rojo
// lo lleva el StatusChip de la fila, jamás el filtro.
//
// Adaptaciones al stack real:
//  - El spec pide Phosphor `Check`; esa lib NO está. Se usa `Icons.Filled.Check`
//    (material-icons core), decorativo (el estado lo porta también el borde grueso).
//  - El token opaco "primary-selected" NO existe en RecreColors: aquí se DERIVA
//    determinista componiendo primary@10% (light) / @16% (dark) SOBRE surface-1
//    (compositeOver → color OPACO, sin hex crudo y sin depender de la fila/hover,
//    como exige el spec: "opaco, no alpha sobre transparent").
//  - reduce-motion real vía ANIMATOR_DURATION_SCALE (mismo criterio que Skeleton):
//    apaga el expand/colapso del Check; el estado sigue legible sin movimiento.

/** Modelo de un chip de la barra de filtros. */
data class FilterChipModel(
    val key: String,
    val label: String,
    val leadingIcon: ImageVector? = null,
    val count: Int? = null,
)

private const val TOGGLE_ANIM_MS = 150
private const val SELECTED_FILL_ALPHA_LIGHT = 0.10f
private const val SELECTED_FILL_ALPHA_DARK = 0.16f

/**
 * Chip de filtro toggle, neutro en reposo y de marca (primary) al seleccionar.
 *
 * El estado "activo" no se confía solo al color: lo refuerzan el icono Check
 * (forma) Y el borde 1.5px más grueso (grosor) — distinguible sin percepción de
 * color. Hit-area real ≥48dp (minimumInteractiveComponentSize) aunque la pill
 * mida 36dp. Semántica Role.Switch con `value` = seleccionado (TalkBack:
 * "activado/desactivado").
 *
 * @param label texto del criterio ("Activa", "Solo descuadres"). i18n; no trunca.
 * @param selected si el filtro está activo.
 * @param onToggle callback con el nuevo valor al alternar.
 * @param leadingIcon glifo opcional del criterio (decorativo; muted/primary).
 * @param count sufijo numérico "· 12" (Geist Mono tabular, muted); null = sin sufijo.
 * @param enabled false ⇒ conjunto al 50% y no interactivo.
 * @param modifier modificador del chip (último parámetro).
 */
@Composable
fun RecreFilterChip(
    label: String,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    leadingIcon: ImageVector? = null,
    count: Int? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    val reduceMotion = !rememberAnimationsEnabled()

    val primary = MaterialTheme.colorScheme.primary
    val surface1 = MaterialTheme.colorScheme.surface
    // Tinte de marca tenue OPACO: primary@10/16% compuesto sobre surface-1. Opaco
    // (compositeOver), no alpha sobre transparent: el contraste no depende de la fila.
    val selectedFill =
        primary
            .copy(alpha = if (colors.isLight) SELECTED_FILL_ALPHA_LIGHT else SELECTED_FILL_ALPHA_DARK)
            .compositeOver(surface1)

    val bg = if (selected) selectedFill else colors.surface2
    val borderColor = if (selected) primary else colors.border
    val borderWidth = if (selected) 1.5.dp else 1.dp
    val contentColor = if (selected) primary else MaterialTheme.colorScheme.onSurface
    val iconTint = if (selected) primary else colors.muted
    val shape = RoundedCornerShape(percent = 50) // pill full

    Row(
        modifier =
            modifier
                .height(36.dp)
                .clip(shape)
                .background(bg)
                .border(borderWidth, borderColor, shape)
                // CONTROL: hit-area ≥48dp sin agrandar el visual + semántica toggle.
                .minimumInteractiveComponentSize()
                .toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onToggle,
                )
                .padding(start = if (selected) 10.dp else 14.dp, end = 14.dp)
                .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Check: portador NO cromático del estado activo (entra/colapsa en horizontal).
        AnimatedVisibility(
            visible = selected,
            enter =
                if (reduceMotion) {
                    EnterTransition.None
                } else {
                    expandHorizontally(animationSpec = tween(TOGGLE_ANIM_MS)) +
                        fadeIn(tween(TOGGLE_ANIM_MS))
                },
            exit =
                if (reduceMotion) {
                    ExitTransition.None
                } else {
                    shrinkHorizontally(animationSpec = tween(TOGGLE_ANIM_MS)) +
                        fadeOut(tween(TOGGLE_ANIM_MS))
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(16.dp),
            )
        }
        leadingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge, // 14/600, no mono (no es cifra)
            color = contentColor,
            maxLines = 1,
        )
        count?.let {
            Text(
                text = "· $it",
                style = RecreType.cifraCaption, // Geist Mono tabular: es cifra
                color = colors.muted,
                maxLines = 1,
            )
        }
    }
}

/**
 * Barra horizontal scrollable de [RecreFilterChip] (multi-selección: varios
 * activos a la vez). Una sola línea que se desliza, sin wrap. Al final, botón de
 * texto "Limpiar filtros" SOLO si hay ≥1 chip activo.
 *
 * @param chips modelos de chip (clave estable para el scroll/recomposición).
 * @param selectedKeys claves de los chips activos.
 * @param onToggle (clave, nuevoValor) al alternar un chip.
 * @param onClear limpia todos los filtros.
 * @param clearLabel texto del botón "Limpiar filtros" (i18n).
 * @param modifier modificador de la barra (último parámetro).
 */
@Composable
fun FilterChipRow(
    chips: List<FilterChipModel>,
    selectedKeys: Set<String>,
    onToggle: (key: String, now: Boolean) -> Unit,
    onClear: () -> Unit,
    clearLabel: String,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(chips, key = { it.key }) { m ->
            RecreFilterChip(
                label = m.label,
                selected = m.key in selectedKeys,
                onToggle = { now -> onToggle(m.key, now) },
                leadingIcon = m.leadingIcon,
                count = m.count,
            )
        }
        if (selectedKeys.isNotEmpty()) {
            item {
                RecreTextButton(text = clearLabel, onClick = onClear) // Botón Texto C-01
            }
        }
    }
}

/**
 * ¿Animaciones activas? Falso en preview o con animaciones del sistema
 * desactivadas (ANIMATOR_DURATION_SCALE = 0). (Función privada por fichero.)
 */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        val scale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        scale != 0f
    }
}

// ---------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------

@Preview(name = "FilterChip · light", showBackground = true)
@Composable
private fun FilterChipLightPreview() {
    RecreTheme(darkTheme = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecreFilterChip(label = "Activa", selected = true, onToggle = {}, count = 12)
            RecreFilterChip(label = "Pendiente firma", selected = false, onToggle = {})
            RecreFilterChip(label = "Solo descuadres", selected = false, onToggle = {})
        }
    }
}

@Preview(name = "FilterChipRow · dark", showBackground = true)
@Composable
private fun FilterChipRowDarkPreview() {
    RecreTheme(darkTheme = true) {
        FilterChipRow(
            chips =
                listOf(
                    FilterChipModel(key = "activa", label = "Activa", count = 12),
                    FilterChipModel(key = "pendiente", label = "Pendiente firma"),
                    FilterChipModel(key = "descuadre", label = "Solo descuadres"),
                    FilterChipModel(key = "andalucia", label = "Andalucía"),
                ),
            selectedKeys = setOf("activa"),
            onToggle = { _, _ -> },
            onClear = {},
            clearLabel = "Limpiar filtros",
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}
