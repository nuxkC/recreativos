package com.recre.app.feature.cambio_placa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.recre.app.ui.components.FieldText
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreSnackbarHost

/**
 * Pantalla de cambio de placa (T-61).
 *
 * Form simple que llama a la Edge Function `crear-cambio-placa`. Tras
 * éxito, dispara un sync forzado y vuelve al detalle del local. Si no
 * hay red, muestra error: a diferencia de la recaudación (siempre
 * persistible offline), el cambio de placa requiere conexión para no
 * dejar baselines inconsistentes.
 *
 * Rediseño (F4·P5): chrome y campos propios (`RecreDetailTopBar`, `FieldText`,
 * `RecrePrimaryButton`).
 */
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
            RecreDetailTopBar(
                titulo = stringResource(R.string.cambio_placa_titulo),
                onBack = onBack,
                subtitulo = state.maquina?.numeroSerie,
                backEnabled = !state.guardando,
            )
        },
        snackbarHost = { RecreSnackbarHost(snackbarHostState) },
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

            FieldText(
                value = state.numeroSerieNueva,
                onValueChange = viewModel::onNumeroSerieNuevaChange,
                label = stringResource(R.string.cambio_placa_label_num_serie_nueva),
                placeholder = stringResource(R.string.cambio_placa_placeholder_num_serie),
                enabled = !state.guardando,
            )
            Spacer(Modifier.height(8.dp))

            FieldText(
                value = state.contadorEntradasInput,
                onValueChange = viewModel::onContadorEntradasChange,
                label = stringResource(R.string.cambio_placa_label_entradas_nueva),
                description = stringResource(R.string.cambio_placa_hint_contadores),
                keyboardType = KeyboardType.Number,
                enabled = !state.guardando,
            )
            Spacer(Modifier.height(8.dp))

            FieldText(
                value = state.contadorSalidasInput,
                onValueChange = viewModel::onContadorSalidasChange,
                label = stringResource(R.string.cambio_placa_label_salidas_nueva),
                keyboardType = KeyboardType.Number,
                enabled = !state.guardando,
            )
            Spacer(Modifier.height(8.dp))

            FieldText(
                value = state.motivo,
                onValueChange = viewModel::onMotivoChange,
                label = stringResource(R.string.cambio_placa_label_motivo),
                singleLine = false,
                minLines = 3,
                enabled = !state.guardando,
            )

            Spacer(Modifier.height(24.dp))
            RecrePrimaryButton(
                text = stringResource(R.string.cambio_placa_accion_guardar),
                onClick = viewModel::onGuardar,
                enabled = state.canGuardar,
                loading = state.guardando,
            )
        }
    }
}
