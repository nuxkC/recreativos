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
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.EmptyState
import com.recre.app.ui.components.FilterChipModel
import com.recre.app.ui.components.FilterChipRow
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
                // Agenda (Planificación P3c): héroe "por recaudar" + filtros. Solo
                // cuando la agenda se cargó (online); offline cae a la lista plana.
                if (state.agendaDisponible) {
                    AgendaHero(
                        pendientes = state.localesPendientes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    FilterChipRow(
                        chips = listOf(
                            FilterChipModel(
                                key = FILTRO_PENDIENTES,
                                label = stringResource(R.string.agenda_filtro_pendientes),
                                count = state.localesPendientes,
                            ),
                            FilterChipModel(
                                key = FILTRO_AL_DIA,
                                label = stringResource(R.string.agenda_filtro_al_dia),
                                count = state.localesAlDia,
                            ),
                        ),
                        selectedKeys = state.filtro,
                        onToggle = viewModel::onFiltroToggle,
                        onClear = viewModel::onFiltroClear,
                        clearLabel = stringResource(R.string.action_clear),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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

                val items = state.itemsVisibles
                when {
                    state.locales.isEmpty() && state.cargandoSync ->
                        ListSkeleton(
                            loadingLabel = stringResource(R.string.locales_vacio_cargando),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        )
                    items.isEmpty() -> {
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
                        items(items, key = { it.local.id }) { item ->
                            Box(Modifier.animateItem(placementSpec = RecreMotion.current.defaultSpatialSpec())) {
                                LocalCard(
                                    local = item.local,
                                    onClick = { onLocalClick(item.local.id) },
                                    estado = if (state.agendaDisponible) item.estado else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Héroe de la agenda: cuántos locales del operario están por recaudar (P3c). Es
 *  un conteo de locales (entero), no dinero → no usa MoneyText. */
@Composable
private fun AgendaHero(pendientes: Int, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pendientes == 0) {
                Text(
                    text = stringResource(R.string.agenda_hero_todo_al_dia),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = pendientes.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = pluralStringResource(
                        id = R.plurals.agenda_hero_pendientes,
                        count = pendientes,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
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
