package com.recre.app.feature.historico

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.ui.components.Banda
import com.recre.app.ui.components.BandaTono
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.formatEur
import java.math.BigDecimal

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

    // N8: subtítulo mono («HISTÓRICO DE LA MÁQUINA — {local}» / «HISTÓRICO DEL
    // LOCAL»). RecreDetailTopBar lo pinta como eyebrow mono en mayúsculas.
    val subtitulo = when (state.contexto) {
        is HistoricoContexto.Maquina ->
            stringResource(
                R.string.historico_contexto_subtitulo_maquina,
                state.recaudaciones.firstOrNull()?.localNombre.orEmpty(),
            )
        is HistoricoContexto.Local -> stringResource(R.string.historico_contexto_subtitulo_local)
        HistoricoContexto.Global -> null
    }

    // N8: resumen del contexto sobre la lista. Cuenta y bruto acumulado del mismo
    // conjunto que se pinta (no hay E/S por ítem en el modelo → no se muestra).
    val numRecaudaciones = state.recaudaciones.size
    val sumaBruto = state.recaudaciones.fold(BigDecimal.ZERO) { acc, rec -> acc + rec.bruto }
    val resumen = pluralStringResource(
        id = R.plurals.historico_contexto_resumen,
        count = numRecaudaciones,
        numRecaudaciones,
        formatEur(sumaBruto),
    )

    Scaffold(
        topBar = {
            RecreDetailTopBar(titulo = titulo, onBack = onBack, subtitulo = subtitulo)
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
                if (numRecaudaciones > 0) {
                    Banda(
                        texto = AnnotatedString(resumen),
                        icon = Icons.Filled.Info,
                        tono = BandaTono.INFO,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                HistoricoListaContenido(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onRecaudacionClick = onRecaudacionClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}
