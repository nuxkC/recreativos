package com.recre.app.feature.cambio_placa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R

/**
 * Pantalla de cambio de placa (T-61).
 *
 * Form simple que llama a la Edge Function `crear-cambio-placa`. Tras
 * éxito, dispara un sync forzado y vuelve al detalle del local. Si no
 * hay red, muestra error: a diferencia de la recaudación (siempre
 * persistible offline), el cambio de placa requiere conexión para no
 * dejar baselines inconsistentes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CambioPlacaScreen(
    viewModel: CambioPlacaViewModel,
    onFinalizar: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessages = mapOf(
        "network" to stringResource(R.string.cambio_placa_error_network),
        "validation_error" to stringResource(R.string.cambio_placa_error_validation),
        "forbidden" to stringResource(R.string.cambio_placa_error_forbidden),
        "not_found" to stringResource(R.string.cambio_placa_error_not_found),
        "unknown" to stringResource(R.string.cambio_placa_error_generic),
    )

    LaunchedEffect(state.guardado) {
        if (state.guardado) onFinalizar()
    }

    LaunchedEffect(state.errorCode) {
        val code = state.errorCode ?: return@LaunchedEffect
        val message = errorMessages[code] ?: errorMessages["unknown"] ?: code
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.cambio_placa_titulo))
                        val maquina = state.maquina
                        if (maquina != null) {
                            Text(
                                text = maquina.numeroSerie,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.guardando) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.cambio_placa_descripcion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.numeroSerieNueva,
                onValueChange = viewModel::onNumeroSerieNuevaChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.cambio_placa_label_num_serie_nueva)) },
                placeholder = { Text(stringResource(R.string.cambio_placa_placeholder_num_serie)) },
                singleLine = true,
                enabled = !state.guardando,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.contadorEntradasInput,
                onValueChange = viewModel::onContadorEntradasChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.cambio_placa_label_entradas_nueva)) },
                supportingText = {
                    Text(stringResource(R.string.cambio_placa_hint_contadores))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !state.guardando,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.contadorSalidasInput,
                onValueChange = viewModel::onContadorSalidasChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.cambio_placa_label_salidas_nueva)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !state.guardando,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.motivo,
                onValueChange = viewModel::onMotivoChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.cambio_placa_label_motivo)) },
                minLines = 3,
                enabled = !state.guardando,
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::onGuardar,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canGuardar,
            ) {
                Text(
                    text = if (state.guardando) {
                        stringResource(R.string.cambio_placa_accion_guardando)
                    } else {
                        stringResource(R.string.cambio_placa_accion_guardar)
                    },
                )
            }
        }
    }
}
