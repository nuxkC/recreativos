package com.recre.app.feature.gestion.instalaciones

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreSnackbarHost
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.components.RecreTonalButton
import com.recre.app.ui.components.SnackbarEstado
import com.recre.app.ui.components.mostrar
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
import com.recre.app.feature.gestion.FkOption
import com.recre.app.feature.gestion.components.GestionDropdown
import com.recre.app.feature.gestion.components.GestionTextField
import com.recre.app.feature.gestion.resolveErrorRes
import com.recre.app.ui.components.FieldDate
import com.recre.app.ui.components.StepIndicator

/** Form de Instalación (T-69) — alta (wizard, T-242), edición y cierre. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalacionFormScreen(
    onBack: () -> Unit,
    onGuardado: () -> Unit,
    viewModel: InstalacionFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val errorMessage = state.errorCode?.let { stringResource(resolveErrorRes(it)) }

    LaunchedEffect(state.guardado, state.cerrada) {
        if (state.guardado || state.cerrada) onGuardado()
    }
    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHost.mostrar(SnackbarEstado.Error, msg)
        viewModel.consumirError()
    }

    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = stringResource(
                    if (state.esEdicion) R.string.gestion_instalacion_editar
                    else R.string.gestion_instalacion_nueva,
                ),
                onBack = onBack,
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
                EdicionInstalacionForm(state, viewModel)
            } else {
                AltaInstalacionWizard(state, viewModel)
            }
        }
    }

    if (state.mostrarCerrarDialog) {
        AlertDialog(
            onDismissRequest = viewModel::cancelarCerrar,
            title = { Text(stringResource(R.string.gestion_instalacion_cerrar_titulo)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.gestion_instalacion_cerrar_descripcion))
                    FieldDate(
                        label = stringResource(R.string.gestion_instalacion_cerrar_fecha),
                        value = state.cerrarFechaFin,
                        onValueChange = viewModel::onCerrarFechaFinChange,
                        minIso = state.fechaInicio,
                    )
                    GestionTextField(
                        label = stringResource(R.string.gestion_instalacion_cerrar_notas),
                        value = state.cerrarNotas,
                        onValueChange = viewModel::onCerrarNotasChange,
                        singleLine = false,
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                RecreTextButton(
                    text = stringResource(R.string.gestion_instalacion_cerrar_confirmar),
                    onClick = viewModel::confirmarCerrar,
                )
            },
            dismissButton = {
                RecreTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = viewModel::cancelarCerrar,
                )
            },
        )
    }
}

/**
 * Alta de instalación como wizard de 2 pasos (T-242): primero **qué se instala**
 * (máquina/licencia/local), luego las **condiciones** (fecha, tasa, %, tolva, notas).
 * El paso 1 gatea "Siguiente" hasta que los 3 selects están elegidos; el paso 2
 * delega en `viewModel.guardar()`, que revalida todo antes de persistir.
 */
