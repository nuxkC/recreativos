package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

// Design System "Confianza Industrial" — atom C-ERR-01 ErrorState (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// Card NEUTRA de reintento: comunica un FALLO DE CARGA recuperable (red caída,
// timeout, 5xx, permiso transitorio) ofreciendo "Reintentar", SIN alarmar.
// NEUTRO (surface-2 + borde + icono/texto `muted`), NUNCA danger/rojo: un fallo
// de red no es un error del dominio económico (T-1). Distinto de EmptyState
// (ausencia legítima de datos, sin reintento) y de los estados danger (avería /
// descuadre / conflicto, que SÍ son rojos y tienen sus propios componentes).
// Sustituye a los ErrorCard/ErrorBanner que pintaban errorContainer.
//
// Adaptaciones al stack real:
//  - El spec pide iconos Phosphor (WifiSlash/LockKey/Warning); esa lib NO está.
//    Se usan equivalentes de material-icons-extended: CloudOff (red), Lock
//    (permiso), Outlined.Warning (genérico). Siempre tintados `muted`, jamás danger.
//  - No hay variante "outline" en RecreButton; el botón Reintentar usa
//    `OutlinedButton` M3 tokenizado (borde = border, texto = foreground), como
//    la referencia del spec — el acento solo vive en el anillo de foco (ring).
//  - El código técnico (status, PGRST…) NO se muestra: va a log, no a UI.

/** Causa del fallo de carga; determina el icono neutro (nunca el color). */
enum class ErrorCausa {
    /** Genérico / 5xx → Warning contorno. */
    Generica,

    /** Sin conexión / red → CloudOff. */
    Red,

    /** Permiso / RLS transitorio / SinAcceso → Lock. */
    Permiso,
}

/** Forma de presentación del estado de error. */
enum class ErrorVariante {
    /** Banner compacto en fila, sobre datos parciales/stale. */
    Inline,

    /** Card centrada dentro del contenedor de lista vacía. */
    Card,

    /** Card centrada en toda la pantalla (SinAcceso, A.1 sin datos). */
    Page,
}

/**
 * Estado de error de carga, neutro y recuperable.
 *
 * El estado nunca se transmite solo por color: lo distinguen el ICONO de causa +
 * TÍTULO + TEXTO, todo en rol neutro sobre surface-2. El contenedor se anuncia
 * como región viva (Polite) sin robar el foco. El botón Reintentar se deshabilita
 * mientras [reintentando] para evitar dobles disparos.
 *
 * @param titulo qué pasó, en lenguaje humano (foreground). i18n por el llamador.
 * @param descripcion una línea accionable (muted), sin códigos técnicos. i18n.
 * @param onReintentar acción de reintento.
 * @param reintentarLabel texto del botón ("Reintentar" / "Comprobar de nuevo"). i18n.
 * @param reintentandoLabel texto mientras revalida ("Reintentando…"). i18n.
 * @param causa causa del fallo (selecciona el icono neutro).
 * @param variante Inline / Card / Page.
 * @param reintentando true mientras revalida: spinner + botón deshabilitado.
 * @param reintentable false ⇒ sin botón Reintentar (p. ej. permiso definitivo);
 *   usar [accionSecundaria] (Salir / Cambiar empresa) en su lugar.
 * @param accionSecundaria slot opcional de acción de texto (link "Ver detalles").
 * @param modifier modificador del contenedor (último parámetro).
 */
@Composable
fun ErrorState(
    titulo: String,
    descripcion: String,
    onReintentar: () -> Unit,
    reintentarLabel: String,
    reintentandoLabel: String,
    causa: ErrorCausa = ErrorCausa.Generica,
    variante: ErrorVariante = ErrorVariante.Card,
    reintentando: Boolean = false,
    reintentable: Boolean = true,
    accionSecundaria: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    val icono =
        when (causa) {
            ErrorCausa.Red -> Icons.Filled.CloudOff
            ErrorCausa.Permiso -> Icons.Filled.Lock
            ErrorCausa.Generica -> Icons.Outlined.Warning
        }
    // Elevación por borde (light) / luminancia de surface-2 (dark): sin sombra.
    val border = if (colors.isLight) BorderStroke(1.dp, colors.border) else null
    val shape = RoundedCornerShape(if (variante == ErrorVariante.Inline) 12.dp else 16.dp)

    // Pulse lento del icono solo en la causa "sin conexión" (ambiental, no error).
    val iconAlpha = rememberOfflinePulse(active = causa == ErrorCausa.Red)

    val containerSemantics =
        Modifier.semantics { liveRegion = LiveRegionMode.Polite }

    when (variante) {
        ErrorVariante.Inline ->
            Surface(
                modifier = modifier.fillMaxWidth().then(containerSemantics),
                color = colors.surface2,
                shape = shape,
                border = border,
            ) {
                Row(
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = icono,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(24.dp).graphicsLayer { alpha = iconAlpha },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titulo,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = descripcion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                    if (reintentable) {
                        RetryButton(
                            onReintentar = onReintentar,
                            reintentarLabel = reintentarLabel,
                            reintentandoLabel = reintentandoLabel,
                            reintentando = reintentando,
                            mutedColor = colors.muted,
                            borderColor = colors.border,
                        )
                    }
                }
            }

        ErrorVariante.Card ->
            Surface(
                modifier = modifier.fillMaxWidth().then(containerSemantics),
                color = colors.surface2,
                shape = shape,
                border = border,
            ) {
                ErrorCardContent(
                    icono = icono,
                    iconAlpha = iconAlpha,
                    titulo = titulo,
                    descripcion = descripcion,
                    onReintentar = onReintentar,
                    reintentarLabel = reintentarLabel,
                    reintentandoLabel = reintentandoLabel,
                    reintentando = reintentando,
                    reintentable = reintentable,
                    accionSecundaria = accionSecundaria,
                    mutedColor = colors.muted,
                    borderColor = colors.border,
                )
            }

        ErrorVariante.Page ->
            Box(
                modifier = modifier.fillMaxSize().then(containerSemantics),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).padding(24.dp),
                    color = colors.surface2,
                    shape = shape,
                    border = border,
                ) {
                    ErrorCardContent(
                        icono = icono,
                        iconAlpha = iconAlpha,
                        titulo = titulo,
                        descripcion = descripcion,
                        onReintentar = onReintentar,
                        reintentarLabel = reintentarLabel,
                        reintentandoLabel = reintentandoLabel,
                        reintentando = reintentando,
                        reintentable = reintentable,
                        accionSecundaria = accionSecundaria,
                        mutedColor = colors.muted,
                        borderColor = colors.border,
                    )
                }
            }
    }
}

