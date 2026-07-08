package com.recre.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.GeistMono
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

/**
 * Fila de desglose `.f` del mockup «Neón de sala»: label a la izquierda, cifra
 * a la derecha en Geist Mono tabular, con separador hairline 1px opcional. Sin
 * card contenedora — vive sobre el fondo. [valueColor] tinta la cifra para dar
 * semántica (p. ej. retenido en aviso, parte empresa en acento); null = onSurface.
 */
@Composable
fun FilaHairline(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    emphasis: Boolean = false,
    hairline: Boolean = true,
) {
    val colors = RecreColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            Text(
                text = value,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = GeistMono,
                        fontFeatureSettings = "tnum",
                        fontWeight = if (emphasis) FontWeight.W600 else FontWeight.W500,
                    ),
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            )
        }
        if (hairline) {
            HorizontalDivider(thickness = 1.dp, color = colors.border)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1B24)
@Composable
private fun FilaHairlinePreview() {
    RecreTheme {
        Column {
            FilaHairline("Créditos netos", "+1.280")
            FilaHairline("Bruto estimado", "256,00 €", hairline = false, emphasis = true)
        }
    }
}
