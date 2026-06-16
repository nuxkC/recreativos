package com.recre.app.feature.recaudacion.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.recre.app.R

/**
 * (A) Aviso EMERGENTE que interrumpe al técnico en cuanto la baseline cambia a
 * mitad del flujo —esté en el paso que esté (contadores, denominaciones,
 * confirmación)—, en vez de esperar a que llegue a confirmar y firmar.
 *
 * Se muestra una sola vez por flujo ([visible] lo controla el ViewModel con
 * `baselineCambiada && !avisoBaselineVisto`). "Rehacer la lectura" reinicia y
 * vuelve al paso de contadores; "Entendido" solo cierra el popup —el guardado
 * sigue bloqueado y el aviso inline de confirmación persiste—.
 */
@Composable
fun BaselineCambiadaDialog(
    visible: Boolean,
    onMarcarVisto: () -> Unit,
    onRehacer: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onMarcarVisto,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.recaudacion_baseline_cambiada_titulo)) },
        text = { Text(stringResource(R.string.recaudacion_baseline_cambiada_mensaje)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onMarcarVisto()
                    onRehacer()
                },
            ) {
                Text(stringResource(R.string.recaudacion_baseline_cambiada_accion))
            }
        },
        dismissButton = {
            TextButton(onClick = onMarcarVisto) {
                Text(stringResource(R.string.recaudacion_baseline_cambiada_descartar))
            }
        },
    )
}
