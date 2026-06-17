package com.recre.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreMotionDurations
import com.recre.app.ui.theme.RecreStandardEasing

/**
 * Destello de éxito (M3): superpone un velo verde que se desvanece en
 * [RecreMotionDurations.SUCCESS_FLASH_MS] cuando [trigger] cambia a un valor nuevo
 * (p. ej. el booleano "cuadra" pasando a true). El color va SIEMPRE acompañado de
 * texto/icono en el llamador (no es feedback solo-color). El SO desactiva la
 * animación bajo "quitar animaciones" (Compose respeta el animationScale).
 */
fun Modifier.successFlash(trigger: Any?): Modifier =
    composed {
        val alpha = remember { Animatable(0f) }
        val color = RecreColors.current.success
        LaunchedEffect(trigger) {
            if (trigger == null) return@LaunchedEffect
            alpha.snapTo(0.32f)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(RecreMotionDurations.SUCCESS_FLASH_MS, easing = RecreStandardEasing),
            )
        }
        drawWithContent {
            drawContent()
            if (alpha.value > 0f) drawRect(color = color.copy(alpha = alpha.value), size = size)
        }
    }

/**
 * Vibración de error (M3): sacude horizontalmente en
 * [RecreMotionDurations.DANGER_SHAKE_MS] cuando [trigger] cambia. Para campos/tarjetas
 * en descuadre o validación fallida; acompáñalo siempre de texto/color de error.
 */
fun Modifier.dangerShake(trigger: Any?): Modifier =
    composed {
        val offsetX = remember { Animatable(0f) }
        LaunchedEffect(trigger) {
            if (trigger == null) return@LaunchedEffect
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec =
                    keyframes {
                        durationMillis = RecreMotionDurations.DANGER_SHAKE_MS
                        0f at 0
                        -10f at 60
                        10f at 140
                        -6f at 220
                        6f at 300
                        0f at RecreMotionDurations.DANGER_SHAKE_MS
                    },
            )
        }
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(offsetX.value.toInt(), 0)
            }
        }
    }
