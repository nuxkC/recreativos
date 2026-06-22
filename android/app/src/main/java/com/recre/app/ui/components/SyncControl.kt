package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.recre.app.R
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme
import kotlinx.coroutines.delay

// Design System "Confianza Industrial" — atom C-SYNC-01 SyncControl (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// Botón de sincronización con máquina de estados que sustituye al
// SyncStaleBanner/SyncStaleBlocker ROJO (T-1). Comunica el estado de la cola
// offline sin alarmar: idle = neutro (muted), syncing = gira en primary, stale =
// warning ámbar ("Sin sincronizar hace Xh"), NUNCA danger/rojo. El rojo se
// reserva a errores reales y vive FUERA del átomo (toast/sonner).
//
// Adaptaciones al stack real:
//  - El spec pide Phosphor `ArrowsClockwise`; esa lib NO está. Se usa el
//    equivalente material-icons-extended `Icons.Filled.Sync`.
//  - reduce-motion: misma detección que Skeleton/OfflineBadge (ANIMATOR_DURATION_SCALE).
//  - El flash success loading→idle: al pasar de Syncing a Idle se muestra un
//    check Lottie breve (sync_ok) en lugar del icono, respetando reduce-motion.
//  - El delta "hace Xh" y el metadato NO se calculan aquí (anti-patrón de fechas
//    ad hoc): entran ya formateados por parámetro desde el estado de sync.

/** Máquina de estados de sincronización. El color es refuerzo, nunca la única señal. */
enum class SyncStatus {
    /** Al día: icono muted, sin badge. */
    Idle,

    /** Sincronizando: icono primary girando. */
    Syncing,

    /** Sin sincronizar hace Xh: icono + badge warning (NO danger). */
    Stale,

    /** Cambios en cola pero sync reciente: badge info. */
    Pending,

    /** Sync globalmente inhabilitada (sin sesión): muted al 38%. */
    Disabled,
}

/** Forma de presentación: icon-button de TopBar o fila/banner expandido. */
enum class SyncVariant {
    /** Icon-button 48dp en TopBar (A.1 / E.1). */
    Compact,

    /** Fila card surface-1 (pantalla Sincronizar D.6 / cabecera stale). */
    Expanded,
}

private const val SPIN_PERIOD_MS = 180 // 360° por vuelta, loop mecánico lineal
private const val PULSE_PERIOD_MS = 1600

/**
 * Control de sincronización idle/syncing/stale/pending, neutro salvo el ámbar de
 * stale; jamás rojo.
 *
 * El estado nunca se transmite solo por color: cada estado lleva icono + texto
 * ([statusLabel], siempre expuesto al lector como contentDescription + región
 * viva Polite). El icono gira solo en [SyncStatus.Syncing] y el dot-badge pulsa
 * solo en [SyncStatus.Stale]; ambos respetan reduce-motion. El control sigue
 * siendo focusable durante el giro (solo se inhabilita el re-disparo).
 *
 * @param status estado de sincronización.
 * @param statusLabel texto del estado, ya formateado ("Sincronizado",
 *   "Sincronizando…", "Sin sincronizar hace 3 h"). Se usa como contentDescription
 *   (compacto) y como título (expandido). i18n por el llamador.
 * @param onSync acción de sincronizar (manual).
 * @param variant Compact (TopBar) o Expanded (fila/banner).
 * @param metadato 2ª línea del expandido ("3 cambios en cola"); muted. i18n.
 * @param pendientes nº de cambios en cola; >0 con [SyncStatus.Pending] pinta el
 *   dot-badge `info`.
 * @param syncAhoraLabel texto del botón "Sincronizar ahora" (solo expandido);
 *   null ⇒ sin botón. i18n.
 * @param modifier modificador del contenedor (último parámetro).
 */
