package com.recre.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme
import com.recre.app.ui.theme.RecreType

/**
 * Eyebrow (S4, mockup «Neón de sala»): etiqueta mono uppercase de bajo peso
 * visual que titula secciones, labels de campo y subtítulos de cabecera.
 * El uppercase se aplica aquí para que los strings de i18n sigan en sentence
 * case (reutilizables fuera del eyebrow).
 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RecreColors.current.muted,
) {
    Text(
        text = text.uppercase(),
        style = RecreType.eyebrow,
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1B24)
@Composable
private fun EyebrowPreview() {
    RecreTheme { Eyebrow("Máquinas activas · 2") }
}
