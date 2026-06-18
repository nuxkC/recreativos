package com.recre.app.feature.gestion.instalaciones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.feature.gestion.components.GestionListaScaffold
import com.recre.app.feature.gestion.resolveErrorRes
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.EmptyState
import com.recre.app.ui.components.ListSkeleton
import com.recre.app.ui.components.SnackbarEstado
import com.recre.app.ui.components.mostrar
import com.recre.app.ui.theme.RecreMotion

/**
 * Lista del CRUD de Instalaciones (T-69). Rediseño F3·P4: chrome propio,
 * `AppCard` tappable que abre el formulario de edición/cierre. Solo
 * muestra instalaciones activas (las cerradas viven en el histórico).
 */
@Composable
fun InstalacionesGestorScreen(
    onBack: () -> Unit,
    onAlta: () -> Unit,
    onEditar: (String) -> Unit,
    viewModel: InstalacionesGestorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val errorMessage = state.errorCode?.let { stringResource(resolveErrorRes(it)) }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHost.mostrar(SnackbarEstado.Error, msg)
        viewModel.consumirError()
    }

    GestionListaScaffold(
        titulo = stringResource(R.string.gestion_instalaciones_titulo),
        buscarPlaceholder = stringResource(R.string.gestion_buscar),
        busqueda = state.busqueda,
        onBusquedaChange = viewModel::onBusquedaChange,
        online = state.online,
        onBack = onBack,
        onAlta = onAlta,
        altaContentDescription = stringResource(R.string.gestion_alta),
        snackbarHost = snackbarHost,
    ) {
        Text(
            stringResource(R.string.gestion_instalaciones_solo_activas),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        val filtered = remember(state.instalaciones, state.busqueda) {
            val q = state.busqueda.trim().lowercase()
            if (q.isEmpty()) state.instalaciones
            else state.instalaciones.filter {
                it.maquinaNumeroSerie.lowercase().contains(q) ||
                    it.licenciaNumero.lowercase().contains(q) ||
                    it.localNombre.lowercase().contains(q)
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
            state.instalaciones.isEmpty() -> EmptyState(
                icon = Icons.Filled.Apartment,
                title = stringResource(R.string.gestion_instalaciones_vacio),
                description = stringResource(R.string.gestion_lista_vacia_desc),
                actionLabel = stringResource(R.string.gestion_alta),
                onActionClick = onAlta,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            filtered.isEmpty() -> EmptyState(
                icon = Icons.Filled.Apartment,
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
                items(filtered, key = { it.id }) { item ->
                    Box(Modifier.animateItem(placementSpec = RecreMotion.current.defaultSpatialSpec())) {
                        InstalacionItemCard(
                            item = item,
                            onEditar = { onEditar(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalacionItemCard(
    item: InstalacionItem,
    onEditar: () -> Unit,
) {
    AppCard(onClick = onEditar, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(item.localNombre, style = MaterialTheme.typography.titleSmall)
            val maquinaText = listOfNotNull(
                item.maquinaNumeroSerie,
                item.maquinaModelo?.takeIf { it.isNotBlank() },
            ).joinToString(" — ")
            Text(
                stringResource(R.string.gestion_instalacion_maquina, maquinaText),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.gestion_instalacion_licencia, item.licenciaNumero),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(
                    R.string.gestion_instalacion_tasa_pct,
                    item.tasaSemanal,
                    item.porcentajeLocal,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
