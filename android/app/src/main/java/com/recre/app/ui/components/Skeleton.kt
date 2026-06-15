package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// Design System "Confianza Industrial" — atom-skeleton (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// Placeholder inerte que reserva el layout mientras se resuelve la petición
// servidor: comunica "estoy trayendo datos" sin reflow. No es un indicador de
// progreso ni de error/empty; vive solo en superficies neutras (surface-2) y
// JAMÁS usa color de estado (success/warning/danger/info) ni renderiza cifras.
//
// Adaptación: el spec asumía la lib externa valentinilk/compose-shimmer
// (rememberShimmer/ShimmerBounds) y un AppCard inexistente. Aquí el barrido se
// implementa con rememberInfiniteTransition (sin dependencias nuevas) y la card
// se compone con surface-1 + border 1px, como el Card real del DS.

/** Forma de la primitiva Skeleton. */
enum class SkeletonShape {
    /** Línea de texto: esquinas muy redondeadas (alto = lineHeight). */
    Line,

    /** Bloque/área: radius pequeño (KPI, imagen, botón). */
    Block,

    /** Círculo: avatar/icono reservado. */
    Circle,
}

private const val SHIMMER_PERIOD_MS = 1200

/**
 * Primitiva de carga: un rectángulo neutro sobre surface-2 con una banda
 * shimmer (highlight border/muted) que barre de izquierda a derecha.
 *
 * Respeta reduce-motion: con animaciones del sistema desactivadas
 * (ANIMATOR_DURATION_SCALE == 0) o en preview (LocalInspectionMode) el relleno
 * queda estático, sin barrido.
 *
 * Es decorativa: se oculta del árbol de semántica con [clearAndSetSemantics]
 * para que el lector anuncie "Cargando" UNA vez en el contenedor, no cada
 * rectángulo. El tamaño se fija con el [modifier] (width/height/size) para
 * igualar al contenido real y evitar reflow al cargar.
 *
 * @param shape forma de la primitiva (línea / bloque / círculo).
 * @param modifier dimensiones de la primitiva (debe traer width+height o size).
 */
@Composable
fun Skeleton(
    shape: SkeletonShape = SkeletonShape.Block,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    val animate = rememberAnimationsEnabled()

    val clipShape: Shape =
        when (shape) {
            SkeletonShape.Line -> RoundedCornerShape(percent = 50)
            SkeletonShape.Block -> RoundedCornerShape(6.dp)
            SkeletonShape.Circle -> CircleShape
        }

    val base = colors.surface2
    // Highlight neutro que barre: en light usamos border, en dark muted; ambos
    // a baja opacidad para leerse como luz, no como acento.
    val highlight = (if (colors.isLight) colors.border else colors.muted).copy(alpha = 0.55f)

    if (!animate) {
        // reduce-motion: placeholder estático en surface-2, sin barrido.
        Box(
            modifier
                .clip(clipShape)
                .background(base)
                .clearAndSetSemantics {},
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = SHIMMER_PERIOD_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "skeleton-progress",
    )

    Box(
        modifier
            .clip(clipShape)
            .drawWithCache {
                val bandWidth = size.width * 0.6f
                // La banda recorre desde fuera por la izquierda hasta fuera por
                // la derecha: una sola pasada de luz por ciclo.
                val travel = size.width + bandWidth
                val start = -bandWidth + travel * progress
                val brush =
                    Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(start, 0f),
                        end = Offset(start + bandWidth, 0f),
                    )
                onDrawBehind {
                    drawRect(color = base)
                    drawRect(brush = brush)
                }
            }
            .clearAndSetSemantics {},
    )
}

