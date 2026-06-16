@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.recre.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import android.provider.Settings
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Transiciones de elemento compartido lista→detalle (T-244). El opt-in
 * experimental de Compose queda **aislado en este fichero**; los scopes se
 * inyectan por CompositionLocal en vez de cablearse por parámetro en cada
 * pantalla:
 *  - [LocalSharedTransitionScope]: lo provee el `SharedTransitionLayout` que
 *    envuelve el NavHost.
 *  - [LocalNavAnimatedVisibilityScope]: lo provee cada `composable(...)` del nav
 *    (su receiver es un `AnimatedContentScope`).
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Comparte los bounds de este elemento entre lista y detalle bajo [key] (la
 * misma clave en ambas pantallas casa el par; p. ej. `"local-nombre-$id"`).
 *
 * **Fallback** incorporado, como pide el plan (shared-element no obligatorio):
 * fuera de un `SharedTransitionLayout` / destino animado —preview, test, o un
 * destino que no provee los scopes— devuelve el Modifier intacto y la
 * navegación funciona igual, sin elemento compartido. Con las animaciones del
 * sistema desactivadas (reduced-motion) el cambio de bounds es instantáneo
 * (`snap`), no se anima. La decisión depende solo de la presencia de los scopes
 * (estable por posición), así que no introduce llamadas composables
 * condicionales.
 */
@Composable
fun Modifier.recreSharedBounds(key: Any): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val animationsOn = rememberSharedAnimationsEnabled()
    return with(sharedScope) {
        this@recreSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = { _, _ -> if (animationsOn) spring() else snap() },
        )
    }
}

/**
 * ¿Animaciones del sistema activas? Falso en preview o con
 * `ANIMATOR_DURATION_SCALE = 0` (reduced-motion). Copia local privada del mismo
 * criterio que el resto de átomos (Skeleton/OfflineBadge): cada fichero tiene la
 * suya para no exponer un símbolo de paquete que choque por sobrecarga.
 */
@Composable
private fun rememberSharedAnimationsEnabled(): Boolean {
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
