package com.recre.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Vocabulario de motion del plan de diseño (T-230 · §2.4/§3.1), materializado de
 * forma **estable** sobre Compose BOM 2025.12 / material3 1.4.0 — donde el API
 * oficial (`MaterialExpressiveTheme` / `MotionScheme.expressive()`) todavía es
 * `internal` y solo se hará público en material3 1.5.0 (Compose BOM 2026.x, T-258).
 *
 * Es el mismo patrón que [RecreColors]: un *slot* que Material 3 aún no expone, vivido
 * por `CompositionLocal`. Un único vocabulario compartido con la web (`motion` 12.x,
 * T-231): muelles físicos para lo **espacial** (posición/layout) y tweens cortos para
 * los **efectos** (color/opacidad). Regla del plan: *solo se anima lo que aclara, guía
 * o confirma*; 200–500 ms; el SO respeta "quitar animaciones".
 *
 * **Migración (T-258):** cuando llegue material3 1.5.0, sustituir `RecreMotion.current`
 * por `MaterialTheme.motionScheme` y `ExpressiveRecreMotion` por `MotionScheme.expressive()`.
 * Las firmas (`*SpatialSpec`/`*EffectsSpec`) se calcan a propósito para que el cambio
 * sea mecánico.
 */
@Immutable
interface RecreMotionScheme {
    /** `spatial.fast` — feedback de tap, chips, thumbs: muelle rígido con vida mínima. */
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T>

    /** `spatial.default` — entrada de cards y cambios de layout: muelle medio. */
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T>

    /** `effects.default` — color, opacidad, crossfade: tween 250 ms (no muelle). */
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T>
}

/**
 * Esquema expresivo por defecto (equivalente a `MotionScheme.expressive()`): springs
 * con un punto de rebote para lo espacial; tween con `FastOutSlowInEasing` para efectos.
 */
val ExpressiveRecreMotion: RecreMotionScheme =
    object : RecreMotionScheme {
        override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            )

        override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )

        override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
            tween(durationMillis = 250, easing = FastOutSlowInEasing)
    }

/** Esquema de motion vivo en el árbol; lo instala [RecreTheme]. */
val LocalRecreMotion = staticCompositionLocalOf { ExpressiveRecreMotion }

/** Accesor de conveniencia, espejo de [RecreColors]: `RecreMotion.current.fastSpatialSpec()`. */
object RecreMotion {
    val current: RecreMotionScheme
        @Composable @ReadOnlyComposable
        get() = LocalRecreMotion.current
}
