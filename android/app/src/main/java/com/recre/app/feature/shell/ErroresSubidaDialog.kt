package com.recre.app.feature.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R

/**
 * Popup central (T-63 parcial · B) que avisa al técnico cuando una recaudación
 * de la cola NO se pudo subir y por qué. Se monta en la raíz ([RecreApp]) sobre
 * el NavHost, así aparece esté donde esté el técnico. Muestra los rechazos de uno
 * en uno; al cerrar, se reconoce y, si hay otro, se muestra el siguiente.
 */
@Composable
fun ErroresSubidaDialogHost(
    viewModel: ErroresSubidaViewModel = hiltViewModel(),
) {
    val error by viewModel.errorActual.collectAsStateWithLifecycle()
    val actual = error ?: return

    AlertDialog(
        onDismissRequest = { viewModel.descartar(actual.id) },
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.error_subida_titulo)) },
        text = {
            Column {
                Text(stringResource(R.string.error_subida_mensaje))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = actual.motivo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.descartar(actual.id) }) {
                Text(stringResource(R.string.error_subida_aceptar))
            }
        },
    )
}
