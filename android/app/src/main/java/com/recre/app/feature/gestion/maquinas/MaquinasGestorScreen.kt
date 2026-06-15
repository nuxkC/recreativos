package com.recre.app.feature.gestion.maquinas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.local.entity.MaquinaEntity
import com.recre.app.feature.gestion.resolveErrorRes
import com.recre.app.ui.components.ListSkeleton
import com.recre.app.ui.theme.RecreMotion

/** Lista de máquinas (T-67). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaquinasGestorScreen(
    onBack: () -> Unit,
    onAlta: () -> Unit,
    onEditar: (String) -> Unit,
    onVerAverias: (String) -> Unit,
    viewModel: MaquinasGestorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var aBorrar by remember { mutableStateOf<MaquinaEntity?>(null) }
    val errorMessage = state.errorCode?.let { stringResource(resolveErrorRes(it)) }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message = msg)
        viewModel.consumirError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gestion_maquinas_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAlta,
                modifier = if (!state.online) Modifier.alpha(0.4f) else Modifier,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.gestion_alta))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
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
                com.recre.app.feature.gestion.components.OfflineBanner(
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = state.busqueda,
                onValueChange = viewModel::onBusquedaChange,
                label = { Text(stringResource(R.string.gestion_buscar)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val filtered = remember(state.maquinas, state.busqueda) {
                val q = state.busqueda.trim().lowercase()
                if (q.isEmpty()) state.maquinas
                else state.maquinas.filter {
                    it.numeroSerie.lowercase().contains(q) ||
                        (it.modelo ?: "").lowercase().contains(q) ||
                        (it.fabricante ?: "").lowercase().contains(q)
                }
            }

            when {
                state.cargando -> CenteredLoader()
                state.maquinas.isEmpty() -> CenteredText(R.string.gestion_maquinas_vacio)
                filtered.isEmpty() -> CenteredText(R.string.gestion_busqueda_vacia)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { m ->
                        Box(Modifier.animateItem(placementSpec = RecreMotion.current.defaultSpatialSpec())) {
                            MaquinaCard(
                                maquina = m,
                                borrando = state.borrando == m.id,
                                onEditar = { onEditar(m.id) },
                                onVerAverias = { onVerAverias(m.id) },
                                onEliminar = { aBorrar = m },
                            )
                        }
                    }
                }
            }
        }
    }

    aBorrar?.let { m ->
        AlertDialog(
            onDismissRequest = { aBorrar = null },
            title = { Text(stringResource(R.string.gestion_eliminar_titulo)) },
            text = {
                Text(
                    stringResource(
                        R.string.gestion_maquina_eliminar_descripcion,
                        m.numeroSerie,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.eliminar(m.id)
                    aBorrar = null
                }) { Text(stringResource(R.string.gestion_eliminar_confirmar)) }
            },
            dismissButton = {
                TextButton(onClick = { aBorrar = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MaquinaCard(
    maquina: MaquinaEntity,
    borrando: Boolean,
    onEditar: () -> Unit,
    onVerAverias: () -> Unit,
    onEliminar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(maquina.numeroSerie, style = MaterialTheme.typography.titleSmall)
            val subtitle = listOfNotNull(
                maquina.fabricante?.takeIf { it.isNotBlank() },
                maquina.modelo?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.gestion_maquina_valor, maquina.valorCredito),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.gestion_estado, maquina.estado),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditar, enabled = !borrando) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = onVerAverias, enabled = !borrando) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = stringResource(R.string.averia_historial_titulo),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEliminar, enabled = !borrando) {
                    if (borrando) CircularProgressIndicator(strokeWidth = 2.dp)
                    else Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredLoader() {
    // T-241: el listado en carga muestra un esqueleto, no un spinner a pantalla
    // completa (plan §3.1). Reutiliza el átomo ListSkeleton.
    ListSkeleton(loadingLabel = stringResource(R.string.cargando))
}

@Composable
private fun CenteredText(resId: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(resId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
