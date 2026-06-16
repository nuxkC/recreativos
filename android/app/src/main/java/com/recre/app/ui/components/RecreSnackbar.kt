package com.recre.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.ui.theme.RecreColors

/**
 * Rol del feedback transitorio (A-SNACKBAR-COLA, T-243). El estado NUNCA va solo
 * por color: cada rol lleva su icono. La cola offline NEUTRA no es un estado de
 * snackbar, la cubre [OfflineBadge] (surface-2 + muted, nunca danger/warning).
 */
enum class SnackbarEstado { Success, Warning, Error, Info, Loading }

/**
 * [SnackbarVisuals] con el [estado] de rol, para que [RecreSnackbarHost] pinte el
 * icono y el acento correctos. Por defecto el error y el loading (pending) son
 * `Indefinite` (sticky): no deben desvanecerse «al sol» antes de la acción.
 */
class RecreSnackbarVisuals(
    override val message: String,
    val estado: SnackbarEstado,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration =
        if (estado == SnackbarEstado.Error || estado == SnackbarEstado.Loading) {
            SnackbarDuration.Indefinite
        } else {
            SnackbarDuration.Short
        },
) : SnackbarVisuals

/** Muestra un snackbar tokenizado con [estado] de rol; suspende como `showSnackbar`. */
suspend fun SnackbarHostState.mostrar(
    estado: SnackbarEstado,
    mensaje: String,
    accionLabel: String? = null,
    withDismiss: Boolean = false,
) = showSnackbar(RecreSnackbarVisuals(mensaje, estado, accionLabel, withDismiss))

/**
 * Host de snackbar tokenizado (drop-in de [SnackbarHost]): superficie `surface-1`
 * + texto `foreground` + sombra de overlay y radio del tema. Cuando los visuals
 * son [RecreSnackbarVisuals] añade el icono de rol (success/warning/danger/info
 * o spinner `info` en loading); un snackbar legacy de solo texto se muestra
 * tokenizado sin icono (adopción incremental vía [mostrar]).
 */
@Composable
fun RecreSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState, modifier) { data ->
        val colors = RecreColors.current
        val sincronizando = stringResource(R.string.snackbar_sincronizando)
        val estado = (data.visuals as? RecreSnackbarVisuals)?.estado
        val icono: ImageVector? = when (estado) {
            SnackbarEstado.Success -> Icons.Filled.CheckCircle
            SnackbarEstado.Warning -> Icons.Filled.Warning
            SnackbarEstado.Error -> Icons.Filled.Error
            SnackbarEstado.Info -> Icons.Filled.Info
            SnackbarEstado.Loading, null -> null
        }
        val tint = when (estado) {
            SnackbarEstado.Success -> colors.success
            SnackbarEstado.Warning -> colors.warning
            SnackbarEstado.Error -> colors.danger
            else -> colors.info
        }

        Snackbar(
            modifier = Modifier.padding(12.dp),
            action = data.visuals.actionLabel?.let { label ->
                { TextButton(onClick = { data.performAction() }) { Text(label) } }
            },
            dismissAction = if (data.visuals.withDismissAction) {
                { TextButton(onClick = { data.dismiss() }) { Text(stringResource(R.string.action_cerrar)) } }
            } else {
                null
            },
            containerColor = MaterialTheme.colorScheme.surface, // surface-1
            contentColor = MaterialTheme.colorScheme.onSurface, // foreground
            actionContentColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (estado == SnackbarEstado.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .semantics { contentDescription = sincronizando },
                        strokeWidth = 2.dp,
                        color = colors.info,
                    )
                } else if (icono != null) {
                    Icon(icono, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Text(data.visuals.message)
            }
        }
    }
}
