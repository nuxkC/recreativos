package com.recre.app.feature.gestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.feature.gestion.components.OfflineBanner
import com.recre.app.ui.components.EntidadRow
import com.recre.app.ui.components.Pip
import com.recre.app.ui.components.RecreBottomBar
import com.recre.app.ui.components.RecreTopBar
import com.recre.app.ui.components.RecreTopBarActions
import com.recre.app.ui.components.TopLevelDestination
import com.recre.app.ui.theme.RecreShapes

/**
 * Pantalla "Gestión" (T-66..T-69 hub).
 *
 * Hub agregador con 5 entradas — licencias, máquinas, locales,
 * instalaciones, deudas — dirigido al rol `gestor+`. Si el usuario no
 * tiene el rol suficiente (técnico/contable), el menú overflow de
 * `LocalesScreen` ya oculta la entrada y, además, esta pantalla muestra
 * un mensaje de "sin permiso" como defensa adicional.
 *
 * Rediseño (F3): chrome propio (`RecreTopBar`) y entradas con `EntidadRow`
 * de la librería, sin `Card`/`TopAppBar` de Material.
 */
@Composable
fun GestionScreen(
    onSelectTab: (TopLevelDestination) -> Unit,
    onAlertasClick: () -> Unit,
    onIncidenciasClick: () -> Unit,
    onLicenciasClick: () -> Unit,
    onMaquinasClick: () -> Unit,
    onLocalesClick: () -> Unit,
    onInstalacionesClick: () -> Unit,
    onDeudasClick: () -> Unit,
    viewModel: GestionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            RecreTopBar(
                titulo = stringResource(R.string.gestion_titulo),
                actions = { RecreTopBarActions(onAlertasClick = onAlertasClick, onIncidenciasClick = onIncidenciasClick) },
            )
        },
        bottomBar = {
            RecreBottomBar(current = TopLevelDestination.GESTION, onSelect = onSelectTab)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.tieneRol) {
                SinPermisoBanner()
                return@Column
            }

            if (!state.online) {
                OfflineBanner(modifier = Modifier.fillMaxWidth())
            }

            Text(
                text = stringResource(R.string.gestion_descripcion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EntradaRow(
                icon = Icons.Default.Description,
                titulo = stringResource(R.string.gestion_entrada_licencias),
                subtitulo = stringResource(R.string.gestion_entrada_licencias_sub),
                onClick = onLicenciasClick,
            )
            EntradaRow(
                icon = Icons.Default.SettingsRemote,
                titulo = stringResource(R.string.gestion_entrada_maquinas),
                subtitulo = stringResource(R.string.gestion_entrada_maquinas_sub),
                onClick = onMaquinasClick,
            )
            EntradaRow(
                icon = Icons.Default.Storefront,
                titulo = stringResource(R.string.gestion_entrada_locales),
                subtitulo = stringResource(R.string.gestion_entrada_locales_sub),
                onClick = onLocalesClick,
            )
            EntradaRow(
                icon = Icons.Default.Apartment,
                titulo = stringResource(R.string.gestion_entrada_instalaciones),
                subtitulo = stringResource(R.string.gestion_entrada_instalaciones_sub),
                onClick = onInstalacionesClick,
            )
            EntradaRow(
                icon = Icons.Default.Payments,
                titulo = stringResource(R.string.gestion_entrada_deudas),
                subtitulo = stringResource(R.string.gestion_entrada_deudas_sub),
                onClick = onDeudasClick,
            )
        }
    }
}

@Composable
private fun EntradaRow(
    icon: ImageVector,
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit,
) {
    EntidadRow(
        titulo = titulo,
        subtitulo = subtitulo,
        onClick = onClick,
        leading = { Pip(icon) },
    )
}

@Composable
private fun SinPermisoBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RecreShapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.gestion_sin_permiso_titulo),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.gestion_sin_permiso_descripcion),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
