package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// Design System "Confianza Industrial" — atom A-EMPTYSTATE (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// "Vacío útil": comunica ausencia de datos (o vacío por filtro) SIN parecer un
// error. Glifo grande en `muted` + título foreground + descripción muted + CTA
// opcional. NUNCA danger/rojo: vacío != error (regla 1). La única salpicadura de
// acento es el CTA primario (dentro del ≤10%); el vacío-por-filtro usa botón
// ghost "Quitar filtros", no primary, para no malgastar acento.
//
// Adaptaciones al stack real:
//  - El spec pide glifos Phosphor; esa lib NO está. El glifo entra por parámetro
//    [icon] (lo aporta el llamador con material-icons-extended). El CTA usa
//    `Icons.Filled.Add` (equivalente al Plus de la referencia).
//  - El CTA reutiliza el átomo Button del DS (RecrePrimaryButton / RecreTextButton),
//    no un Button M3 crudo, como pide el spec ("Reutiliza el átomo Button").
//
// NO es loading (eso es Skeleton hasta confirmar 0 resultados) ni error (eso es
// ErrorState, card neutra con "Reintentar"). EmptyState es solo "sin datos".

// Easing de entrada del spec: cubic-bezier(0.2, 0, 0, 1).
private val EmptyStateEnterEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val ENTER_DURATION_MS = 180
private const val ENTER_TRANSLATE_DP = 4

/**
 * Estado vacío canónico: glifo `muted` + título + descripción + CTA opcional.
 *
 * El estado nunca se transmite solo por color: el vacío lo comunican el glifo
 * (neutro, nunca rojo) y el texto explícito. El contenedor se anuncia como
 * región viva (Polite) para que al pasar de loading a vacío el lector diga el
 * título. Aparición fade + translateY 4px (180ms), desactivada con reduce-motion.
 *
 * @param icon glifo de dominio (decorativo; el significado va en el texto).
 * @param title qué falta (heading corto, foreground). i18n por el llamador.
 * @param description acción sugerida o porqué (muted, 1-2 líneas). i18n.
 * @param actionLabel texto del CTA; null ⇒ vacío no accionable (sin CTA).
 * @param onActionClick acción del CTA; null ⇒ sin CTA.
 * @param filtered true cuando el vacío viene de un filtro/búsqueda: el CTA pasa
 *   a ghost "Quitar filtros" (sin acento, sin icono Plus).
 * @param compact true dentro de tabla/lista densa (padding e icono reducidos).
 * @param modifier modificador del contenedor (último parámetro).
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    filtered: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    val animate = rememberAnimationsEnabled()

    // Entrada una sola vez: fade + 4px→0. Con reduce-motion aparece instantánea.
    var appeared by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec =
            if (animate) {
                tween(durationMillis = ENTER_DURATION_MS, easing = EmptyStateEnterEasing)
            } else {
                snap()
            },
        label = "emptystate-enter",
    )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * ENTER_TRANSLATE_DP.dp.toPx()
                }
                .padding(
                    horizontal = 24.dp,
                    vertical = if (compact) 24.dp else 32.dp,
                )
                // Una sola región viva para que el lector anuncie el título al
                // resolverse la query con 0 resultados.
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Glifo decorativo en `muted` (jamás danger ni primary), plano, sin círculo.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(if (compact) 40.dp else 48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium, // H2 18/600
            color = MaterialTheme.colorScheme.onSurface, // foreground
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge, // body 16/450
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        if (actionLabel != null && onActionClick != null) {
            Spacer(Modifier.height(24.dp))
            if (filtered) {
                // Vacío por filtro: ghost, sin acento ni icono Plus.
                RecreTextButton(
                    text = actionLabel,
                    onClick = onActionClick,
                )
            } else {
                // Vacío accionable: única aplicación de acento (CTA primary + Plus).
                RecrePrimaryButton(
                    text = actionLabel,
                    onClick = onActionClick,
                    leadingIcon = Icons.Filled.Add,
                    fullWidth = false,
                )
            }
        }
    }
}

/**
 * ¿Animaciones activas? Falso en preview o con animaciones del sistema
 * desactivadas (ANIMATOR_DURATION_SCALE = 0). Equivale a prefers-reduced-motion.
 * (Mismo criterio que Skeleton/OfflineBadge; función privada por fichero.)
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

@Preview(name = "EmptyState accionable · light", showBackground = true)
@Composable
private fun EmptyStateActionableLightPreview() {
    RecreTheme(darkTheme = false) {
        EmptyState(
            icon = Icons.Filled.Inbox,
            title = "Aún no hay máquinas",
            description = "Añade la primera para empezar a recaudar.",
            actionLabel = "Añadir máquina",
            onActionClick = {},
        )
    }
}

@Preview(name = "EmptyState sin CTA · light", showBackground = true)
@Composable
private fun EmptyStateNoActionLightPreview() {
    RecreTheme(darkTheme = false) {
        EmptyState(
            icon = Icons.Filled.Inbox,
            title = "Sin locales",
            description = "No tienes locales con máquina instalada.",
        )
    }
}

@Preview(name = "EmptyState por filtro · dark", showBackground = true)
@Composable
private fun EmptyStateFilteredDarkPreview() {
    RecreTheme(darkTheme = true) {
        EmptyState(
            icon = Icons.Filled.Inbox,
            title = "Sin resultados",
            description = "Prueba con otros filtros.",
            actionLabel = "Quitar filtros",
            onActionClick = {},
            filtered = true,
        )
    }
}
