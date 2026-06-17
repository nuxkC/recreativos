package com.recre.app.feature.locales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.recre.app.ui.components.recreSharedBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.repository.LocalDetalle
import com.recre.app.feature.locales.components.MaquinaCard
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreTonalButton
import com.recre.app.ui.theme.RecreShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDetalleScreen(
    viewModel: LocalDetalleViewModel,
    localId: String,
    onBack: () -> Unit,
    onRecaudarMaquina: (String) -> Unit,
    onCambioPlaca: (String) -> Unit,
    onReportarAveria: (String) -> Unit,
    onRecaudarTodas: (String) -> Unit,
    onVerDeudas: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = state.detalle?.local?.nombre ?: stringResource(R.string.local_detalle_titulo),
                onBack = onBack,
                // T-244: par compartido con el nombre de la card de la lista.
                tituloModifier = Modifier.recreSharedBounds("local-nombre-$localId"),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.cargandoSync,
            onRefresh = viewModel::refrescar,
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val detalle = state.detalle
            when {
                !state.cargado -> Loading()
                detalle == null -> NoEncontrado()
                else -> Contenido(
                    detalle = detalle,
                    syncStale = state.syncStale,
                    onRecaudarMaquina = onRecaudarMaquina,
                    onCambioPlaca = onCambioPlaca,
                    onReportarAveria = onReportarAveria,
                    onSincronizar = viewModel::refrescar,
                    onRecaudarTodas = onRecaudarTodas,
                    onVerDeudas = onVerDeudas,
                )
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.local_detalle_cargando),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoEncontrado() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.local_detalle_no_encontrado),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.local_detalle_no_encontrado_descripcion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Contenido(
    detalle: LocalDetalle,
    syncStale: Boolean,
    onRecaudarMaquina: (String) -> Unit,
    onCambioPlaca: (String) -> Unit,
    onReportarAveria: (String) -> Unit,
    onSincronizar: () -> Unit,
    onRecaudarTodas: (String) -> Unit,
    onVerDeudas: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (syncStale) {
            item("stale-banner") {
                SyncStaleBanner(onSincronizar = onSincronizar)
            }
        }
        item("cabecera") {
            CabeceraLocal(detalle = detalle)
        }
        item("deudas") {
            RecreTonalButton(
                text = stringResource(R.string.local_detalle_ver_deudas),
                onClick = onVerDeudas,
                fullWidth = true,
            )
        }
        if (detalle.maquinas.isNotEmpty() && !syncStale) {
            // Botón "Recaudar todas" disponible cuando hay máquinas activas y
            // el sync no está stale. Saltamos a la primera por orden.
            item("recaudar-todas") {
                val instaladas = detalle.maquinas.filter { it.estado == "instalada" }
                if (mostrarRecaudarTodas(instaladas.size)) {
                    RecrePrimaryButton(
                        text = stringResource(R.string.local_detalle_recaudar_todas, instaladas.size),
                        onClick = { onRecaudarTodas(instaladas.first().instalacionId) },
                    )
                }
            }
        }
        if (detalle.maquinas.isEmpty()) {
            item("sin-maquinas") {
                SinMaquinas()
            }
        } else {
            items(detalle.maquinas, key = { it.instalacionId }) { maquina ->
                MaquinaCard(
                    maquina = maquina,
                    syncStale = syncStale,
                    onRecaudarClick = { onRecaudarMaquina(maquina.instalacionId) },
                    onCambioPlacaClick = { onCambioPlaca(maquina.instalacionId) },
                    onReportarAveriaClick = { onReportarAveria(maquina.maquinaId) },
                )
            }
        }
    }
}

@Composable
private fun SyncStaleBanner(onSincronizar: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RecreShapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.locales_stale_titulo),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.locales_stale_descripcion),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            RecreTonalButton(
                text = stringResource(R.string.sync_force),
                onClick = onSincronizar,
            )
        }
    }
}

@Composable
private fun CabeceraLocal(detalle: LocalDetalle) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!detalle.local.direccion.isNullOrBlank()) {
            Text(
                text = detalle.local.direccion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (!detalle.local.titularNombre.isNullOrBlank()) {
            Text(
                text = stringResource(
                    R.string.local_detalle_titular,
                    detalle.local.titularNombre,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!detalle.local.telefono.isNullOrBlank()) {
            Text(
                text = stringResource(
                    R.string.local_detalle_telefono,
                    detalle.local.telefono,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.local_detalle_seccion_maquinas),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun SinMaquinas() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.local_detalle_sin_maquinas),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Recaudar todas" (modo cadena) solo tiene sentido con 2+ maquinas instaladas. */
internal fun mostrarRecaudarTodas(maquinasInstaladas: Int): Boolean = maquinasInstaladas > 1
