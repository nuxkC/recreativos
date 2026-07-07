package com.recre.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Halo neón dibujado detrás del contenido. Se usa drawBehind con gradiente radial
 * porque minSdk 26 no soporta sombras de color (ambientShadowColor requiere API 28)
 * y elevation no admite tinte. El halo NO ocupa layout: se pinta fuera de bounds.
 */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 24.dp,
    alpha: Float = 0.35f,
): Modifier =
    drawBehind {
        val r = radius.toPx()
        val brush =
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = (maxOf(size.width, size.height) / 2f) + r,
            )
        drawRect(
            brush = brush,
            topLeft = Offset(-r, -r),
            size = size.copy(width = size.width + 2 * r, height = size.height + 2 * r),
        )
    }