/** Columna centrada de la card (icono + título + texto + botón + acción secundaria). */
@Composable
private fun ErrorCardContent(
    icono: ImageVector,
    iconAlpha: Float,
    titulo: String,
    descripcion: String,
    onReintentar: () -> Unit,
    reintentarLabel: String,
    reintentandoLabel: String,
    reintentando: Boolean,
    reintentable: Boolean,
    accionSecundaria: (@Composable () -> Unit)?,
    mutedColor: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = mutedColor,
            modifier = Modifier.size(40.dp).graphicsLayer { alpha = iconAlpha },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = descripcion,
            style = MaterialTheme.typography.bodyMedium,
            color = mutedColor,
            textAlign = TextAlign.Center,
        )
        if (reintentable) {
            Spacer(Modifier.height(16.dp))
            RetryButton(
                onReintentar = onReintentar,
                reintentarLabel = reintentarLabel,
                reintentandoLabel = reintentandoLabel,
                reintentando = reintentando,
                mutedColor = mutedColor,
                borderColor = borderColor,
            )
        }
        accionSecundaria?.let {
            Spacer(Modifier.height(8.dp))
            it()
        }
    }
}

/** Botón Reintentar: outline tokenizado, ≥48dp, spinner `muted` mientras revalida. */
@Composable
private fun RetryButton(
    onReintentar: () -> Unit,
    reintentarLabel: String,
    reintentandoLabel: String,
    reintentando: Boolean,
    mutedColor: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
) {
    OutlinedButton(
        onClick = onReintentar,
        enabled = !reintentando,
        border = BorderStroke(1.dp, borderColor), // border, no primary (acento ≤10%)
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface, // foreground
            ),
        modifier = Modifier.heightIn(min = 48.dp), // touch
    ) {
        if (reintentando) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = mutedColor,
            )
            Spacer(Modifier.width(8.dp))
            Text(reintentandoLabel)
        } else {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(reintentarLabel)
        }
    }
}

/**
 * Opacidad del icono: pulse lento 1↔0.6 (~1600ms) cuando [active] y hay
 * animaciones; en caso contrario opacidad fija 1f. Ambiental (offline), no una
 * transición de UI. Respeta reduce-motion / preview.
 */
@Composable
private fun rememberOfflinePulse(active: Boolean): Float {
    if (!active || !rememberAnimationsEnabled()) return 1f
    val transition = rememberInfiniteTransition(label = "error-offline-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "error-offline-alpha",
    )
    return alpha
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

@Preview(name = "ErrorState card · light", showBackground = true)
@Composable
private fun ErrorStateCardLightPreview() {
    RecreTheme(darkTheme = false) {
        ErrorState(
            titulo = "No se pudo cargar",
            descripcion = "Comprueba tu conexión e inténtalo de nuevo.",
            onReintentar = {},
            reintentarLabel = "Reintentar",
            reintentandoLabel = "Reintentando…",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "ErrorState inline red · dark", showBackground = true)
@Composable
private fun ErrorStateInlineDarkPreview() {
    RecreTheme(darkTheme = true) {
        ErrorState(
            titulo = "Sin conexión",
            descripcion = "Trabajando con datos guardados.",
            onReintentar = {},
            reintentarLabel = "Reintentar",
            reintentandoLabel = "Reintentando…",
            causa = ErrorCausa.Red,
            variante = ErrorVariante.Inline,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "ErrorState reintentando · light", showBackground = true)
@Composable
private fun ErrorStateLoadingLightPreview() {
    RecreTheme(darkTheme = false) {
        ErrorState(
            titulo = "No se pudo cargar",
            descripcion = "Comprueba tu conexión e inténtalo de nuevo.",
            onReintentar = {},
            reintentarLabel = "Reintentar",
            reintentandoLabel = "Reintentando…",
            reintentando = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
