package com.recre.app.feature.gestion.maquinas

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import com.recre.app.ui.components.RecreSnackbarHost
import com.recre.app.ui.components.SnackbarEstado
import com.recre.app.ui.components.mostrar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.feature.gestion.ESTADOS_MAQUINA
import com.recre.app.feature.gestion.components.GestionDropdown
import com.recre.app.feature.gestion.components.GestionTextField
import com.recre.app.feature.gestion.resolveErrorRes
import com.recre.app.ui.components.StepIndicator

/** Formulario de Máquina (T-67) — alta (wizard, T-242) y edición (form único). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaquinaFormScreen(
    onBack: () -> Unit,
    onGuardado: () -> Unit,
    viewModel: MaquinaFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val errorMessage = state.errorCode?.let { stringResource(resolveErrorRes(it)) }

    LaunchedEffect(state.guardado) { if (state.guardado) onGuardado() }
    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHost.mostrar(SnackbarEstado.Error, msg)
        viewModel.consumirError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.esEdicion) R.string.gestion_maquina_editar
                            else R.string.gestion_maquina_nueva,
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
        snackbarHost = { RecreSnackbarHost(snackbarHost) },
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
            if (state.esEdicion) {
                MaquinaIdentificacion(state, viewModel)
                MaquinaEconomico(state, viewModel)
                Spacer(Modifier.height(8.dp))
                GuardarMaquinaButton(state, viewModel)
            } else {
                MaquinaAltaWizard(state, viewModel)
            }
        }
    }
}

/**
 * Alta de máquina como wizard de 2 pasos (T-242): primero **identificación**
 * (nº de serie, fabricante, modelo, estado) y luego **datos económicos** (valor
 * del crédito, contadores base, notas). El paso 1 gatea "Siguiente" hasta que el
 * nº de serie no está vacío; "Crear" delega en `guardar()`, que revalida todo.
 */
@Composable
private fun MaquinaAltaWizard(
    state: MaquinaFormUiState,
    viewModel: MaquinaFormViewModel,
) {
    var paso by rememberSaveable { mutableIntStateOf(0) }

    StepIndicator(
        current = paso + 1,
        total = 2,
        label = stringResource(R.string.wizard_paso),
        connector = stringResource(R.string.wizard_de),
    )
    Text(
        text = stringResource(
            if (paso == 0) R.string.wizard_maquina_paso1
            else R.string.wizard_maquina_paso2,
        ),
        style = MaterialTheme.typography.titleMedium,
    )

    if (paso == 0) {
        MaquinaIdentificacion(state, viewModel)
    } else {
        MaquinaEconomico(state, viewModel)
    }

    Spacer(Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (paso > 0) {
            OutlinedButton(
                onClick = { paso-- },
                enabled = !state.guardando,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.wizard_atras)) }
        }
        if (paso == 0) {
            Button(
                onClick = { paso++ },
                enabled = state.numeroSerie.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.wizard_siguiente)) }
        } else {
            GuardarMaquinaButton(state, viewModel, modifier = Modifier.weight(1f))
        }
    }
}

/** Paso 1 / edición: identificación de la máquina. */
@Composable
private fun MaquinaIdentificacion(
    state: MaquinaFormUiState,
    viewModel: MaquinaFormViewModel,
) {
    GestionTextField(
        label = stringResource(R.string.gestion_maquina_numero_serie),
        value = state.numeroSerie,
        onValueChange = viewModel::onNumeroSerieChange,
        error = state.errores["numeroSerie"]?.let {
            stringResource(R.string.gestion_validacion_requerido)
        },
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        GestionTextField(
            label = stringResource(R.string.gestion_maquina_fabricante),
            value = state.fabricante,
            onValueChange = viewModel::onFabricanteChange,
            modifier = Modifier.weight(1f),
        )
        GestionTextField(
            label = stringResource(R.string.gestion_maquina_modelo),
            value = state.modelo,
            onValueChange = viewModel::onModeloChange,
            modifier = Modifier.weight(1f),
        )
    }
    GestionDropdown(
        label = stringResource(R.string.gestion_maquina_estado),
        selected = state.estado,
        options = ESTADOS_MAQUINA,
        optionLabel = { it },
        onSelected = viewModel::onEstadoChange,
    )
}

/** Paso 2 / edición: datos económicos (crédito, contadores base, notas). */
@Composable
private fun MaquinaEconomico(
    state: MaquinaFormUiState,
    viewModel: MaquinaFormViewModel,
) {
    GestionTextField(
        label = stringResource(R.string.gestion_maquina_valor_credito),
        value = state.valorCredito,
        onValueChange = viewModel::onValorCreditoChange,
        placeholder = "0.20",
        keyboardType = KeyboardType.Decimal,
        error = state.errores["valorCredito"]?.let {
            stringResource(R.string.gestion_validacion_valor_credito)
        },
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        GestionTextField(
            label = stringResource(R.string.gestion_maquina_contador_entradas),
            value = state.contadorEntradasInicial,
            onValueChange = viewModel::onContadorEntradasChange,
            keyboardType = KeyboardType.Number,
            error = state.errores["contadorEntradas"]?.let {
                stringResource(R.string.gestion_validacion_contador)
            },
            modifier = Modifier.weight(1f),
        )
        GestionTextField(
            label = stringResource(R.string.gestion_maquina_contador_salidas),
            value = state.contadorSalidasInicial,
            onValueChange = viewModel::onContadorSalidasChange,
            keyboardType = KeyboardType.Number,
            error = state.errores["contadorSalidas"]?.let {
                stringResource(R.string.gestion_validacion_contador)
            },
            modifier = Modifier.weight(1f),
        )
    }
    GestionTextField(
        label = stringResource(R.string.gestion_maquina_notas),
        value = state.notas,
        onValueChange = viewModel::onNotasChange,
        singleLine = false,
        minLines = 3,
    )
}

/** Botón "Guardar" con spinner mientras persiste; deshabilitado offline. */
@Composable
private fun GuardarMaquinaButton(
    state: MaquinaFormUiState,
    viewModel: MaquinaFormViewModel,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = viewModel::guardar,
        enabled = !state.guardando && state.online,
        modifier = modifier.fillMaxWidth(),
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
