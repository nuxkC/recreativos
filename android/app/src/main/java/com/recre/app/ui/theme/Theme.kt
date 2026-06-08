package com.recre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Indigo500,
    onPrimary = Slate100,
    secondary = Slate700,
    background = Slate100,
    surface = Slate100,
    error = Red500,
)

private val DarkColors = darkColorScheme(
    primary = Indigo300,
    onPrimary = Slate900,
    secondary = Slate100,
    background = Slate900,
    surface = Slate900,
    error = Red500,
)

@Composable
fun RecreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
