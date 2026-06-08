package com.recre.app.feature.recaudacion.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Canvas Compose para capturar la firma del local.
 *
 * Almacena los strokes (cada uno una secuencia de [Offset]) en el callback
 * [onStrokeAppend], que el ViewModel acumula. La firma rasterizada a
 * Bitmap solo se hace en T-57 al persistir, no aquí, para no congelar
 * dato visual en el estado.
 *
 * Permite limpiar pasando `strokes = emptyList()` desde el padre.
 */
@Composable
fun SignaturePad(
    strokes: List<List<Offset>>,
    onStrokeAppend: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    backgroundColor: Color = Color(0xFFF5F5F5),
    strokeColor: Color = Color.Black,
    strokeWidth: Float = 4f,
) {
    var currentStroke by remember { mutableStateOf(listOf<Offset>()) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentStroke = listOf(offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentStroke = currentStroke + change.position
                    },
                    onDragEnd = {
                        if (currentStroke.size > 1) {
                            onStrokeAppend(currentStroke)
                        }
                        currentStroke = emptyList()
                    },
                    onDragCancel = {
                        currentStroke = emptyList()
                    },
                )
            },
    ) {
        // Strokes finalizados.
        for (stroke in strokes) {
            drawStroke(stroke, strokeColor, strokeWidth)
        }
        // Stroke en curso (mientras el dedo está sobre el canvas).
        drawStroke(currentStroke, strokeColor, strokeWidth)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    points: List<Offset>,
    color: Color,
    width: Float,
) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