/**
 * ¿Están activas las animaciones? Falso en preview o si el usuario desactivó las
 * animaciones del sistema (Opciones de desarrollador / accesibilidad →
 * ANIMATOR_DURATION_SCALE = 0). Equivale al prefers-reduced-motion de la web.
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

/**
 * Contenedor de carga: anuncia "Cargando" UNA vez (liveRegion polite) y agrupa
 * las primitivas Skeleton, que ya se ocultan a sí mismas del lector. Úsalo como
 * raíz de cualquier composición de skeletons (card, fila, lista).
 *
 * @param loadingLabel texto anunciado por el lector (i18n, lo pone el llamador).
 * @param modifier modificador del contenedor.
 * @param content primitivas Skeleton que forman el molde.
 */
@Composable
fun SkeletonContainer(
    loadingLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier.semantics {
                contentDescription = loadingLabel
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        content()
    }
}

/**
 * Variante de composición skeleton-card: molde 1:1 de un Card real (surface-1 +
 * border 1px, sin sombra) con título, subtítulo, chip y avatar reservado.
 *
 * @param loadingLabel texto de carga anunciado (i18n).
 * @param modifier modificador del contenedor.
 */
@Composable
fun CardSkeleton(
    loadingLabel: String,
    modifier: Modifier = Modifier,
) {
    SkeletonContainer(loadingLabel = loadingLabel, modifier = modifier.fillMaxWidth()) {
        CardSkeletonMold()
    }
}

/**
 * Molde visual de una card (avatar + título + subtítulo + chip), SIN región de
 * carga propia: la región la declara UNA sola vez el contenedor padre, para que
 * el lector no anuncie "Cargando" por cada card.
 */
@Composable
private fun CardSkeletonMold(modifier: Modifier = Modifier) {
    val colors = RecreColors.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border1px(colors.border)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Skeleton(SkeletonShape.Circle, Modifier.size(40.dp)) // avatar reservado
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Skeleton(SkeletonShape.Line, Modifier.fillMaxWidth(0.6f).height(16.dp)) // título
            Spacer(Modifier.height(8.dp))
            Skeleton(SkeletonShape.Line, Modifier.fillMaxWidth(0.4f).height(13.dp)) // subtítulo
        }
        Spacer(Modifier.width(12.dp))
        Skeleton(SkeletonShape.Block, Modifier.width(72.dp).height(24.dp)) // chip
    }
}

/**
 * Variante skeleton-list: N cards apiladas como molde de una lista mientras
 * carga (p. ej. Locales = 3, ListaGestion = N). Una sola región "Cargando".
 *
 * @param loadingLabel texto de carga anunciado (i18n).
 * @param count número de cards molde a renderizar.
 * @param modifier modificador del contenedor.
 */
@Composable
fun ListSkeleton(
    loadingLabel: String,
    count: Int = 3,
    modifier: Modifier = Modifier,
) {
    SkeletonContainer(loadingLabel = loadingLabel, modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Molde sin región propia: la región de carga la declara este contenedor.
            repeat(count) { CardSkeletonMold() }
        }
    }
}

/** Borde 1px del color dado (helper local: la card del DS se eleva por borde, sin sombra). */
private fun Modifier.border1px(color: Color): Modifier =
    this.border(width = 1.dp, color = color, shape = RoundedCornerShape(12.dp))

// region Previews

@Preview(name = "Skeleton primitivas · light", showBackground = true)
@Composable
private fun SkeletonPrimitivesLightPreview() {
    RecreTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Skeleton(SkeletonShape.Line, Modifier.fillMaxWidth(0.7f).height(16.dp))
            Skeleton(SkeletonShape.Block, Modifier.fillMaxWidth().height(36.dp))
            Skeleton(SkeletonShape.Circle, Modifier.size(40.dp))
        }
    }
}

@Preview(name = "CardSkeleton · light", showBackground = true)
@Composable
private fun CardSkeletonLightPreview() {
    RecreTheme(darkTheme = false) {
        CardSkeleton(loadingLabel = "Cargando", modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "ListSkeleton · dark", showBackground = true)
@Composable
private fun ListSkeletonDarkPreview() {
    RecreTheme(darkTheme = true) {
        ListSkeleton(loadingLabel = "Cargando", count = 3, modifier = Modifier.padding(16.dp))
    }
}

// endregion
