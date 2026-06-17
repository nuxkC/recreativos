package com.recre.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =====================================================================
// Design System "Confianza Industrial" — Grupo Espaciado (rediseño F0 · spec §3).
// Escala 4/8/12/16/24/32. Mismo patrón que RecreColors/RecreMotion: una constante
// + un CompositionLocal para consumo ergonómico (RecreSpacing.lg). El espaciado NO
// depende del tema claro/oscuro: hay un único valor, pero se expone por Local para
// que las pantallas no hardcodeen dp sueltos y el guardarraíl pueda señalarlos.
// =====================================================================

@Immutable
data class RecreSpacingScale(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/** Escala de espaciado de marca (valor único; no varía con claro/oscuro). */
val RecreSpacing = RecreSpacingScale()

/** Escala viva en el árbol; la instala [RecreTheme]. */
val LocalRecreSpacing = staticCompositionLocalOf { RecreSpacing }
