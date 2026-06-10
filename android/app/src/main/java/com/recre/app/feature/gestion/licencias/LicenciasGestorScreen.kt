package com.recre.app.feature.gestion.licencias

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
import com.recre.app.core.data.local.entity.LicenciaEntity
import com.recre.app.feature.gestion.resolveErrorRes

/**
 * Lista de licencias para gestor+ (T-66). Buscador client-side, FAB de
 * alta y AlertDialog de confirmación de borrado por fila.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenciasGestorScreen(
    onBack: () -> Unit,
    onAlta: () -> Unit,
    onEditar: (String) -> Unit,
    viewModel: LicenciasGestorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var aBorrar by remember { mutableStateOf<LicenciaEntity?>(null) }
    val errorMessage = state.errorCode?.let { stringResource(resolveErrorRes(it)) }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message = msg)
        viewModel.consumirError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gestion_licencias_titulo)) },
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

            val filtered = remember(state.licencias, state.busqueda) {
                val q = state.busqueda.trim().lowercase()
                if (q.isEmpty()) state.licencias
                else state.licencias.filter {
                    it.numero.lowercase().contains(q) ||
                        (it.tipo ?: "").lowercase().contains(q) ||
                        (it.comunidadAutonoma ?: "").lowercase().contains(q)
                }
            }

            when {
                state.cargando -> Loading()
                state.licencias.isEmpty() -> EmptyMessage(R.string.gestion_licencias_vacio)
                filtered.isEmpty() -> EmptyMessage(R.string.gestion_busqueda_vacia)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { lic ->
                        LicenciaCard(
                            licencia = lic,
                            borrando = state.borrando == lic.id,
                            onEditar = { onEditar(lic.id) },
                            onEliminar = { aBorrar = lic },
                        )
                    }
                }
            }
        }
    }

    aBorrar?.let { lic ->
        AlertDialog(
            onDismissRequest = { aBorrar = null },
            title = { Text(stringResource(R.string.gestion_eliminar_titulo)) },
            text = {
                Text(stringResource(R.string.gestion_licencia_eliminar_descripcion, lic.numero))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.eliminar(lic.id)
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
private fun LicenciaCard(
    licencia: LicenciaEntity,
    borrando: Boolean,
    onEditar: () -> Unit,
    onEliminar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(licencia.numero, style = MaterialTheme.typography.titleSmall)
            licencia.tipo?.takeIf { it.isNotBlank() }?.let { tipo ->
                Text(
                    tipo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.gestion_estado, licencia.estado),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            licencia.fechaCaducidad?.takeIf { it.isNotBlank() }?.let { fc ->
                Text(
                    stringResource(R.string.gestion_licencia_caduca, fc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onEditar, enabled = !borrando) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(
                    onClick = onEliminar,
                    enabled = !borrando,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
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
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyMessage(stringRes: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(stringRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Resuelve un código i18n de error a su recurso. Útil cuando un caller
 * quiera mostrar el snackbar con copy localizado en lugar del raw code.
 */
@Composable
fun localizedError(code: String?): String? =
    code?.let { stringResource(resolveErrorRes(it)) }
