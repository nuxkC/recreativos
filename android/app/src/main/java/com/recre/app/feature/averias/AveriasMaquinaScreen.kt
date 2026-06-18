package com.recre.app.feature.averias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.EmptyState
import com.recre.app.ui.components.FieldText
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecreSnackbarHost
import com.recre.app.ui.components.RecreTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.repository.AveriaHistorial
import com.recre.app.feature.gestion.components.OfflineBanner
import com.recre.app.ui.components.ListSkeleton

/**
 * Historial de averías de una máquina (T-222), en gestión. Lectura en línea de
 * la hoja de vida (atraviesa instalaciones); permite cerrar averías abiertas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AveriasMaquinaScreen(
    onBack: () -> Unit,
    viewModel: AveriasMaquinaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var resolverDialog by remember { mutableStateOf<AveriaHistorial?>(null) }

    val errorMessage = state.errorCode?.let { stringResource(resolveAveriaErrorRes(it)) }
    val okMessage = state.mensajeOkRes?.let { stringResource(it) }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message = msg)
        viewModel.consumeError()
    }
    LaunchedEffect(okMessage) {
        val msg = okMessage ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message = msg)
        viewModel.consumeMensaje()
    }

    Scaffold(
        topBar = {
            TopAppBarAverias(onBack = onBack)
        },
        snackbarHost = { RecreSnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (!state.online) {
                OfflineBanner(modifier = Modifier.fillMaxWidth())
            }
            when {
                state.cargando -> CenteredLoader()
                state.averias.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Build,
                    title = stringResource(R.string.averia_historial_vacio),
                    description = stringResource(R.string.averia_historial_vacio_desc),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.averias, key = { it.id }) { averia ->
                        AveriaCard(
                            averia = averia,
                            resolviendo = state.resolviendo == averia.id,
                            puedeResolver = state.online && state.resolviendo == null,
                            onResolver = { resolverDialog = averia },
                        )
                    }
                }
            }
        }
    }

    resolverDialog?.let { averia ->
        ResolverDialog(
            onDismiss = { resolverDialog = null },
            onConfirm = { notas ->
                viewModel.resolver(averia.id, notas)
                resolverDialog = null
            },
        )
    }
}

@Composable
private fun TopAppBarAverias(onBack: () -> Unit) {
    RecreDetailTopBar(
        titulo = stringResource(R.string.averia_historial_titulo),
        onBack = onBack,
    )
}

@Composable
private fun AveriaCard(
    averia: AveriaHistorial,
    resolviendo: Boolean,
    puedeResolver: Boolean,
    onResolver: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = categoriaLabel(averia.categoria),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                EstadoAveriaBadge(estado = averia.estado)
            }
            Text(
                text = formatFechaAveria(averia.fechaReporte),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            averia.localNombre?.let { nombre ->
                Text(
                    text = stringResource(R.string.averia_en_local, nombre),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (averia.poneMaquinaFueraServicio) {
                Text(
                    text = stringResource(R.string.averia_fuera_servicio_marca),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!averia.descripcion.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(averia.descripcion, style = MaterialTheme.typography.bodyMedium)
            }

            if (averia.recambios.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.averia_recambios_titulo),
                    style = MaterialTheme.typography.labelMedium,
                )
                averia.recambios.forEach { r ->
                    Text(
                        text = buildString {
                            append("• ")
                            append(r.pieza)
                            append("  ×")
                            append(r.cantidad)
                            val coste = formatCoste(r.coste)
                            if (coste.isNotEmpty()) append("  ·  $coste")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (!averia.notas.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.averia_notas, averia.notas),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (averia.estado != "resuelta") {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (resolviendo) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(24.dp))
                    } else {
                        RecreTextButton(
                            text = stringResource(R.string.averia_accion_resolver),
                            onClick = onResolver,
                            enabled = puedeResolver,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolverDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var notas by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.averia_resolver_titulo)) },
        text = {
            Column {
                Text(stringResource(R.string.averia_resolver_descripcion))
                Spacer(Modifier.height(8.dp))
                FieldText(
                    value = notas,
                    onValueChange = { notas = it.take(1000) },
                    label = stringResource(R.string.averia_resolver_notas),
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            RecreTextButton(
                text = stringResource(R.string.averia_accion_resolver),
                onClick = { onConfirm(notas.trim().ifBlank { null }) },
            )
        },
        dismissButton = {
            RecreTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
    )
}

@Composable
private fun CenteredLoader() {
    // T-241: el listado en carga muestra un esqueleto, no un spinner a pantalla
    // completa (plan §3.1). Reutiliza el átomo ListSkeleton.
    ListSkeleton(loadingLabel = stringResource(R.string.cargando))
}

