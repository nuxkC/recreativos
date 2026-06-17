package com.recre.app.feature.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R

/**
 * Aviso ligero raíz (T-260, antes panel modal T-63). Si hay incidencias sin resolver
 * (recaudaciones o averías que no se subieron al servidor), muestra un aviso breve que
 * ENLAZA al Centro de Incidencias —ya no lista ni actúa sobre las filas: para eso está
 * la pantalla—. Se monta en la raíz ([com.recre.app.RecreApp]) sobre el NavHost, así
 * aparece esté donde esté el técnico.
 */
@Composable
fun ErroresSubidaDialogHost(
    onVerIncidencias: () -> Unit,
    viewModel: ErroresSubidaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible || state.count == 0) return

    AlertDialog(
        onDismissRequest = viewModel::cerrar,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.incidencias_aviso_titulo)) },
        text = {
            Text(
                text = pluralStringResource(
                    R.plurals.incidencias_aviso_texto,
                    state.count,
                    state.count,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.cerrar()
                    onVerIncidencias()
                },
            ) {
                Text(stringResource(R.string.incidencias_aviso_ver))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cerrar) {
                Text(stringResource(R.string.incidencias_aviso_ahora_no))
            }
        },
    )
}
