package com.recre.app.feature.gestion.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.recre.app.ui.components.StatusChip
import com.recre.app.ui.components.StatusChipSize
import com.recre.app.ui.components.StatusRole

/**
 * Chips de estado para las listas del CRUD gestor (rediseño F3·P4): "estado
 * = color + texto", nunca solo color. El catálogo de estados vive en
 * `GestionShared` (`ESTADOS_LICENCIA`, `ESTADOS_MAQUINA`).
 */
@Composable
fun LicenciaEstadoChip(estado: String, modifier: Modifier = Modifier) {
    val (role, icon) = when (estado) {
        "activa" -> StatusRole.SUCCESS to Icons.Filled.CheckCircle
        "suspendida" -> StatusRole.WARNING to Icons.Filled.Schedule
        "caducada" -> StatusRole.DANGER to Icons.Filled.Warning
        else -> StatusRole.NEUTRAL to Icons.Filled.HelpOutline // baja
    }
    StatusChip(
        role = role,
        label = estado.replaceFirstChar { it.uppercase() },
        icon = icon,
        size = StatusChipSize.SM,
        modifier = modifier,
    )
}

@Composable
fun MaquinaEstadoChip(estado: String, modifier: Modifier = Modifier) {
    val (role, icon) = when (estado) {
        "instalada" -> StatusRole.SUCCESS to Icons.Filled.CheckCircle
        "averiada" -> StatusRole.DANGER to Icons.Filled.Warning
        else -> StatusRole.NEUTRAL to Icons.Filled.HelpOutline // almacen, baja
    }
    StatusChip(
        role = role,
        label = estado.replaceFirstChar { it.uppercase() },
        icon = icon,
        size = StatusChipSize.SM,
        modifier = modifier,
    )
}
