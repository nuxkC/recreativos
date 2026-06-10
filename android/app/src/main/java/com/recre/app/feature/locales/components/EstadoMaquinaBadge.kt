package com.recre.app.feature.locales.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recre.app.R

private data class EstadoColors(
    val label: String,
    val container: Color,
    val content: Color,
)

/**
 * Badge compacto con el estado de una máquina.
 *
 * Mismos colores semánticos que la web
 * (`web/src/components/maquinas/estado-badge.tsx`):
 *
 * - `instalada` → primary container (success)
 * - `almacen`   → surface variant (muted)
 * - `averiada`  → tertiary container (warning)
 * - `baja`      → error container (destructive)
 */
@Composable
fun EstadoMaquinaBadge(
    estado: String,
    modifier: Modifier = Modifier,
) {
    val colors = when (estado) {
        "instalada" -> EstadoColors(
            label = stringResource(R.string.maquina_estado_instalada),
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        "almacen" -> EstadoColors(
            label = stringResource(R.string.maquina_estado_almacen),
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        "averiada" -> EstadoColors(
            label = stringResource(R.string.maquina_estado_averiada),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        "baja" -> EstadoColors(
            label = stringResource(R.string.maquina_estado_baja),
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> EstadoColors(
            label = estado,
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(
        text = colors.label,
        style = MaterialTheme.typography.labelSmall,
        color = colors.content,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.container)
            .padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp)),
    )
}
