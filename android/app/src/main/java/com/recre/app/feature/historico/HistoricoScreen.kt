package com.recre.app.feature.historico

import com.recre.app.ui.components.formatEur

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.repository.EstadoHistorico
import com.recre.app.ui.components.RecreBottomBar
import com.recre.app.ui.components.RecreTopBarActions
import com.recre.app.ui.components.TopLevelDestination
import com.recre.app.core.data.repository.RecaudacionHistorica
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pantalla "Mis recaudaciones" (T-63).
 *
 * Listado de las recaudaciones del técnico autenticado, ordenadas por
 * fecha desc. Buscador por número de serie / nombre de local /
 * número de licencia. Pull-to-refresh para volver a pedir al backend.
 *
 * Cada fila muestra fecha + local + máquina + bruto + estado (firme /
 * anulada / conflicto pendiente). Tap → detalle, donde se reimprime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoScreen(
    onSelectTab: (TopLevelDestination) -> Unit,
    onAlertasClick: () -> Unit,
    onRecaudacionClick: (String) -> Unit,
    viewModel: HistoricoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.historico_titulo)) },
                actions = { RecreTopBarActions(onAlertasClick = onAlertasClick) },
            )
        },
        bottomBar = {
            RecreBottomBar(current = TopLevelDestination.HISTORICO, onSelect = onSelectTab)
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.cargando,
            onRefresh = viewModel::refrescar,
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Buscador(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                state.error?.let { code ->
                    ErrorCard(
                        textRes = errorTextoRes(code),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                val filtradas = state.filtradas
                if (filtradas.isEmpty()) {
                    EmptyState(
                        cargando = state.cargando,
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
                        items(filtradas, key = { it.id }) { rec ->
                            HistoricoCard(
                                recaudacion = rec,
                                onClick = { onRecaudacionClick(rec.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Buscador(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.historico_buscar_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
    )
}

@Composable
private fun HistoricoCard(
    recaudacion: RecaudacionHistorica,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatFecha(recaudacion.fecha),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    EstadoBadge(recaudacion = recaudacion)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = recaudacion.localNombre,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        recaudacion.maquinaSerie,
                        recaudacion.maquinaModelo,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.historico_label_bruto),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatEur(recaudacion.bruto),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.historico_label_parte_local),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatEur(recaudacion.parteLocal),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EstadoBadge(recaudacion: RecaudacionHistorica) {
    val (textRes, container, onContainer) = when {
        recaudacion.conflictoPendiente -> Triple(
            R.string.historico_badge_conflicto,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )

        recaudacion.estado == EstadoHistorico.Anulada -> Triple(
            R.string.historico_badge_anulada,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> Triple(
            R.string.historico_badge_firme,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Text(
            text = stringResource(textRes),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = onContainer,
        )
    }
}

@Composable
private fun ErrorCard(textRes: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = stringResource(textRes),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
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
                    cargando -> stringResource(R.string.historico_vacio_cargando)
                    conQuery -> stringResource(R.string.historico_vacio_busqueda)
                    else -> stringResource(R.string.historico_vacio_sin_recaudaciones)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun errorTextoRes(code: HistoricoErrorCode): Int = when (code) {
    HistoricoErrorCode.Network -> R.string.historico_error_network
    HistoricoErrorCode.Auth -> R.string.historico_error_auth
    HistoricoErrorCode.Unknown -> R.string.historico_error_generic
}

private fun formatFecha(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

// formatEur migrado al canónico de ui.components (money-safe, agrupación es-ES).
