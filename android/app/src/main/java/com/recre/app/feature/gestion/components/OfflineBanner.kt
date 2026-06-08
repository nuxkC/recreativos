package com.recre.app.feature.gestion.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recre.app.R

/**
 * Banner de "Sin conexión" para el CRUD gestor (T-70).
 *
 * Las operaciones de gestión (alta/edit/cierre/borrado de licencias,
 * máquinas, locales e instalaciones) **no se encolan offline** —
 * encolarlas crearía baselines inconsistentes y problemas de unicidad
 * difíciles de auditar. Solo las recaudaciones (T-57) viven offline,
 * porque son la verdad física del dinero recaudado.
 *
 * Cuando no hay red este banner se muestra arriba de la lista o el
 * formulario, los FAB/Guardar se deshabilitan y los snackbars de error
 * que llegan desde el repositorio reusan el copy `gestion_error_network`.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.gestion_offline_titulo),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.gestion_offline_descripcion),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
