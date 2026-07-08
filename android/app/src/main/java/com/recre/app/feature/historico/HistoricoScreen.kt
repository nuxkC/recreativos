package com.recre.app.feature.historico

import com.recre.app.ui.components.formatEur

import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.repository.EstadoHistorico
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.LottieIllustration
import com.recre.app.ui.components.RecreBottomBar
import com.recre.app.ui.components.RecreTopBar
import com.recre.app.ui.components.RecreTopBarActions
import com.recre.app.ui.components.SearchField
import com.recre.app.ui.components.StatusChip
import com.recre.app.ui.components.StatusChipSize
import com.recre.app.ui.components.StatusRole
import com.recre.app.ui.components.TopLevelDestination
import com.recre.app.core.data.repository.RecaudacionHistorica
import com.recre.app.ui.theme.RecreMotion
import com.recre.app.ui.theme.RecreShapes
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
    onIncidenciasClick: () -> Unit,
    onRecaudacionClick: (String) -> Unit,
    viewModel: HistoricoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            RecreTopBar(
                titulo = stringResource(R.string.historico_titulo),
                actions = { RecreTopBarActions(onAlertasClick = onAlertasClick, onIncidenciasClick = onIncidenciasClick) },
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
            HistoricoListaContenido(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onRecaudacionClick = onRecaudacionClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Cuerpo común de la lista del histórico (buscador + error + lista o
 * vacío). Lo comparten el tab global ([HistoricoScreen]) y el drill-down
 * por local/máquina ([HistoricoContextoScreen]); cada uno aporta su
 * propio chrome (Scaffold) y su `PullToRefreshBox`.
 */
@Composable
internal fun HistoricoListaContenido(
    state: HistoricoUiState,
    onQueryChange: (String) -> Unit,
    onRecaudacionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Buscador(
            query = state.query,
            onQueryChange = onQueryChange,
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
                    Box(Modifier.animateItem(placementSpec = RecreMotion.current.defaultSpatialSpec())) {
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

@Composable
private fun Buscador(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = stringResource(R.string.historico_buscar_placeholder),
        clearContentDescription = stringResource(R.string.action_clear),
        modifier = modifier,
    )
}

@Composable
private fun HistoricoCard(
    recaudacion: RecaudacionHistorica,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatFecha(recaudacion.fecha),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    val (rol, icono, textoRes) = when {
                        recaudacion.conflictoPendiente ->
                            Triple(StatusRole.WARNING, Icons.Filled.Warning, R.string.historico_badge_conflicto)
                        recaudacion.estado == EstadoHistorico.Anulada ->
                            Triple(StatusRole.DANGER, Icons.Filled.Block, R.string.historico_badge_anulada)
                        else ->
                            Triple(StatusRole.SUCCESS, Icons.Filled.CheckCircle, R.string.historico_badge_firme)
                    }
                    StatusChip(
                        role = rol,
                        label = stringResource(textoRes),
                        icon = icono,
                        size = StatusChipSize.SM,
                    )
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
private fun ErrorCard(textRes: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RecreShapes.medium,
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
            // Animación Lottie sutil solo en el vacío "sin recaudaciones" (no en
            // carga ni en búsqueda sin resultados); con reduce-motion no aparece.
            if (!cargando && !conQuery && rememberAnimationsEnabled()) {
                LottieIllustration(
                    rawRes = R.raw.empty_historico,
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
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
private fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        val scale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        scale != 0f
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
