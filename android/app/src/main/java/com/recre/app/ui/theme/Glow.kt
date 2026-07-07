package com.recre.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Halo neón dibujado detrás del contenido. Se usa drawBehind con gradiente radial
 * porque minSdk 26 no soporta sombras de color (ambientShadowColor requiere API 28)
 * y elevation no admite tinte. El halo NO ocupa layout: se pinta fuera de bounds.
 *
 * El degradado se dibuja como una ELIPSE (no un círculo): debe morir a distancia
 * `radius` del borde en AMBOS ejes, de modo que en elementos alargados el eje corto
 * no termine en un corte duro.
 */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 24.dp,
    alpha: Float = 0.35f,
): Modifier =
    drawBehind {
        val r = radius.toPx()
        val centro = Offset(size.width / 2f, size.height / 2f)
        val rx = size.width / 2f + r
        val ry = size.height / 2f + r
        // Elipse, no círculo: el degradado debe morir a distancia r del borde en AMBOS
        // ejes. Con radio escalar, en elementos alargados el eje corto terminaba en un
        // corte duro. Se dibuja un círculo unitario y se escala el canvas por eje.
        scale(scaleX = rx, scaleY = ry, pivot = centro) {
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = centro,
                        radius = 1f,
                    ),
                radius = 1f,
                center = centro,
            )
        }
    }
