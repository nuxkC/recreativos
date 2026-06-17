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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.recreSharedBounds
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recre.app.R
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
