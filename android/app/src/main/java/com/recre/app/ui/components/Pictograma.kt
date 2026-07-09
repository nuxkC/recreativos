package com.recre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

/** Tono del pictograma: NEUTRO (informativo, acento) o ALERTA (error, danger). */
enum class PictogramaTono { NEUTRO, ALERTA }

/**
 * Pictograma de pantalla-estado (mockup: tile 74 radio 28 con icono grande), para
 * los estados vacíos/error del tramo 1 (sin acceso, error de sesión). Decorativo:
 * el título de la pantalla ya nombra el estado, así que no lleva contentDescription.
 */
@Composable
fun Pictograma(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tono: PictogramaTono = PictogramaTono.NEUTRO,
) {
    val c = RecreColors.current
    val tint = when (tono) {
        PictogramaTono.NEUTRO -> c.accentBright
        PictogramaTono.ALERTA -> c.danger
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(74.dp).clip(RoundedCornerShape(28.dp)).background(tint.copy(alpha = 0.12f)),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1B24)
@Composable
private fun PictogramaPreview() {
    RecreTheme { Pictograma(Icons.Filled.Lock) }
}
