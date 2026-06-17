package com.recre.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Ilustración animada propia (estados vacíos, onboarding, éxito) — rediseño F0.
 * El asset JSON vive en `res/raw` y se referencia por id; los assets concretos se
 * añaden al migrar cada pantalla (diferido a fases posteriores). Decorativa: el
 * llamador aporta el texto accesible alrededor, no la animación.
 */
@Composable
fun LottieIllustration(
    rawRes: Int,
    modifier: Modifier = Modifier.size(160.dp),
    iterations: Int = LottieConstants.IterateForever,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val progress by animateLottieCompositionAsState(composition, iterations = iterations)
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
}
