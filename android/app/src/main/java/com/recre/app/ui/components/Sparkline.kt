package com.recre.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme
import java.math.BigDecimal

// Design System "Confianza Industrial" — atom F3-A-SPARKLINE (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// Mini-gráfica de tendencia inline (~10-20 puntos): línea + área sutil, sin ejes
// ni labels, para INSINUAR dirección junto a una cifra-héroe. Es estrictamente
// DECORATIVA/redundante: el dato real vive siempre en la cifra adyacente
// (MoneyText/KPI) + delta + título del host; el gráfico NUNCA es la única fuente
// del número. Por eso es aria-hidden (clearAndSetSemantics{}).
//
// Roles: Primary (default, acento de marca, cuenta para el ≤10% de pantalla),
// Money (success) y Alert (danger) SOLO cuando la serie significa literalmente
// dinero/cuadre o avería/descuadre; Neutral (muted) para deuda/saldo (deber NO
// es error) y series secundarias.
//
// Adaptaciones al stack real:
//  - `LocalRecreColors`/`LocalReducedMotion` del pseudocódigo NO existen: se usan
//    `RecreColors.current` (+ `colorScheme.primary`) y la detección real de
//    reduce-motion (ANIMATOR_DURATION_SCALE), como el resto de átomos.
//  - MONEY-SAFE: la serie entra como List<BigDecimal>; el `toDouble()` es SOLO
//    para coordenadas de pintura, JAMÁS para mostrar una cifra (eso es del host).

/** Rol cromático de la serie. success/danger SOLO con semántica de dinero/alerta. */
enum class SparkRole { Primary, Money, Alert, Neutral }

private val SparkEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Mini-gráfica decorativa de una serie ya provista (no la recalcula).
 *
 * Invisible para lectores (aria-hidden): la cifra/delta/título del host es la
 * fuente real del dato, y la dirección la refuerza el host con icono+texto (no
 * solo el color de la línea). Con <2 puntos no dibuja nada (reserva el alto). La
 * línea se "dibuja" de izquierda a derecha al aparecer, salvo reduce-motion.
 *
 * @param data serie money-safe (BigDecimal); solo se usa su forma, no su valor.
 * @param role color de la serie (Primary por defecto; Money/Alert solo con
 *   semántica real de dinero/alerta; Neutral para deuda/saldo/secundarias).
 * @param height alto del viewport (44 compacto · 68 dominante · 36 fila).
 * @param showArea pinta el gradiente de área 14%→0% bajo la línea.
 * @param showEndDot ancla el último valor con un punto del rol.
 * @param modifier modificador del contenedor (último parámetro).
 */
@Composable
fun Sparkline(
    data: List<BigDecimal>,
    role: SparkRole = SparkRole.Primary,
    height: Dp = 44.dp,
    showArea: Boolean = true,
    showEndDot: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val reduceMotion = !rememberAnimationsEnabled()

    val roleColor: Color =
        when (role) {
            SparkRole.Primary -> primary
            SparkRole.Money -> colors.success // relleno, no es texto
            SparkRole.Alert -> colors.danger
            SparkRole.Neutral -> colors.muted
        }

    // <2 puntos ⇒ estado vacío: no se pinta línea (el host muestra '0'/'—').
    if (data.size < 2) {
        Spacer(modifier.height(height))
        return
    }

    // Normalización en Double SOLO para coordenadas de pintura (no para el dato).
    val ys = data.map { it.toDouble() }
    val minV = ys.min()
    val maxV = ys.max()
    val flat = (maxV - minV) < 1e-9
    val effColor = if (flat) colors.muted else roleColor // serie plana = neutra

    val progress = remember(data) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(data, reduceMotion) {
        if (reduceMotion) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, tween(durationMillis = 180, easing = SparkEasing))
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {}, // decorativo: invisible para el lector
    ) {
        val w = size.width
        val h = size.height
        val padV = 2.dp.toPx() // evita recorte del stroke en max/min
        val usableH = h - padV * 2
        val n = ys.size
        val dx = if (n > 1) w / (n - 1) else w

        fun yAt(v: Double): Float {
            val t = if (flat) 0.5 else (v - minV) / (maxV - minV) // 0 abajo .. 1 arriba
            return (padV + (1.0 - t) * usableH).toFloat()
        }

        val pts = ys.mapIndexed { i, v -> Offset(i * dx, yAt(v)) }

        val linePath =
            Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }

        // El draw progresivo se logra recortando el ancho visible a progress.
        clipRect(left = 0f, top = 0f, right = w * progress.value, bottom = h) {
            if (showArea && !flat) {
                val areaPath =
                    Path().apply {
                        addPath(linePath)
                        lineTo(pts.last().x, h)
                        lineTo(pts.first().x, h)
                        close()
                    }
                drawPath(
                    path = areaPath,
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(effColor.copy(alpha = 0.14f), effColor.copy(alpha = 0f)),
                            startY = 0f,
                            endY = h,
                        ),
                )
            }
            drawPath(
                path = linePath,
                color = effColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // El punto final ancla el "ahora"; aparece al completarse el draw.
        if (showEndDot && progress.value >= 0.999f) {
            drawCircle(color = effColor, radius = 2.dp.toPx(), center = pts.last()) // Ø4dp
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

private val demoSerie =
    listOf("10", "12", "11", "15", "14", "18", "17", "22", "20", "26")
        .map { BigDecimal(it) }

@Preview(name = "Sparkline primary · light", showBackground = true, widthDp = 160)
@Composable
private fun SparklinePrimaryLightPreview() {
    RecreTheme(darkTheme = false) {
        Sparkline(data = demoSerie, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "Sparkline neutral · dark", showBackground = true, widthDp = 160)
@Composable
private fun SparklineNeutralDarkPreview() {
    RecreTheme(darkTheme = true) {
        Sparkline(
            data = demoSerie,
            role = SparkRole.Neutral,
            showArea = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
