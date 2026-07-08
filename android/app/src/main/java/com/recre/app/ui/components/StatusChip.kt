package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.recre.app.ui.theme.GeistMono
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// =====================================================================
// StatusChip · C-StatusChip — átomo del design system "Confianza Industrial".
// Chip de estado "soft" (fondo rol-container OPACO + contenido en el rol-fg
// validado): dot + icono + label SIEMPRE juntos (regla 2: estado NUNCA solo
// color, ~350M personas con daltonismo). Sustituye los badges ad hoc
// (EstadoMaquinaBadge solo-texto). NO es acción ni marca: el primary petróleo
// NO se usa aquí.
//
// Cinco roles semánticos: success (cuadra / dinero+ / firme), warning
// (pendiente / sin-firmar / stale), danger (avería / conflicto / descuadre),
// neutral (offline / borrador / info pasiva → muted, NUNCA rojo) e info
// (sincronizando). Pares bg/fg OPACOS PRECOMPUTADOS por rol+modo en
// RecreColors (el contraste no depende de la fila/hover/card).
//
// SSOT: .kiro/specs/recre/fase3-component-specs.md (StatusChip).
// =====================================================================

/** Rol semántico del chip. El acento de marca (primary) NO es un rol: un chip es estado. */
enum class StatusRole { SUCCESS, WARNING, DANGER, NEUTRAL, INFO }

/** Tamaño del chip. md por defecto; sm sin dot (tablas densas); lg para cabeceras de detalle. */
enum class StatusChipSize { SM, MD, LG }

/** Par OPACO bg/fg resuelto por rol+modo. fg = dot + icono + texto (mismo hex, validado). */
private data class StatusChipColors(val bg: Color, val fg: Color)

/**
 * Indicador de estado compacto y "soft".
 *
 * @param role rol semántico (color + significado). NUNCA danger para offline.
 * @param label texto del estado (corto, una línea, sin truncar). El llamador lo
 *   pasa ya resuelto vía stringResource — aquí NO se hardcodea texto de UI.
 * @param icon glifo del rol (Material Icons). Obligatorio: nunca un chip sin icono.
 *   Decorativo para a11y (el label ya nombra el estado); su forma da redundancia
 *   sin color. En spinning con reduced-motion se sustituye por un icono estático.
 * @param size tamaño (sm/md/lg). sm omite el dot por defecto.
 * @param showDot muestra el dot 6dp (refuerzo visual). Off en sm por defecto.
 * @param pulsing pulso lento del conjunto (estado offline). OFF con reduced-motion.
 * @param spinning rotación continua del icono (sincronizando). OFF con reduced-motion
 *   (icono estático). pulsing y spinning son excluyentes; si ambos, gana spinning.
 * @param modifier modificador externo (último parámetro, convención del repo).
 */
