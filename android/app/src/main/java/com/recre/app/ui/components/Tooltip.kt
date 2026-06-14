package com.recre.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme

// Design System "Confianza Industrial" — atom-tooltip (Fase 3).
// SSOT: .kiro/specs/recre/fase3-component-specs.md.
//
// Etiqueta contextual NO interactiva que revela el nombre/acción de un control
// compacto (IconButton del TopBar, acción de fila, chip…). Es PROGRESIVA y
// REDUNDANTE: aclara algo ya codificado de otra forma (icono reconocible +
// contentDescription del trigger) y JAMÁS encierra información crítica/única.
// Rol semánticamente NEUTRO: overlay neutro de alto contraste, NUNCA
// success/danger/warning/secondary/primary (el estado se comunica con
// StatusChip/MoneyText, no con el color de un tooltip).
//
// Android no tiene hover: se invoca por long-press (o foco D-pad) vía
// TooltipBox/PlainTooltip M3, con auto-dismiss ~1.5s. El [content] (trigger)
// conserva SIEMPRE su propio contentDescription; el tooltip no lo sustituye.
//
// Adaptaciones al stack real:
//  - El overlay del spec (inverse-surface light / surface-2 dark) se deriva de
//    tokens reales: en light burbuja = foreground (#11161B) con texto background
//    (#FAFBFC) = 16:1; en dark burbuja = surface-2 (#1B1E24) con texto foreground
//    = ~12:1. Opaco: el contraste no depende de la superficie de debajo.
//  - El caption del spec (13sp/500) se mapea a labelMedium (Geist Sans).

/**
 * Tooltip neutro por long-press / foco sobre un control compacto.
 *
 * El [content] es el trigger y debe llevar su propio contentDescription: el
 * tooltip es redundante, nunca el único portador del nombre. El overlay es
 * neutro (state-neutral), jamás coloreado por estado.
 *
 * @param text texto corto (≤2 líneas): nombre de la acción, unidad o valor
 *   truncado. i18n por el llamador. Sin botones ni enlaces (eso es un Popover).
 * @param modifier modificador del contenedor del trigger.
 * @param content el control anfitrión (conserva su propio contentDescription).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecreTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = RecreColors.current
    // Overlay neutro opaco: light = burbuja oscura (foreground) / texto claro
    // (background); dark = surface-2 elevada / texto foreground. Nunca un rol.
    val container =
        if (colors.isLight) MaterialTheme.colorScheme.onSurface else colors.surface2
    val contentColor =
        if (colors.isLight) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface

    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberPlainTooltipPositionProvider(
                spacingBetweenTooltipAndAnchor = 4.dp, // = +4px del motion popover de marca
            ),
        state = rememberTooltipState(isPersistent = false), // auto-dismiss ~1.5s
        modifier = modifier,
        tooltip = {
            PlainTooltip(
                containerColor = container,
                contentColor = contentColor,
                shape = RoundedCornerShape(8.dp), // radio pequeño de overlay, nunca pill/card
                shadowElevation = 3.dp, // overlays SÍ llevan sombra (excepción a "borde, no sombra")
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium, // caption 13/500 Geist Sans
                    maxLines = 2,
                )
            }
        },
        content = content,
    )
}

// ---------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------

@Preview(name = "Tooltip · light", showBackground = true)
@Composable
private fun RecreTooltipLightPreview() {
    RecreTheme(darkTheme = false) {
        RecreTooltip(text = "Notificaciones") {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notificaciones", // el trigger conserva su label
                )
            }
        }
    }
}

@Preview(name = "Tooltip · dark", showBackground = true)
@Composable
private fun RecreTooltipDarkPreview() {
    RecreTheme(darkTheme = true) {
        RecreTooltip(text = "Notificaciones") {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notificaciones",
                )
            }
        }
    }
}
