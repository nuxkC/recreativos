package com.recre.app.feature.locales.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
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
import com.recre.app.feature.locales.formatearDireccionLocal

/**
 * Tarjeta de un local en la lista principal.
 *
 * Tap navega al detalle (`/local/{localId}`). N8: Row [nombre + meta de una
 * línea «dirección · N máquinas»] con el `StatusChip` de estado a la DERECHA;
 * sin chevron (la card ya es táctil).
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
                // N8: meta en una sola línea «dirección · N máquinas». El conteo de
                // máquinas SÍ lo expone LocalResumen (maquinasActivas), así que se une
                // a la dirección con el separador; si no hay dirección, solo el conteo.
                val direccion = formatearDireccionLocal(
                    local.calle,
                    local.codigoPostal,
                    local.comunidadAutonoma,
                )
                val maquinasText = pluralStringResource(
                    id = R.plurals.locales_maquinas_count,
                    count = local.maquinasActivas,
                    local.maquinasActivas,
                )
                val meta = if (!direccion.isNullOrBlank()) {
                    stringResource(R.string.locales_meta_separador, direccion, maquinasText)
                } else {
                    maquinasText
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (estado != null) {
                Spacer(Modifier.width(8.dp))
                EstadoAgendaChip(estado)
            }
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
        EstadoAgenda.PENDIENTE -> {
            role = StatusRole.INFO
            icon = Icons.Filled.Schedule
            labelRes = R.string.agenda_estado_pendiente
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