@Composable
fun StatusChip(
    role: StatusRole,
    label: String,
    icon: ImageVector,
    size: StatusChipSize = StatusChipSize.MD,
    showDot: Boolean = size != StatusChipSize.SM,
    pulsing: Boolean = false,
    spinning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = RecreColors.current
    val reducedMotion = rememberReducedMotion()

    val c =
        when (role) {
            StatusRole.SUCCESS -> StatusChipColors(tokens.successChipBg, tokens.successChipFg)
            StatusRole.WARNING -> StatusChipColors(tokens.warningChipBg, tokens.warningChipFg)
            StatusRole.DANGER -> StatusChipColors(tokens.dangerChipBg, tokens.dangerChipFg)
            StatusRole.NEUTRAL -> StatusChipColors(tokens.neutralChipBg, tokens.neutralChipFg)
            StatusRole.INFO -> StatusChipColors(tokens.infoChipBg, tokens.infoChipFg)
        }

    val height =
        when (size) {
            StatusChipSize.SM -> 20.dp
            StatusChipSize.MD -> 24.dp
            StatusChipSize.LG -> 28.dp
        }
    val hPad =
        when (size) {
            StatusChipSize.SM -> 6.dp
            StatusChipSize.MD -> 8.dp
            StatusChipSize.LG -> 10.dp
        }
    val iconSz =
        when (size) {
            StatusChipSize.SM -> 12.dp
            StatusChipSize.MD -> 14.dp
            StatusChipSize.LG -> 16.dp
        }
    val style =
        when (size) {
            // S10 (mockup .estado): label mono uppercase con tracking; el icono se
            // conserva por decisión D.3-4 (estado nunca solo por color).
            StatusChipSize.SM ->
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = GeistMono, fontWeight = FontWeight.W600, letterSpacing = 0.08.em,
                )
            StatusChipSize.MD ->
                MaterialTheme.typography.labelMedium.copy(
                    fontFamily = GeistMono, fontWeight = FontWeight.W600, letterSpacing = 0.08.em,
                )
            StatusChipSize.LG ->
                MaterialTheme.typography.labelLarge.copy(
                    fontFamily = GeistMono, fontWeight = FontWeight.W600, letterSpacing = 0.08.em,
                )
        }

    // Animaciones SIEMPRE con guard reducedMotion: el estado sigue 100% legible sin movimiento.
    val spin = spinning && !reducedMotion
    val pulse = pulsing && !spinning && !reducedMotion

    val spinAngle =
        if (spin) {
            val transition = rememberInfiniteTransition(label = "statusChipSpin")
            transition
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 800, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "statusChipSpinAngle",
                ).value
        } else {
            0f
        }

    val pulseAlpha =
        if (pulse) {
            val transition = rememberInfiniteTransition(label = "statusChipPulse")
            transition
                .animateFloat(
                    initialValue = 1f,
                    targetValue = 0.5f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 2000),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "statusChipPulseAlpha",
                ).value
        } else {
            1f
        }

    // spinning + reduced-motion → icono estático equivalente (no spinner): el chip nunca se vacía.
    val drawIcon = if (spinning && reducedMotion) Icons.Filled.Autorenew else icon

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .height(height)
                .clip(RoundedCornerShape(percent = 50)) // pill (full-rounded), nunca radios de card
                .background(c.bg) // container OPACO, no alpha: contraste independiente de la superficie
                .padding(horizontal = hPad)
                .alpha(pulseAlpha) // pulse offline; off si reducedMotion
                // El chip se anuncia como un único nodo: mergeDescendants funde icono
                // (cd=null) + Text(label) en una sola etiqueta, sin doble anuncio.
                // liveRegion Polite → el lector avisa de cambios (p.ej. avería nueva) sin interrumpir.
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        if (showDot) {
            // Decorativo (aria-hidden): redundante con icono+texto. mismo hex que el fg, ya validado.
            androidx.compose.foundation.layout.Box(
                Modifier.size(6.dp).clip(CircleShape).background(c.fg),
            )
        }
        // Icono decorativo: contentDescription=null porque el label ya nombra el estado.
        Icon(
            imageVector = drawIcon,
            contentDescription = null,
            tint = c.fg,
            modifier = Modifier.size(iconSz).rotate(spinAngle),
        )
        // Label: peso del rol-fg, una línea, sin truncar (los estados son cortos).
        Text(
            text = label.uppercase(),
            style = style,
            color = c.fg,
            maxLines = 1,
        )
    }
}

/**
 * Señal REAL de reduced-motion en Android (no pseudocódigo): el usuario ha
 * desactivado/reducido las animaciones del sistema → ANIMATOR_DURATION_SCALE == 0.
 * Apaga pulse offline y giro del spinner. En @Preview/inspección no hay ajuste
 * del sistema fiable → se asume movimiento permitido.
 */
@Composable
private fun rememberReducedMotion(): Boolean {
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
// Previews — light y dark. Cubren los 5 roles + tamaños + offline/sincronizando.
// ---------------------------------------------------------------------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StatusChipShowcase() {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
    ) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(StatusRole.SUCCESS, "Cuadra", Icons.Filled.Check)
            StatusChip(StatusRole.WARNING, "Pendiente", Icons.Filled.Warning)
            StatusChip(StatusRole.DANGER, "Avería", Icons.Outlined.ErrorOutline)
            StatusChip(StatusRole.NEUTRAL, "Offline", Icons.Filled.CloudOff)
            StatusChip(StatusRole.INFO, "Sincronizando", Icons.Filled.Sync, spinning = true)
        }
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(StatusRole.SUCCESS, "Cuadra", Icons.Filled.Check, size = StatusChipSize.SM)
            StatusChip(StatusRole.WARNING, "Pendiente", Icons.Filled.Warning, size = StatusChipSize.LG)
            StatusChip(StatusRole.NEUTRAL, "Offline", Icons.Filled.CloudOff, pulsing = true)
        }
    }
}

@Preview(name = "StatusChip · Light", showBackground = true)
@Composable
private fun StatusChipLightPreview() {
    RecreTheme(darkTheme = false) { StatusChipShowcase() }
}

@Preview(name = "StatusChip · Dark", showBackground = true)
@Composable
private fun StatusChipDarkPreview() {
    RecreTheme(darkTheme = true) { StatusChipShowcase() }
}
