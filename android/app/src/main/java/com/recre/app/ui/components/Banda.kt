package com.recre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

/** Tono de la banda: INFO (dato de contexto, cian) o DEUDA (retención, aviso). */
enum class BandaTono { INFO, DEUDA }

/**
 * Banda de contexto de una línea (`banda.info` / `banda.deuda` del mockup): tile
 * con icono del tono + texto rico. Fondo surface, borde 1px del tono, radio 16.
 * El texto llega como AnnotatedString para poder resaltar cifras en negrita.
 */
@Composable
fun Banda(
    texto: AnnotatedString,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tono: BandaTono = BandaTono.INFO,
) {
    val colors = RecreColors.current
    val acento = when (tono) {
        BandaTono.INFO -> colors.infoText
        BandaTono.DEUDA -> colors.warningText
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, acento.copy(alpha = 0.45f), shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(10.dp)).background(acento.copy(alpha = 0.12f)),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = acento, modifier = Modifier.size(16.dp))
        }
        Text(text = texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1B24)
@Composable
private fun BandaPreview() {
    RecreTheme {
        Banda(texto = AnnotatedString("Última lectura: E 467.409 · S 335.343"), icon = Icons.Filled.Info)
    }
}
