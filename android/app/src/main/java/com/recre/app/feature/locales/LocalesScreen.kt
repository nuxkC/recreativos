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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.recre.app.ui.components.RecreBottomBar
import com.recre.app.ui.components.RecreTopBarActions
import com.recre.app.ui.components.TopLevelDestination
import com.recre.app.ui.theme.RecreMotion
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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.empresaNombre.ifEmpty {
                                stringResource(R.string.locales_titulo)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatSubtitulo(
                                cargando = state.cargandoSync,
                                ultima = state.ultimaSync,
                                pendientes = state.pendientes,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.pendientes > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = { RecreTopBarActions(onAlertasClick = onAlertasClick, onIncidenciasClick = onIncidenciasClick) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
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
                BuscadorLocales(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                val filtrados = state.localesFiltrados
                if (filtrados.isEmpty()) {
                    EmptyState(
                        cargando = state.locales.isEmpty() && state.cargandoSync,
                        conQuery = state.query.isNotBlank(),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
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
private fun BuscadorLocales(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.locales_buscar_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        singleLine = true,
    )
}

@Composable
private fun SyncStaleBanner(onSincronizar: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
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
                TextButton(onClick = onSincronizar) {
                    Text(stringResource(R.string.sync_force))
                }
            }
        }
    }
}



@Composable
private fun EmptyState(
    cargando: Boolean,
    conQuery: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = when {
                    cargando -> stringResource(R.string.locales_vacio_cargando)
                    conQuery -> stringResource(R.string.locales_vacio_busqueda)
                    else -> stringResource(R.string.locales_vacio_sin_locales)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            if (!cargando && !conQuery) {
                Text(
                    text = stringResource(R.string.locales_vacio_descripcion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
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
