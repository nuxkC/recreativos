package com.recre.app.feature.gestion.instalaciones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.feature.gestion.resolveErrorRes
import com.recre.app.ui.components.ListSkeleton

/** Lista del CRUD de Instalaciones (T-69). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalacionesGestorScreen(
    onBack: () -> Unit,
    onAlta: () -> Unit,
    onEditar: (String) -> Unit,
    viewModel: InstalacionesGestorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val errorMessage = state.errorCode?.let { stringResource(resolveErrorRes(it)) }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message = msg)
        viewModel.consumirError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gestion_instalaciones_titulo)) },
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
            Text(
                stringResource(R.string.gestion_instalaciones_solo_activas),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val filtered = remember(state.instalaciones, state.busqueda) {
                val q = state.busqueda.trim().lowercase()
                if (q.isEmpty()) state.instalaciones
                else state.instalaciones.filter {
                    it.maquinaNumeroSerie.lowercase().contains(q) ||
                        it.licenciaNumero.lowercase().contains(q) ||
                        it.localNombre.lowercase().contains(q)
                }
            }

            when {
                state.cargando -> CenteredLoader()
                state.instalaciones.isEmpty() -> CenteredText(R.string.gestion_instalaciones_vacio)
                filtered.isEmpty() -> CenteredText(R.string.gestion_busqueda_vacia)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        InstalacionItemCard(
                            item = item,
                            onEditar = { onEditar(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalacionItemCard(
    item: InstalacionItem,
    onEditar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.localNombre, style = MaterialTheme.typography.titleSmall)
            val maquinaText = listOfNotNull(
                item.maquinaNumeroSerie,
                item.maquinaModelo?.takeIf { it.isNotBlank() },
            ).joinToString(" — ")
            Text(
                stringResource(R.string.gestion_instalacion_maquina, maquinaText),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.gestion_instalacion_licencia, item.licenciaNumero),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(
                    R.string.gestion_instalacion_tasa_pct,
                    item.tasaSemanal,
                    item.porcentajeLocal,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onEditar,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
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
