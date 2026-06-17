package com.recre.app.feature.recaudacion.denominaciones

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.theme.PillShape
import com.recre.app.ui.theme.RecreType

/**
 * Tarjeta compacta de una denominación (R3 del flujo de recaudación). Sólo el valor
 * facial, centrado, en Geist Mono tabular. Seleccionada → borde petróleo (vía
 * [AppCard] `selected`). El [cantidad] aparece como chip flotante straddling el borde
 * superior-derecho, con "pop" (scale+fade) al pasar de 0 (M2). Toda la tarjeta es un
 * destino tappable que activa la fila para el keypad.
 */
@Composable
fun DenominacionCard(
    etiqueta: String,
    cantidad: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AppCard(
            onClick = onSelect,
            selected = selected,
            contentDescription = "$etiqueta, $cantidad unidades",
            contentPadding = PaddingValues(vertical = 22.dp, horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = etiqueta,
                    style = RecreType.cifra,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        AnimatedVisibility(
            visible = cantidad > 0,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-10).dp) // straddle: sobresale ~media altura
                    .clearAndSetSemantics {}, // la cantidad ya va en el contentDescription
        ) {
            Text(
                text = cantidad.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.primary, PillShape) // fondo píldora
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
