package com.recre.app.feature.locales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.feature.locales.components.LocalCard
import com.recre.app.ui.components.EmptyState
import com.recre.app.ui.components.ListSkeleton
import com.recre.app.ui.components.RecreBottomBar
import com.recre.app.ui.components.RecreTonalButton
import com.recre.app.ui.components.RecreTopBar
import com.recre.app.ui.components.RecreTopBarActions
import com.recre.app.ui.components.SearchField
import com.recre.app.ui.components.TopLevelDestination
import com.recre.app.ui.theme.RecreMotion
import com.recre.app.ui.theme.RecreShapes
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalesScreen(
    viewModel: LocalesViewModel,
    onLocalClick: (String) -> Unit,
    onAlertasClick: () -> Unit,
    onIncidenciasClick: () -> Unit,
    onSelectTab: (TopLevelDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()

    // Refrescamos el conteo de alertas (T-64) al volver de cualquier
    // pantalla secundaria (alertas, histórico, ajustes…) para que el
    // badge del menú overflow esté al día sin polling ni realtime.
    LifecycleResumeEffect(Unit) {
        viewModel.refrescarAlertas()
        onPauseOrDispose { /* nada */ }
    }

    Scaffold(
        topBar = {
            RecreTopBar(
                titulo = state.empresaNombre.ifEmpty { stringResource(R.string.locales_titulo) },
                subtitulo = formatSubtitulo(
                    cargando = state.cargandoSync,
                    ultima = state.ultimaSync,
                    pendientes = state.pendientes,
                ),
                actions = { RecreTopBarActions(onAlertasClick = onAlertasClick, onIncidenciasClick = onIncidenciasClick) },
            )
        },
        bottomBar = {
            RecreBottomBar(current = TopLevelDestination.LOCALES, onSelect = onSelectTab)
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
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.syncStale) {
                    SyncStaleBanner(
                        onSincronizar = viewModel::refrescar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.locales_buscar_placeholder),
                    clearContentDescription = stringResource(R.string.action_clear),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                val filtrados = state.localesFiltrados
                when {
                    state.locales.isEmpty() && state.cargandoSync ->
                        ListSkeleton(
                            loadingLabel = stringResource(R.string.locales_vacio_cargando),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        )
                    filtrados.isEmpty() -> {
                        val conQuery = state.query.isNotBlank()
                        EmptyState(
                            icon = Icons.Filled.Storefront,
                            title = stringResource(
                                if (conQuery) R.string.locales_vacio_busqueda else R.string.locales_vacio_sin_locales,
                            ),
                            description = stringResource(
                                if (conQuery) R.string.locales_vacio_busqueda_desc else R.string.locales_vacio_descripcion,
                            ),
                            filtered = conQuery,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtrados, key = { it.id }) { local ->
                            Box(Modifier.animateItem(placementSpec = RecreMotion.current.defaultSpatialSpec())) {
                                LocalCard(
                                    local = local,
                                    onClick = { onLocalClick(local.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStaleBanner(onSincronizar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RecreTonalButton(
                    text = stringResource(R.string.sync_force),
                    onClick = onSincronizar,
                )
            }
        }
    }
}



@Composable
private fun formatSubtitulo(cargando: Boolean, ultima: Instant?, pendientes: Int): String {
    val sync = formatUltimaSync(cargando, ultima)
    if (pendientes <= 0) return sync
    val pendientesText = pluralStringResource(
        id = R.plurals.locales_pendientes_count,
        count = pendientes,
        pendientes,
    )
    return "$sync · $pendientesText"
}

@Composable
private fun formatUltimaSync(sincronizando: Boolean, ultima: Instant?): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return when {
        sincronizando -> stringResource(R.string.sync_in_progress)
        ultima == null -> stringResource(R.string.sync_never)
        else -> {
            val minutes = Duration.between(ultima, Instant.now()).toMinutes()
            when {
                minutes < 1 -> context.getString(R.string.sync_just_now)
                minutes < 60 -> context.getString(R.string.sync_minutes_ago, minutes)
                minutes < 60 * 24 -> context.getString(R.string.sync_hours_ago, minutes / 60)
                else -> context.getString(R.string.sync_days_ago, minutes / (60 * 24))
            }
        }
    }
}
