package com.recre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// RecreTheme: ColorScheme M3 (petróleo) + tokens de dominio (RecreColors).
// NO usa dynamicColor: la identidad de marca es fija (regla del proyecto).
// danger == error; info == tertiary ("sincronizando", no marca);
// surface-2 == surfaceVariant; border == outlineVariant; muted == onSurfaceVariant.

private val LightColorScheme =
    lightColorScheme(
        primary = RecrePrimaryLight,
        onPrimary = RecreOnPrimaryLight,
        primaryContainer = RecrePrimaryContainerLight,
        onPrimaryContainer = RecreOnPrimaryContainerLight,
        secondary = RecreSecondaryLight,
        onSecondary = RecreOnSecondaryContainerLight,
        secondaryContainer = RecreSecondaryContainerLight,
        onSecondaryContainer = RecreOnSecondaryContainerLight,
        tertiary = RecreInfoLight, // info = "sincronizando", no es marca
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = RecreInfoContainerLight,
        onTertiaryContainer = RecreOnInfoContainerLight,
        background = RecreBackgroundLight,
        onBackground = RecreOnSurfaceLight,
        surface = RecreSurface1Light, // surface = surface-1
        onSurface = RecreOnSurfaceLight,
        surfaceVariant = RecreSurface2Light, // surface-2 sutil
        onSurfaceVariant = RecreMutedLight, // texto secundario sobre surface
        surfaceContainerLowest = RecreSurface1Light,
        surfaceContainerLow = RecreSurface2Light,
        surfaceContainer = RecreSurface2Light,
        surfaceContainerHigh = Color(0xFFEDF0F3),
        surfaceContainerHighest = Color(0xFFE7EBEF),
        error = RecreDangerLight, // error == danger
        onError = Color(0xFFFFFFFF),
        errorContainer = RecreDangerContainerLight,
        onErrorContainer = RecreOnDangerContainerLight,
        outline = RecreMutedLight, // bordes con énfasis
        outlineVariant = RecreBorderLight, // separadores 1px (la mayoría)
        inverseSurface = RecreSurface1Dark,
        inverseOnSurface = RecreOnSurfaceDark,
        inversePrimary = RecrePrimaryDark,
        scrim = RecreScrim,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = RecrePrimaryDark,
        onPrimary = RecreOnPrimaryDark,
        primaryContainer = RecrePrimaryContainerDark,
        onPrimaryContainer = RecreOnPrimaryContainerDark,
        secondary = RecreSecondaryDark,
        onSecondary = RecreOnSecondaryContainerDark,
        secondaryContainer = RecreSecondaryContainerDark,
        onSecondaryContainer = RecreOnSecondaryContainerDark,
        tertiary = RecreInfoDark,
        onTertiary = Color(0xFF0A2247),
        tertiaryContainer = RecreInfoContainerDark,
        onTertiaryContainer = RecreOnInfoContainerDark,
        background = RecreBackgroundDark,
        onBackground = RecreOnSurfaceDark,
        surface = RecreSurface1Dark,
        onSurface = RecreOnSurfaceDark,
        surfaceVariant = RecreSurface2Dark,
        onSurfaceVariant = RecreMutedDark,
        surfaceContainerLowest = RecreBackgroundDark,
        surfaceContainerLow = RecreSurface1Dark,
        surfaceContainer = RecreSurface2Dark,
        surfaceContainerHigh = Color(0xFF22262D),
        surfaceContainerHighest = Color(0xFF2A2F37),
        error = RecreDangerDark,
        onError = RecreOnDangerFillDark, // DARK: oscuro sobre fill danger (blanco falla)
        errorContainer = RecreDangerContainerDark,
        onErrorContainer = RecreOnDangerContainerDark,
        outline = RecreMutedDark,
        outlineVariant = RecreBorderDark,
        inverseSurface = RecreSurface1Light,
        inverseOnSurface = RecreOnSurfaceLight,
        inversePrimary = RecrePrimaryLight,
        scrim = RecreScrim,
    )

@Composable
fun RecreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(
        LocalRecreColors provides recreSemanticColors(darkTheme),
        // Vocabulario de motion (T-230). Sustituirá a MaterialTheme.motionScheme
        // cuando material3 1.5.0 lo haga público (BOM 2026.x, T-258). Ver Motion.kt.
        LocalRecreMotion provides ExpressiveRecreMotion,
        // Escala de espaciado de marca (rediseño F0). Ver Spacing.kt.
        LocalRecreSpacing provides RecreSpacing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = RecreShapes, // grupo Forma (radios 12/16/20) → rediseño F0
            typography = Typography, // grupo Tipografía (Geist) → T-228
            content = content,
        )
    }
}
