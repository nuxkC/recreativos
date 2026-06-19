package com.recre.app.feature.historico

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.ui.components.RecreDetailTopBar

/**
 * Histórico acotado a un local o una máquina (drill-down desde sus
 * fichas). Reusa el cuerpo de lista del tab global
 * ([HistoricoListaContenido]) pero con chrome de detalle (volver) y un
 * título contextual derivado de la primera fila. El filtro lo aplica el
 * servidor por `local_id`/`maquina_id` (no in-memory), así que no se
 * pierde histórico más allá del tope del listado global.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoContextoScreen(
    onBack: () -> Unit,
    onRecaudacionClick: (String) -> Unit,
    viewModel: HistoricoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()

    val titulo = when (state.contexto) {
        is HistoricoContexto.Local ->
            state.recaudaciones.firstOrNull()?.localNombre
                ?: stringResource(R.string.historico_contexto_local_titulo)
        is HistoricoContexto.Maquina ->
            state.recaudaciones.firstOrNull()
                ?.let { rec ->
                    listOfNotNull(rec.maquinaSerie, rec.maquinaModelo).joinToString(" · ")
                }
                ?: stringResource(R.string.historico_contexto_maquina_titulo)
        HistoricoContexto.Global -> stringResource(R.string.historico_titulo)
    }

    Scaffold(
        topBar = {
            RecreDetailTopBar(titulo = titulo, onBack = onBack)
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
