package com.recre.app.feature.gestion.maquinas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.local.entity.MaquinaEntity
import com.recre.app.feature.gestion.components.GestionConfirmDialog
import com.recre.app.feature.gestion.components.GestionListaScaffold
import com.recre.app.feature.gestion.components.MaquinaEstadoChip
import com.recre.app.feature.gestion.resolveErrorRes
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.EmptyState
import com.recre.app.ui.components.ListSkeleton
import com.recre.app.ui.components.OverflowAccion
import com.recre.app.ui.components.RecreOverflowMenu
import com.recre.app.ui.components.SnackbarEstado
import com.recre.app.ui.components.mostrar
import com.recre.app.ui.theme.RecreMotion

/**
 * Lista de máquinas (T-67). Rediseño F3·P4: chrome propio, `AppCard` con
 * chip de estado y menú de acciones (editar / ver averías / eliminar).
 */
@Composable
fun MaquinasGestorScreen(
    onBack: () -> Unit,
    onAlta: () -> Unit,
    onEditar: (String) -> Unit,
    onVerAverias: (String) -> Unit,
    onVerHistorico: (String) -> Unit,
    viewModel: MaquinasGestorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var aBorrar by remember { mutableStateOf<MaquinaEntity?>(null) }
    val errorMessage = state.errorCode?.let { stringResource(resolveErrorRes(it)) }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHost.mostrar(SnackbarEstado.Error, msg)
        viewModel.consumirError()
    }

    GestionListaScaffold(
        titulo = stringResource(R.string.gestion_maquinas_titulo),
        buscarPlaceholder = stringResource(R.string.gestion_buscar),
        busqueda = state.busqueda,
        onBusquedaChange = viewModel::onBusquedaChange,
        online = state.online,
        onBack = onBack,
        onAlta = onAlta,
        altaContentDescription = stringResource(R.string.gestion_alta),
        snackbarHost = snackbarHost,
    ) {
        val filtered = remember(state.maquinas, state.busqueda) {
            val q = state.busqueda.trim().lowercase()
            if (q.isEmpty()) state.maquinas
            else state.maquinas.filter {
                it.numeroSerie.lowercase().contains(q) ||
                    (it.modelo ?: "").lowercase().contains(q) ||
                    (it.fabricante ?: "").lowercase().contains(q)
            }
        }

        when {
            state.cargando -> ListSkeleton(
                loadingLabel = stringResource(R.string.cargando),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            state.maquinas.isEmpty() -> EmptyState(
                icon = Icons.Filled.SettingsRemote,
                title = stringResource(R.string.gestion_maquinas_vacio),
                description = stringResource(R.string.gestion_lista_vacia_desc),
                actionLabel = stringResource(R.string.gestion_alta),
                onActionClick = onAlta,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            filtered.isEmpty() -> EmptyState(
                icon = Icons.Filled.SettingsRemote,
                title = stringResource(R.string.gestion_busqueda_vacia),
                description = stringResource(R.string.gestion_busqueda_vacia_desc),
                filtered = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { m ->
                    Box(Modifier.animateItem(placementSpec = RecreMotion.current.defaultSpatialSpec())) {
                        MaquinaCard(
                            maquina = m,
                            borrando = state.borrando == m.id,
                            onEditar = { onEditar(m.id) },
                            onVerAverias = { onVerAverias(m.id) },
                            onVerHistorico = { onVerHistorico(m.id) },
                            onEliminar = { aBorrar = m },
                        )
                    }
                }
            }
        }
    }

    aBorrar?.let { m ->
        GestionConfirmDialog(
            titulo = stringResource(R.string.gestion_eliminar_titulo),
            mensaje = stringResource(R.string.gestion_maquina_eliminar_descripcion, m.numeroSerie),
            confirmarLabel = stringResource(R.string.gestion_eliminar_confirmar),
            cancelarLabel = stringResource(R.string.action_cancel),
            onConfirmar = {
                viewModel.eliminar(m.id)
                aBorrar = null
            },
            onDismiss = { aBorrar = null },
        )
    }
}

@Composable
private fun MaquinaCard(
    maquina: MaquinaEntity,
    borrando: Boolean,
    onEditar: () -> Unit,
    onVerAverias: () -> Unit,
    onVerHistorico: () -> Unit,
    onEliminar: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(maquina.numeroSerie, style = MaterialTheme.typography.titleSmall)
                val subtitle = listOfNotNull(
                    maquina.fabricante?.takeIf { it.isNotBlank() },
                    maquina.modelo?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.gestion_maquina_valor, maquina.valorCredito),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MaquinaEstadoChip(maquina.estado)
            Spacer(Modifier.width(8.dp))
            if (borrando) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            } else {
                RecreOverflowMenu(
                    contentDescription = stringResource(R.string.gestion_acciones),
                    acciones = listOf(
                        OverflowAccion(stringResource(R.string.action_edit), onEditar),
                        OverflowAccion(stringResource(R.string.averia_historial_titulo), onVerAverias),
                        OverflowAccion(stringResource(R.string.historico_ver_accion), onVerHistorico),
                        OverflowAccion(stringResource(R.string.action_delete), onEliminar),
                    ),
                )
            }
        }
    }
}
