package com.recre.app.feature.gestion.licencias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.feature.gestion.ESTADOS_LICENCIA
import com.recre.app.feature.gestion.components.GestionDropdown
import com.recre.app.feature.gestion.components.GestionTextField
import com.recre.app.feature.gestion.resolveErrorRes

/**
 * Formulario de alta / edición de Licencia (T-66).
 *
 * Cada campo opcional se muestra como `OutlinedTextField` con label y
 * `supportingText` para los errores locales. El estado se elige con un
 * dropdown enum. El botón "Guardar" está deshabilitado mientras hay una
 * mutación en vuelo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenciaFormScreen(
    onBack: () -> Unit,
    onGuardado: () -> Unit,
    viewModel: LicenciaFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val errorMessage = state.errorCode?.let { stringResource(resolveErrorRes(it)) }

    LaunchedEffect(state.guardado) {
        if (state.guardado) onGuardado()
    }
    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message = msg)
        viewModel.consumirError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.esEdicion) R.string.gestion_licencia_editar
                            else R.string.gestion_licencia_nueva,
                        ),
                    )
                },
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
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        if (state.cargando) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.online) {
                com.recre.app.feature.gestion.components.OfflineBanner(
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GestionTextField(
                label = stringResource(R.string.gestion_licencia_numero),
                value = state.numero,
                onValueChange = viewModel::onNumeroChange,
                error = state.errores["numero"]?.let {
                    stringResource(R.string.gestion_validacion_requerido)
                },
            )
            GestionTextField(
                label = stringResource(R.string.gestion_licencia_tipo),
                value = state.tipo,
                onValueChange = viewModel::onTipoChange,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GestionTextField(
                    label = stringResource(R.string.gestion_licencia_fecha_expedicion),
                    value = state.fechaExpedicion,
                    onValueChange = viewModel::onFechaExpedicionChange,
                    placeholder = "YYYY-MM-DD",
                    error = state.errores["fechaExpedicion"]?.let {
                        stringResource(R.string.gestion_validacion_fecha)
                    },
                    modifier = Modifier.weight(1f),
                )
                GestionTextField(
                    label = stringResource(R.string.gestion_licencia_fecha_caducidad),
                    value = state.fechaCaducidad,
                    onValueChange = viewModel::onFechaCaducidadChange,
                    placeholder = "YYYY-MM-DD",
                    error = state.errores["fechaCaducidad"]?.let { code ->
                        stringResource(
                            if (code == "fecha_caducidad_anterior_expedicion") {
                                R.string.gestion_validacion_fecha_caducidad_orden
                            } else {
                                R.string.gestion_validacion_fecha
                            },
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            GestionTextField(
                label = stringResource(R.string.gestion_licencia_comunidad),
                value = state.comunidadAutonoma,
                onValueChange = viewModel::onComunidadAutonomaChange,
            )
            GestionDropdown(
                label = stringResource(R.string.gestion_licencia_estado),
                selected = state.estado,
                options = ESTADOS_LICENCIA,
                optionLabel = { it },
                onSelected = viewModel::onEstadoChange,
            )
            GestionTextField(
                label = stringResource(R.string.gestion_licencia_notas),
                value = state.notas,
                onValueChange = viewModel::onNotasChange,
                singleLine = false,
                minLines = 3,
                keyboardType = KeyboardType.Text,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::guardar,
                enabled = !state.guardando && state.online,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.guardando) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(20.dp),
                    )
                } else {
                    Text(stringResource(R.string.gestion_guardar))
                }
            }
        }
    }
}