@Composable
private fun AltaInstalacionWizard(
    state: InstalacionFormUiState,
    viewModel: InstalacionFormViewModel,
) {
    var paso by rememberSaveable { mutableIntStateOf(0) }
    val seleccionListos = !state.maquinaId.isNullOrEmpty() &&
        !state.licenciaId.isNullOrEmpty() &&
        !state.localId.isNullOrEmpty()

    StepIndicator(
        current = paso + 1,
        total = 2,
        label = stringResource(R.string.wizard_paso),
        connector = stringResource(R.string.wizard_de),
    )
    Text(
        text = stringResource(
            if (paso == 0) R.string.wizard_instalacion_paso1
            else R.string.wizard_instalacion_paso2,
        ),
        style = MaterialTheme.typography.titleMedium,
    )

    if (paso == 0) {
        AltaPickers(state, viewModel)
    } else {
        CamposEconomicos(state, viewModel)
        GestionTextField(
            label = stringResource(R.string.gestion_instalacion_tolva),
            value = state.tolva,
            onValueChange = viewModel::onTolvaChange,
            placeholder = "0.00",
            keyboardType = KeyboardType.Decimal,
            error = state.errores["tolva"]?.let {
                stringResource(R.string.gestion_validacion_tolva)
            },
        )
        Text(
            text = stringResource(R.string.gestion_instalacion_tolva_ayuda),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GestionTextField(
            label = stringResource(R.string.gestion_instalacion_notas),
            value = state.notas,
            onValueChange = viewModel::onNotasChange,
            singleLine = false,
            minLines = 3,
        )
    }

    Spacer(Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (paso > 0) {
            RecreTonalButton(
                text = stringResource(R.string.wizard_atras),
                onClick = { paso-- },
                enabled = !state.guardando,
                fullWidth = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (paso == 0) {
            RecrePrimaryButton(
                text = stringResource(R.string.wizard_siguiente),
                onClick = { paso++ },
                enabled = seleccionListos,
                modifier = Modifier.weight(1f),
            )
        } else {
            RecrePrimaryButton(
                text = stringResource(R.string.gestion_guardar),
                onClick = viewModel::guardar,
                enabled = !state.guardando && state.online,
                loading = state.guardando,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Edición de una instalación existente: FKs inmutables, sin tolva ni wizard. */
@Composable
private fun EdicionInstalacionForm(
    state: InstalacionFormUiState,
    viewModel: InstalacionFormViewModel,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                state.cabecera.ifBlank { stringResource(R.string.gestion_instalacion_editar) },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.gestion_instalacion_fk_inmutables),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    CamposEconomicos(state, viewModel)
    GestionTextField(
        label = stringResource(R.string.gestion_instalacion_notas),
        value = state.notas,
        onValueChange = viewModel::onNotasChange,
        singleLine = false,
        minLines = 3,
    )

    Spacer(Modifier.height(8.dp))
    RecrePrimaryButton(
        text = stringResource(R.string.gestion_guardar),
        onClick = viewModel::guardar,
        enabled = !state.guardando && !state.cerrando && state.online,
        loading = state.guardando,
    )
    RecreTonalButton(
        text = stringResource(R.string.gestion_instalacion_cerrar),
        onClick = viewModel::pedirCerrar,
        enabled = !state.cerrando && !state.guardando && state.online,
        loading = state.cerrando,
        fullWidth = true,
    )
}

/** Campos comunes de alta y edición: fecha de inicio + tasa semanal + % local. */
@Composable
private fun CamposEconomicos(
    state: InstalacionFormUiState,
    viewModel: InstalacionFormViewModel,
) {
    FieldDate(
        label = stringResource(R.string.gestion_instalacion_fecha_inicio),
        value = state.fechaInicio,
        onValueChange = viewModel::onFechaInicioChange,
        isError = state.errores["fechaInicio"] != null,
        errorText = state.errores["fechaInicio"]?.let {
            stringResource(R.string.gestion_validacion_fecha)
        },
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        GestionTextField(
            label = stringResource(R.string.gestion_instalacion_tasa_semanal),
            value = state.tasaSemanal,
            onValueChange = viewModel::onTasaChange,
            placeholder = "0.00",
            keyboardType = KeyboardType.Decimal,
            error = state.errores["tasaSemanal"]?.let {
                stringResource(R.string.gestion_validacion_tasa)
            },
            modifier = Modifier.weight(1f),
        )
        GestionTextField(
            label = stringResource(R.string.gestion_instalacion_porcentaje_local),
            value = state.porcentajeLocal,
            onValueChange = viewModel::onPorcentajeChange,
            placeholder = "50.00",
            keyboardType = KeyboardType.Decimal,
            error = state.errores["porcentajeLocal"]?.let {
                stringResource(R.string.gestion_validacion_porcentaje)
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AltaPickers(
    state: InstalacionFormUiState,
    viewModel: InstalacionFormViewModel,
) {
    if (state.maquinasDisponibles.isEmpty()) {
        Text(
            stringResource(R.string.gestion_instalacion_sin_maquinas_disponibles),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (state.licenciasDisponibles.isEmpty()) {
        Text(
            stringResource(R.string.gestion_instalacion_sin_licencias_disponibles),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (state.locales.isEmpty()) {
        Text(
            stringResource(R.string.gestion_instalacion_sin_locales),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    GestionDropdown(
        label = stringResource(R.string.gestion_instalacion_maquina),
        selected = state.maquinasDisponibles.firstOrNull { it.id == state.maquinaId }
            ?: FkOption(id = "", label = stringResource(R.string.gestion_seleccionar)),
        options = state.maquinasDisponibles,
        optionLabel = { it.label },
        onSelected = { viewModel.onMaquinaChange(it.id) },
        enabled = state.maquinasDisponibles.isNotEmpty(),
        error = state.errores["maquina"]?.let {
            stringResource(R.string.gestion_validacion_requerido)
        },
    )
    GestionDropdown(
        label = stringResource(R.string.gestion_instalacion_licencia),
        selected = state.licenciasDisponibles.firstOrNull { it.id == state.licenciaId }
            ?: FkOption(id = "", label = stringResource(R.string.gestion_seleccionar)),
        options = state.licenciasDisponibles,
        optionLabel = { it.label },
        onSelected = { viewModel.onLicenciaChange(it.id) },
        enabled = state.licenciasDisponibles.isNotEmpty(),
        error = state.errores["licencia"]?.let {
            stringResource(R.string.gestion_validacion_requerido)
        },
    )
    GestionDropdown(
        label = stringResource(R.string.gestion_instalacion_local),
        selected = state.locales.firstOrNull { it.id == state.localId }
            ?: FkOption(id = "", label = stringResource(R.string.gestion_seleccionar)),
        options = state.locales,
        optionLabel = { it.label },
        onSelected = { viewModel.onLocalChange(it.id) },
        enabled = state.locales.isNotEmpty(),
        error = state.errores["local"]?.let {
            stringResource(R.string.gestion_validacion_requerido)
        },
    )
}
