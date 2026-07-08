package com.recre.app.feature.gestion.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecreSnackbarHost
import com.recre.app.ui.components.SearchField
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.neonGlow

/**
 * Andamiaje común de las 4 listas del CRUD gestor (T-66..T-69) — rediseño
 * F3·P4. Aporta el chrome propio (`RecreDetailTopBar`), el FAB de alta, el
 * banner offline, el buscador (`SearchField`) y el host de snackbars; cada
 * lista solo aporta el cuerpo (estados de carga/vacío + `LazyColumn`).
 *
 * El FAB conserva el comportamiento previo: visible siempre, atenuado sin
 * red (la alta real se valida en su propio flujo / repositorio).
 */
@Composable
fun GestionListaScaffold(
    titulo: String,
    buscarPlaceholder: String,
    busqueda: String,
    onBusquedaChange: (String) -> Unit,
    online: Boolean,
    onBack: () -> Unit,
    onAlta: () -> Unit,
    altaContentDescription: String,
    snackbarHost: SnackbarHostState,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = { RecreDetailTopBar(titulo = titulo, onBack = onBack) },
        floatingActionButton = {
            // S8: FAB acento pleno con halo neón (mockup .fab). La forma ya sale
            // del theme (CornerLarge = 20dp). Offline se atenúa, como antes.
            FloatingActionButton(
                onClick = onAlta,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    (if (!online) Modifier.alpha(0.4f) else Modifier)
                        .neonGlow(RecreColors.current.accentBright, radius = 18.dp, alpha = 0.35f),
            ) {
                Icon(Icons.Default.Add, contentDescription = altaContentDescription)
            }
        },
        snackbarHost = { RecreSnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!online) {
                OfflineBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            SearchField(
                value = busqueda,
                onValueChange = onBusquedaChange,
                placeholder = buscarPlaceholder,
                clearContentDescription = stringResource(R.string.action_clear),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            content()
        }
    }
}
