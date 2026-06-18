package com.recre.app.feature.empresa

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.auth.Rol
import com.recre.app.core.session.Membresia
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.RecreSnackbarHost
import com.recre.app.ui.components.RecreTonalButton
import com.recre.app.ui.components.RecreTopBar

@Composable
fun SeleccionarEmpresaScreen(
    viewModel: SeleccionarEmpresaViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val membresias by viewModel.membresias.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorSeleccion = stringResource(R.string.empresa_seleccion_error)

    LaunchedEffect(membresias.size) {
        // Solo dispara cuando ya tenemos la lista cargada.
        if (membresias.size == 1) viewModel.autoseleccionarSiSoloUna()
    }

    LaunchedEffect(uiState.errorMessage) {
        val key = uiState.errorMessage ?: return@LaunchedEffect
        val message = when (key) {
            SeleccionarEmpresaViewModel.ERROR_SELECCION -> errorSeleccion
            else -> errorSeleccion
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    Scaffold(
        topBar = {
            RecreTopBar(titulo = stringResource(R.string.empresa_seleccion_titulo))
        },
        snackbarHost = { RecreSnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            membresias.isEmpty() -> EmptyOrLoading(padding)
            else -> ContenidoSeleccion(
                membresias = membresias,
                seleccionando = uiState.seleccionando,
                onSeleccionar = viewModel::seleccionar,
                onCerrarSesion = viewModel::cerrarSesion,
                padding = padding,
            )
        }
    }
}

@Composable
private fun EmptyOrLoading(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ContenidoSeleccion(
    membresias: List<Membresia>,
    seleccionando: String?,
    onSeleccionar: (String) -> Unit,
    onCerrarSesion: () -> Unit,
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.empresa_seleccion_descripcion),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(membresias, key = { it.empresa.id }) { membresia ->
                MembresiaCard(
                    membresia = membresia,
                    cargando = seleccionando == membresia.empresa.id,
                    bloqueado = seleccionando != null && seleccionando != membresia.empresa.id,
                    onClick = { onSeleccionar(membresia.empresa.id) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        RecreTonalButton(
            text = stringResource(R.string.auth_signout),
            onClick = onCerrarSesion,
            enabled = seleccionando == null,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MembresiaCard(
    membresia: Membresia,
    cargando: Boolean,
    bloqueado: Boolean,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = !bloqueado && !cargando,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = membresia.empresa.nombre,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(rolStringRes(membresia.rol)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cargando) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

private fun rolStringRes(rol: Rol): Int = when (rol) {
    Rol.OWNER -> R.string.rol_owner
    Rol.ADMIN -> R.string.rol_admin
    Rol.GESTOR -> R.string.rol_gestor
    Rol.TECNICO -> R.string.rol_tecnico
    Rol.CONTABLE -> R.string.rol_contable
}
