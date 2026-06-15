package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// Design System "Confianza Industrial" — atom A-OfflineBadge (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// Indicador discreto y persistente de "sin conexión / trabajando offline" en la
// TopBar. Comunica que la app opera contra la cola local sin alarmar: es NEUTRO,
// NO error. T-1: offline/stale → neutro, NUNCA danger/rojo. Sustituye al
// SyncStaleBlocker rojo por su versión neutra. Distinto de StatusChip (que
// rotula el estado semántico de una entidad con fondo de color); aquí es un
// meta-estado de conectividad de menor énfasis, sin color semántico, que
// "respira" (pulse lento de opacidad).
//
// Adaptaciones al stack real:
//  - El spec pide Phosphor `CloudSlash`; esa lib NO está. Se usa el equivalente
//    de material-icons-extended `Icons.Filled.CloudOff` (ya consumido por
//    MoneyText). El icono es decorativo (contentDescription = null): el
//    significado lo aporta el [label] + el contentDescription del contenedor.
//  - reduce-motion: misma detección que Skeleton (ANIMATOR_DURATION_SCALE / la
//    preview), pero aquí el dot/icono queda a opacidad fija intermedia (~0.85),
//    sin respirar; el significado se preserva por icono+texto.
//
// "online (ausente)": el componente simplemente NO se renderiza cuando hay red.
// NO existe un OfflineBadge "apagado"/disabled; eso lo decide el llamador.

// Opacidad fija intermedia con reduce-motion (el pulse iría de 0.40 a 1.0).
private const val STATIC_PULSE_ALPHA = 0.85f
private const val PULSE_MIN_ALPHA = 0.40f
private const val PULSE_MAX_ALPHA = 1.0f
private const val PULSE_PERIOD_MS = 1000

/**
 * Badge neutro de "sin conexión / trabajando offline" para la TopBar.
 *
 * Estado nunca solo-color: dot + icono `CloudOff` + texto, todo en el rol NEUTRO
 * (`stateNeutral*` / `muted`), nunca warning/danger. El dot y el icono "respiran"
 * (pulse lento de opacidad) salvo con reduce-motion, donde quedan a opacidad fija.
 *
 * El cambio "ahora estás offline" se anuncia UNA vez (liveRegion Polite) vía el
 * [contentDescription] del contenedor; el pulse va marcado como decorativo.
 *
 * @param label texto visible (i18n, lo pone el llamador con stringResource).
 *   P. ej. "Sin conexión" o, en variante stale, "Sin conexión · 3h".
 * @param contentDescription descripción completa para el lector (i18n). P. ej.
 *   "Sin sincronizar desde hace 3 horas". Si es null, se usa [label].
 * @param onClick acción opcional al pulsar (p. ej. abrir Sincronizar). Si es null
 *   el badge es no interactivo; si se aporta, gana objetivo táctil ≥48dp y foco.
 * @param modifier modificador del contenedor (último parámetro).
 */
@Composable
fun OfflineBadge(
    label: String,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    val animate = rememberAnimationsEnabled()

    // Pulse lento de opacidad del dot/icono. Con reduce-motion: estático a 0.85.
    val pulseAlpha =
        if (animate) {
            val transition = rememberInfiniteTransition(label = "offline-pulse")
            val value by transition.animateFloat(
                initialValue = PULSE_MIN_ALPHA,
                targetValue = PULSE_MAX_ALPHA,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(durationMillis = PULSE_PERIOD_MS, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "offline-pulse-alpha",
            )
            value
        } else {
            STATIC_PULSE_ALPHA
        }

    val announce = contentDescription ?: label

    // Visual del pill: altura fija 28dp (Android), fondo surface-2 (stateNeutralBg)
    // + borde 1px. Elevación por borde, nunca sombra. El llamador pasa el texto.
    val pillContent: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(colors.stateNeutralBg)
                    .border(1.dp, colors.stateNeutralBorder, CircleShape)
                    .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Dot pulsante Ø 8dp, relleno `muted`. Decorativo (anima opacidad).
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(colors.muted),
            )
            // Icono CloudOff 16dp, `muted`. Decorativo (significado vía label/desc).
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = colors.muted,
                modifier =
                    Modifier
                        .size(16.dp)
                        .graphicsLayer { alpha = pulseAlpha },
            )
            // Label en `muted` (rol del spec para el offline; estado de-enfatizado).
            Text(
                text = label,
                color = colors.muted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    if (onClick != null) {
        // Target táctil ≥48dp ENVOLVIENDO el visual de 28dp (no se infla el pill).
        Box(
            modifier =
                modifier
                    .sizeIn(minHeight = 48.dp, minWidth = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = announce
                        liveRegion = LiveRegionMode.Polite
                    },
            contentAlignment = Alignment.Center,
        ) { pillContent() }
    } else {
        Box(
            modifier =
                modifier.semantics(mergeDescendants = true) {
                    this.contentDescription = announce
                    liveRegion = LiveRegionMode.Polite
                    role = Role.Image
                },
        ) { pillContent() }
    }
}

/**
 * ¿Están activas las animaciones? Falso en preview o si el usuario desactivó las
 * animaciones del sistema (ANIMATOR_DURATION_SCALE = 0). Equivale al
 * prefers-reduced-motion de la web. (Mismo criterio que Skeleton.)
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

@Preview(name = "OfflineBadge · light", showBackground = true)
@Composable
private fun OfflineBadgeLightPreview() {
    RecreTheme(darkTheme = false) {
        OfflineBadge(
            label = "Sin conexión",
            contentDescription = "Sin conexión, trabajando sin conexión",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "OfflineBadge stale · light", showBackground = true)
@Composable
private fun OfflineBadgeStaleLightPreview() {
    RecreTheme(darkTheme = false) {
        OfflineBadge(
            label = "Sin conexión · 3h",
            contentDescription = "Sin sincronizar desde hace 3 horas",
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "OfflineBadge · dark", showBackground = true)
@Composable
private fun OfflineBadgeDarkPreview() {
    RecreTheme(darkTheme = true) {
        OfflineBadge(
            label = "Sin conexión",
            contentDescription = "Sin conexión, trabajando sin conexión",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "OfflineBadge stale · dark", showBackground = true)
@Composable
private fun OfflineBadgeStaleDarkPreview() {
    RecreTheme(darkTheme = true) {
        OfflineBadge(
            label = "Sin conexión · 3h",
            contentDescription = "Sin sincronizar desde hace 3 horas",
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
