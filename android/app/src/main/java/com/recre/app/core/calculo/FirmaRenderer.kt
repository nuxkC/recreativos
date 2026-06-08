package com.recre.app.core.calculo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import androidx.compose.ui.geometry.Offset
import java.io.ByteArrayOutputStream

/**
 * Rasteriza la firma capturada por el [SignaturePad] a un PNG para
 * persistir/subir.
 *
 * La firma viaja como `List<List<Offset>>` mientras está en memoria
 * (cada lista es un trazo). Al guardar:
 *
 *  1. Calculamos el bounding box de los puntos para escalar y centrar.
 *  2. Pintamos sobre un Bitmap con fondo blanco y trazos negros, mismo
 *     stroke style que el SignaturePad (cap/join Round, ancho 4f).
 *  3. Comprimimos a PNG.
 *
 * Si no hay strokes (o solo strokes con < 2 puntos), devuelve un PNG
 * blanco — pero el ViewModel debería bloquear "Guardar" antes de llegar
 * aquí.
 */
object FirmaRenderer {

    private const val DEFAULT_WIDTH_PX = 600
    private const val DEFAULT_HEIGHT_PX = 240
    private const val PADDING_PX = 24f
    private const val STROKE_WIDTH = 4f

    /** Renderiza los strokes a PNG en `[width] × [height]` píxeles. */
    fun renderToPng(
        strokes: List<List<Offset>>,
        width: Int = DEFAULT_WIDTH_PX,
        height: Int = DEFAULT_HEIGHT_PX,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = STROKE_WIDTH
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val flat = strokes.filter { it.size >= 2 }
        if (flat.isEmpty()) {
            return bitmapToPng(bitmap)
        }

        val bounds = computeBounds(flat)
        val scale = computeScale(bounds, width, height)
        val translateX = (width - bounds.width() * scale) / 2f - bounds.minX * scale
        val translateY = (height - bounds.height() * scale) / 2f - bounds.minY * scale

        for (stroke in flat) {
            val path = Path()
            val first = stroke[0]
            path.moveTo(first.x * scale + translateX, first.y * scale + translateY)
            for (i in 1 until stroke.size) {
                val pt = stroke[i]
                path.lineTo(pt.x * scale + translateX, pt.y * scale + translateY)
            }
            canvas.drawPath(path, paint)
        }

        return bitmapToPng(bitmap)
    }

    /** Codifica los bytes PNG a base64 sin saltos de línea para enviarlo a la Edge Function. */
    fun toBase64(png: ByteArray): String =
        Base64.encodeToString(png, Base64.NO_WRAP)

    // -------------------------------------------------------------------- helpers

    private fun bitmapToPng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        fun width() = (maxX - minX).coerceAtLeast(1f)
        fun height() = (maxY - minY).coerceAtLeast(1f)
    }

    private fun computeBounds(strokes: List<List<Offset>>): Bounds {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (stroke in strokes) {
            for (pt in stroke) {
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
        }
        return Bounds(minX, minY, maxX, maxY)
    }

    private fun computeScale(bounds: Bounds, width: Int, height: Int): Float {
        val availableW = width - 2 * PADDING_PX
        val availableH = height - 2 * PADDING_PX
        val sx = availableW / bounds.width()
        val sy = availableH / bounds.height()
        return minOf(sx, sy).coerceAtLeast(0.1f)
    }
}
