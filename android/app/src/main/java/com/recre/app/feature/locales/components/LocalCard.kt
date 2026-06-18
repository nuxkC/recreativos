package com.recre.app.feature.locales.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.StatusChip
import com.recre.app.ui.components.StatusChipSize
import com.recre.app.ui.components.StatusRole
import com.recre.app.ui.components.recreSharedBounds
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.core.data.repository.EstadoAgenda
import com.recre.app.core.data.repository.LocalResumen

/**
 * Tarjeta de un local en la lista principal.
 *
 * Tap navega al detalle (`/local/{localId}`). Muestra:
 * - Nombre (titular)
 * - Dirección (subtítulo, single line elipsado)
 * - Recuento de máquinas activas con plural ICU.
 */
@Composable
fun LocalCard(
    local: LocalResumen,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Estado de agenda (Planificación P3c). null = sin agenda (offline) → sin chip.
    estado: EstadoAgenda? = null,
) {
    AppCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = local.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // T-244: comparte el nombre con el título del detalle (no-op si no hay scopes).
                    modifier = Modifier.recreSharedBounds("local-nombre-${local.id}"),
                )
                if (!local.direccion.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = local.direccion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = pluralStringResource(
                        id = R.plurals.locales_maquinas_count,
                        count = local.maquinasActivas,
                        local.maquinasActivas,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (local.maquinasActivas == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                if (estado != null) {
                    Spacer(Modifier.height(6.dp))
                    EstadoAgendaChip(estado)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Chip de estado de agenda del local (Planificación P3c). El estado nunca se
 *  comunica solo por color: el StatusChip lleva siempre icono. */
@Composable
private fun EstadoAgendaChip(estado: EstadoAgenda) {
    val role: StatusRole
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val labelRes: Int
    when (estado) {
        EstadoAgenda.ATRASADO -> {
            role = StatusRole.DANGER
            icon = Icons.Filled.Warning
            labelRes = R.string.agenda_estado_atrasado
        }
        EstadoAgenda.TOCA_HOY -> {
            role = StatusRole.WARNING
            icon = Icons.Filled.Today
            labelRes = R.string.agenda_estado_toca_hoy
        }
        EstadoAgenda.AL_DIA -> {
            role = StatusRole.SUCCESS
            icon = Icons.Filled.CheckCircle
            labelRes = R.string.agenda_estado_al_dia
        }
        EstadoAgenda.SIN_PLANIFICAR -> {
            role = StatusRole.NEUTRAL
            icon = Icons.Filled.HelpOutline
            labelRes = R.string.agenda_estado_sin_planificar
        }
    }
    StatusChip(
        role = role,
        label = stringResource(labelRes),
        icon = icon,
        size = StatusChipSize.SM,
    )
}
