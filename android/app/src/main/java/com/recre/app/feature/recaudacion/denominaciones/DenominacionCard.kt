package com.recre.app.feature.recaudacion.denominaciones

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.theme.GeistMono
import com.recre.app.ui.theme.RecreColors

/**
 * Fila de una denominación en la lista del extracto (D.3-2/D.3-3). Sustituye al
 * tile de rejilla: [cara física] · «×N» (mono, ocupa el hueco) ··· [subtotal €
 * mono a la derecha]. Seleccionada → borde petróleo (vía [AppCard] `selected`).
 * Toda la fila es un destino tappable que activa la denominación para el keypad.
 * La cantidad va inline (`×N`), así que ya no hay chip flotante.
 */
@Composable
fun DenominacionCard(
    key: String,
    cantidad: Int,
    subtotal: String, // eq € ya formateado (formatEur)
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    AppCard(
        onClick = onSelect,
        selected = selected,
        contentDescription = "${etiquetaFacialDenominacion(key)}, $cantidad unidades, $subtotal",
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            DenominacionFace(key = key)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "×$cantidad",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = GeistMono, fontFeatureSettings = "tnum"),
                color = if (cantidad > 0) MaterialTheme.colorScheme.onSurface else colors.muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = subtotal,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistMono, fontFeatureSettings = "tnum"),
                color = if (cantidad > 0) MaterialTheme.colorScheme.onSurface else colors.muted,
            )
        }
    }
}

/**
 * Cara física: círculo (moneda) o rect redondeado (billete), con la etiqueta corta
 * centrada en Geist Mono. Sin tinte por valor (guardarraíl de tokens): la forma
 * distingue moneda de billete.
 */
@Composable
private fun DenominacionFace(key: String, modifier: Modifier = Modifier) {
    val colors = RecreColors.current
    val billete = esBillete(key)
    val shape = if (billete) RoundedCornerShape(6.dp) else CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .then(if (billete) Modifier.size(width = 44.dp, height = 30.dp) else Modifier.size(36.dp))
                .clip(shape)
                .background(colors.surface2)
                .border(1.5.dp, colors.border, shape),
    ) {
        Text(
            text = etiquetaCaraDenominacion(key),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = GeistMono, fontWeight = FontWeight.W600),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
