package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// Design System "Confianza Industrial" — atom collapsible-header (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// Divulgación progresiva: una cabecera pulsable (toda la fila es el control) que
// expande/colapsa una región de contenido. El chevron rota 0°→180° y el cuerpo
// crece/decrece animado, respetando reduce-motion. NO captura datos (la cabecera
// es solo-lectura); para chip de estado o importe de contexto reutiliza
// StatusChip / MoneyText vía los slots [leading]/[trailing] — no se recolorea la
// cabecera por estado (eso lo lleva el StatusChip).
//
// Adaptaciones al stack real:
//  - El spec pide Phosphor `CaretDown`; esa lib NO está. Se usa el equivalente
//    material-icons `Icons.Filled.KeyboardArrowDown` (rota 0°→180° al expandir).
//  - Motion self-contained: tween 150ms ease.standard (0.2,0,0,1), o snap (0ms)
//    con reduce-motion real (ANIMATOR_DURATION_SCALE = 0). No depende de un
//    LocalRecreMotion externo.
//  - Estado uncontrolled (rememberSaveable, sobrevive a rotación) con callback
//    opcional [onExpandedChange] para que el llamador reaccione si lo necesita.

// ease.standard del proyecto: cubic-bezier(0.2, 0, 0, 1). Sin overshoot/spring.
private val CollapseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val COLLAPSE_DURATION_MS = 150

/**
 * Cabecera plegable: toda la fila es el control (un único nodo accesible).
 *
 * El estado expandido/colapsado se comunica por la rotación del chevron Y por
 * [stateDescription] (nunca solo por movimiento): en reduce-motion el chevron
 * salta a su orientación final y sigue indicando el estado. La fila supera el
 * touch mínimo (≥56dp). Al colapsar, el panel se desmonta y sale del orden de
 * foco; el foco permanece en la cabecera.
 *
 * @param title título de la cabecera (h2/titleLarge, foreground). i18n.
 * @param subtitle subtítulo opcional (caption muted). i18n.
 * @param leading slot opcional a la izquierda (icono muted o StatusChip).
 * @param trailing slot opcional antes del chevron (p. ej. MoneyText de contexto).
 * @param initiallyExpanded estado inicial.
 * @param sticky variante keypad/LocalDetalle: cabecera con fondo surface-2; la
 *   variante card (false) usa surface-1 (la eleva el AppCard contenedor).
 * @param onExpandedChange callback opcional con el nuevo estado al alternar.
 * @param expandedStateDescription texto de estado para el lector cuando expandido.
 * @param collapsedStateDescription texto de estado para el lector cuando colapsado.
 * @param modifier modificador del contenedor (último parámetro).
 * @param content cuerpo que se monta bajo el separador al expandir.
 */
@Composable
fun RecreCollapsible(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    sticky: Boolean = false,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    expandedStateDescription: String = "Expandido",
    collapsedStateDescription: String = "Contraído",
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val colors = RecreColors.current
    // 0ms (snap) con reduce-motion; el estado y la semántica se conservan.
    val durationMs = if (rememberAnimationsEnabled()) COLLAPSE_DURATION_MS else 0
    val headerBg = if (sticky) colors.surface2 else MaterialTheme.colorScheme.surface

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = durationMs, easing = CollapseEasing),
        label = "collapsible-chevron",
    )

    Column(modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .heightIn(min = 56.dp)
                    // Un único nodo accesible para toda la fila (el chevron no es botón aparte).
                    .clickable(role = Role.Button) {
                        expanded = !expanded
                        onExpandedChange?.invoke(expanded)
                    }
                    .semantics {
                        stateDescription =
                            if (expanded) expandedStateDescription else collapsedStateDescription
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge, // h2
                    color = MaterialTheme.colorScheme.onSurface, // foreground
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                }
            }
            trailing?.invoke()
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null, // el estado va por stateDescription
                tint = colors.muted,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter =
                expandVertically(animationSpec = tween(durationMs, easing = CollapseEasing)) +
                    fadeIn(animationSpec = tween(durationMs)),
            exit =
                shrinkVertically(animationSpec = tween(durationMs, easing = CollapseEasing)) +
                    fadeOut(animationSpec = tween(durationMs)),
        ) {
            Column {
                HorizontalDivider(thickness = 1.dp, color = colors.border)
                Column(
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.surface) // contenido sobre surface-1
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    content = content,
                )
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

@Preview(name = "Collapsible card · light", showBackground = true)
@Composable
private fun CollapsibleCardLightPreview() {
    RecreTheme(darkTheme = false) {
        RecreCollapsible(
            title = "Auditoría técnica",
            subtitle = "Denominaciones · firma · cambios",
            initiallyExpanded = true,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Detalle de la recaudación…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(name = "Collapsible sticky colapsado · dark", showBackground = true)
@Composable
private fun CollapsibleStickyDarkPreview() {
    RecreTheme(darkTheme = true) {
        RecreCollapsible(
            title = "Bar Pepe",
            subtitle = "3 máquinas",
            sticky = true,
        ) {
            Text(
                text = "Contexto del local…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
