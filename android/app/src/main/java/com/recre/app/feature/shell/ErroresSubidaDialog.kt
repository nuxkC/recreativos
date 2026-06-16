package com.recre.app.feature.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
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
 * Panel central (T-63 · mejora C) que LISTA las recaudaciones de la cola que no se
 * pudieron subir, con su motivo, y deja al técnico Reintentar o Descartar cada una.
 * Se monta en la raíz ([com.recre.app.RecreApp]) sobre el NavHost, así aparece esté
 * donde esté. Sustituye al antiguo aviso de "una en una": ahora se ven TODAS.
 */
@Composable
fun ErroresSubidaDialogHost(
    viewModel: ErroresSubidaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible || state.bloqueadas.isEmpty()) return

    AlertDialog(
        onDismissRequest = viewModel::cerrar,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.cola_bloqueadas_titulo, state.bloqueadas.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.cola_bloqueadas_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.bloqueadas.forEach { bloqueada ->
                        BloqueadaItem(
                            item = bloqueada,
                            onReintentar = { viewModel.reintentar(bloqueada.id) },
                            onDescartar = { viewModel.descartar(bloqueada.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::cerrar) {
                Text(stringResource(R.string.cola_bloqueadas_cerrar))
            }
        },
    )
}

@Composable
private fun BloqueadaItem(
    item: RecaudacionBloqueadaUi,
    onReintentar: () -> Unit,
    onDescartar: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.etiqueta,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.cola_bloqueadas_importe, item.importe),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = item.motivo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDescartar) {
                    Text(stringResource(R.string.cola_bloqueadas_descartar))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onReintentar) {
                    Text(stringResource(R.string.cola_bloqueadas_reintentar))
                }
            }
        }
    }
}
