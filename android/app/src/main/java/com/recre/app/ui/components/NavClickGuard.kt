package com.recre.app.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Guard contra toques fantasma en la navegación.
 *
 * Durante la animación de salida (tras "atrás" o un navigate), la pantalla
 * saliente sigue compuesta y recibiendo toques, pero su NavBackStackEntry ya
 * bajó de RESUMED a STARTED. Sin guard, un tap rápido en ese instante dispara
 * la acción de la pantalla que se está yendo (p. ej. abre el detalle que
 * "había debajo" del dedo antes de dar atrás) o duplica navegaciones y pops
 * (doble-tap, doble-atrás).
 *
 * Dentro del NavHost, [LocalLifecycleOwner] es el NavBackStackEntry de la
 * pantalla, así que basta con descartar la invocación si ya no está RESUMED.
 * Mismo contrato que `androidx.lifecycle.compose.dropUnlessResumed`; se
 * redefine aquí para poder añadir la variante con parámetro sin ambigüedad
 * de imports.
 */
@Composable
fun dropUnlessResumed(block: () -> Unit): () -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    return {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            block()
        }
    }
}

/** Variante con un parámetro (ids de navegación, pestañas…) de [dropUnlessResumed]. */
@Composable
fun <T> dropUnlessResumed(block: (T) -> Unit): (T) -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    return { value ->
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            block(value)
        }
    }
}
