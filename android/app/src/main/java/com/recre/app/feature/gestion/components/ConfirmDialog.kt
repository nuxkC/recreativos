package com.recre.app.feature.gestion.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.recre.app.ui.components.RecreTextButton

/**
 * Diálogo de confirmación compartido por las listas del CRUD gestor
 * (borrado de licencia/máquina/local) — rediseño F3·P4.
 *
 * `AlertDialog` (no prohibido por el guardarraíl) con botones de la
 * librería propia (`RecreTextButton`), para no repetir el patrón en cada
 * lista ni recaer en `TextButton` de Material.
 */
@Composable
fun GestionConfirmDialog(
    titulo: String,
    mensaje: String,
    confirmarLabel: String,
    cancelarLabel: String,
    onConfirmar: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = { Text(mensaje) },
        confirmButton = {
            RecreTextButton(text = confirmarLabel, onClick = onConfirmar)
        },
        dismissButton = {
            RecreTextButton(text = cancelarLabel, onClick = onDismiss)
        },
    )
}
