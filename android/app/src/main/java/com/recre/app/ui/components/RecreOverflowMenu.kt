package com.recre.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// =====================================================================
// RecreOverflowMenu · menú ⋮ de acciones secundarias (P3).
// SSOT: docs/superpowers/specs/2026-06-17-rediseno-ui-android-design.md §6.2.
//
// Las acciones PRIMARIAS de una entidad van visibles (RecreButton/StatusChip);
// las SECUNDARIAS (reportar avería, cambio de placa…) se pliegan aquí para que
// la tarjeta lidere con "estado + acción" sin un montón de botones. DropdownMenu
// M3 no está en la lista de Material prohibido, pero se envuelve para uniformar
// y para que el trigger sea el IconAction neutro del sistema.
// =====================================================================

/** Una acción del overflow: etiqueta ya localizada + callback. */
data class OverflowAccion(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * Menú ⋮ para las acciones secundarias de una entidad concreta. Trigger =
 * IconAction MoreVert (muted, neutro). [contentDescription] DEBE nombrar la
 * entidad ("Más acciones de {máquina}"), nunca el genérico "Más".
 */
@Composable
fun RecreOverflowMenu(
    contentDescription: String,
    acciones: List<OverflowAccion>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    IconAction(
        icon = Icons.Filled.MoreVert,
        contentDescription = contentDescription,
        onClick = { expanded = true },
        modifier = modifier,
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        acciones.forEach { accion ->
            DropdownMenuItem(
                text = { Text(accion.label) },
                enabled = accion.enabled,
                onClick = {
                    expanded = false
                    accion.onClick()
                },
            )
        }
    }
}
