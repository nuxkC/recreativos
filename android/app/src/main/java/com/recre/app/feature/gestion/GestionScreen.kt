package com.recre.app.feature.gestion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R

/**
 * Pantalla "Gestión" (T-66..T-69 hub).
 *
 * Hub agregador con 4 entradas — licencias, máquinas, locales,
 * instalaciones — dirigido al rol `gestor+`. Si el usuario no tiene
 * el rol suficiente (técnico/contable), el menú overflow de
 * `LocalesScreen` ya oculta la entrada y, además, esta pantalla muestra
 * un mensaje de "sin permiso" como defensa adicional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestionScreen(
    onBack: () -> Unit,
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
            TopAppBar(
                title = { Text(stringResource(R.string.gestion_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
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
                SinPermisoCard()
                return@Column
            }

            if (!state.online) {
                com.recre.app.feature.gestion.components.OfflineBanner(
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = stringResource(R.string.gestion_descripcion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EntradaCard(
                icon = Icons.Default.Description,
                titulo = stringResource(R.string.gestion_entrada_licencias),
                subtitulo = stringResource(R.string.gestion_entrada_licencias_sub),
                onClick = onLicenciasClick,
            )
            EntradaCard(
                icon = Icons.Default.SettingsRemote,
                titulo = stringResource(R.string.gestion_entrada_maquinas),
                subtitulo = stringResource(R.string.gestion_entrada_maquinas_sub),
                onClick = onMaquinasClick,
            )
            EntradaCard(
                icon = Icons.Default.Storefront,
                titulo = stringResource(R.string.gestion_entrada_locales),
                subtitulo = stringResource(R.string.gestion_entrada_locales_sub),
                onClick = onLocalesClick,
            )
            EntradaCard(
                icon = Icons.Default.Apartment,
                titulo = stringResource(R.string.gestion_entrada_instalaciones),
                subtitulo = stringResource(R.string.gestion_entrada_instalaciones_sub),
                onClick = onInstalacionesClick,
            )
            EntradaCard(
                icon = Icons.Default.Payments,
                titulo = stringResource(R.string.gestion_entrada_deudas),
                subtitulo = stringResource(R.string.gestion_entrada_deudas_sub),
                onClick = onDeudasClick,
            )
        }
    }
}

@Composable
private fun EntradaCard(
    icon: ImageVector,
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = titulo, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SinPermisoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
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