@Composable
fun SyncControl(
    status: SyncStatus,
    statusLabel: String,
    onSync: () -> Unit,
    variant: SyncVariant = SyncVariant.Compact,
    metadato: String? = null,
    pendientes: Int = 0,
    syncAhoraLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    val animate = rememberAnimationsEnabled()

    // Flash de éxito al completar (Syncing → Idle): un check Lottie breve en vez
    // de que el icono pase a muted sin más. Respeta reduce-motion.
    var justSynced by remember { mutableStateOf(false) }
    var statusPrevio by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        if (statusPrevio == SyncStatus.Syncing && status == SyncStatus.Idle && animate) {
            justSynced = true
            delay(1100)
            justSynced = false
        } else {
            justSynced = false
        }
        statusPrevio = status
    }

    // Tinte del icono por estado: idle/pending muted, syncing primary, stale warning.
    val tint =
        when (status) {
            SyncStatus.Idle, SyncStatus.Pending -> colors.muted
            SyncStatus.Syncing -> MaterialTheme.colorScheme.primary
            SyncStatus.Stale -> colors.warning // ámbar, NUNCA danger
            SyncStatus.Disabled -> colors.muted.copy(alpha = 0.38f)
        }

    // Rotación 360°/180ms en loop solo mientras gira y con animaciones activas.
    val angle =
        if (status == SyncStatus.Syncing && animate) {
            val transition = rememberInfiniteTransition(label = "sync-spin")
            val value by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = SPIN_PERIOD_MS, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "sync-angle",
            )
            value
        } else {
            0f
        }

    val interactive = status != SyncStatus.Disabled && status != SyncStatus.Syncing

    when (variant) {
        SyncVariant.Compact ->
            Box(
                modifier =
                    modifier
                        .size(48.dp) // hit-area táctil aunque el icono mida 24dp
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = interactive, onClick = onSync)
                        .semantics {
                            contentDescription = statusLabel
                            liveRegion = LiveRegionMode.Polite
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                SyncGlifo(
                    justSynced = justSynced,
                    angle = angle,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                // Dot-badge: warning (pulsa) en stale; info (estático) si hay cola.
                when {
                    status == SyncStatus.Stale ->
                        SyncStatusDot(
                            color = colors.warning,
                            pulse = animate,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp),
                        )
                    status == SyncStatus.Pending && pendientes > 0 ->
                        SyncStatusDot(
                            color = colors.info,
                            pulse = false,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp),
                        )
                }
            }

        SyncVariant.Expanded ->
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface, // surface-1, NO errorContainer
                shape = RoundedCornerShape(16.dp),
                // Elevación por borde; en stale el borde pasa a warning.
                border =
                    BorderStroke(
                        1.dp,
                        if (status == SyncStatus.Stale) colors.warning else colors.border,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SyncGlifo(
                        justSynced = justSynced,
                        angle = angle,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (status == SyncStatus.Stale) {
                                    colors.warning
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                        if (metadato != null) {
                            Text(
                                text = metadato,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                    }
                    if (syncAhoraLabel != null) {
                        RecreTextButton(
                            text = syncAhoraLabel,
                            onClick = onSync,
                            enabled = status != SyncStatus.Syncing,
                        )
                    }
                }
            }
    }
}

/** Dot-badge de estado Ø8dp; pulsa opacidad 1↔0.6 (~1600ms) si [pulse]. Decorativo. */
/**
 * Glifo del control de sync: un check Lottie breve tras completar ([justSynced])
 * o el icono Sync (que gira mientras [SyncStatus.Syncing]). Decorativo: el
 * estado lo anuncia el contentDescription del contenedor.
 */
@Composable
private fun SyncGlifo(
    justSynced: Boolean,
    angle: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    if (justSynced) {
        LottieIllustration(rawRes = R.raw.sync_ok, modifier = modifier, iterations = 1)
    } else {
        Icon(
            imageVector = Icons.Filled.Sync,
            contentDescription = null,
            tint = tint,
            modifier = modifier.graphicsLayer { rotationZ = angle },
        )
    }
}

@Composable
private fun SyncStatusDot(
    color: Color,
    pulse: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha =
        if (pulse) {
            val transition = rememberInfiniteTransition(label = "sync-dot-pulse")
            val value by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.6f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = PULSE_PERIOD_MS, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "sync-dot-alpha",
            )
            value
        } else {
            1f
        }
    Box(
        modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color),
    )
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

@Preview(name = "SyncControl compacto · light", showBackground = true)
@Composable
private fun SyncControlCompactLightPreview() {
    RecreTheme(darkTheme = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SyncControl(status = SyncStatus.Idle, statusLabel = "Sincronizado", onSync = {})
            SyncControl(status = SyncStatus.Syncing, statusLabel = "Sincronizando…", onSync = {})
            SyncControl(
                status = SyncStatus.Stale,
                statusLabel = "Sin sincronizar hace 3 h",
                onSync = {},
            )
            SyncControl(
                status = SyncStatus.Pending,
                statusLabel = "2 cambios pendientes",
                onSync = {},
                pendientes = 2,
            )
        }
    }
}

@Preview(name = "SyncControl expandido stale · dark", showBackground = true)
@Composable
private fun SyncControlExpandedStaleDarkPreview() {
    RecreTheme(darkTheme = true) {
        SyncControl(
            status = SyncStatus.Stale,
            statusLabel = "Sin sincronizar hace 3 h",
            onSync = {},
            variant = SyncVariant.Expanded,
            metadato = "3 cambios en cola",
            syncAhoraLabel = "Sincronizar ahora",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "SyncControl expandido idle · light", showBackground = true)
@Composable
private fun SyncControlExpandedIdleLightPreview() {
    RecreTheme(darkTheme = false) {
        SyncControl(
            status = SyncStatus.Idle,
            statusLabel = "Sincronizado",
            onSync = {},
            variant = SyncVariant.Expanded,
            metadato = "Última: hace 5 min",
            syncAhoraLabel = "Sincronizar ahora",
            modifier = Modifier.padding(16.dp),
        )
    }
}
