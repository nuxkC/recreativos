package com.recre.app.feature.gestion.licencias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.feature.gestion.ESTADOS_LICENCIA
import com.recre.app.feature.gestion.components.GestionDropdown
import com.recre.app.feature.gestion.components.GestionFormScaffold
import com.recre.app.feature.gestion.components.GestionTextField
import com.recre.app.feature.gestion.resolveErrorRes
import com.recre.app.ui.components.FieldDate
import com.recre.app.ui.components.SnackbarEstado
import com.recre.app.ui.components.mostrar

/**
 * Formulario de alta / edición de Licencia (T-66).
 *
 * Rediseño (F3·P5): chrome y botón propios (`GestionFormScaffold`); campos
 * de la librería (`GestionTextField`/`GestionDropdown`/`FieldDate`) con
 * validación inline. El "Guardar" es el único primario.
 */
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
        snackbarHost.mostrar(SnackbarEstado.Error, msg)
        viewModel.consumirError()
    }

    GestionFormScaffold(
        titulo = stringResource(
            if (state.esEdicion) R.string.gestion_licencia_editar
            else R.string.gestion_licencia_nueva,
        ),
        onBack = onBack,
        cargando = state.cargando,
        online = state.online,
        snackbarHost = snackbarHost,
        guardarLabel = stringResource(R.string.gestion_guardar),
        guardando = state.guardando,
        onGuardar = viewModel::guardar,
    ) {
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
            FieldDate(
                label = stringResource(R.string.gestion_licencia_fecha_expedicion),
                value = state.fechaExpedicion,
                onValueChange = viewModel::onFechaExpedicionChange,
                isError = state.errores["fechaExpedicion"] != null,
                errorText = state.errores["fechaExpedicion"]?.let {
                    stringResource(R.string.gestion_validacion_fecha)
                },
                modifier = Modifier.weight(1f),
            )
            FieldDate(
                label = stringResource(R.string.gestion_licencia_fecha_caducidad),
                value = state.fechaCaducidad,
                onValueChange = viewModel::onFechaCaducidadChange,
                minIso = state.fechaExpedicion,
                isError = state.errores["fechaCaducidad"] != null,
                errorText = state.errores["fechaCaducidad"]?.let { code ->
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
    }
}
