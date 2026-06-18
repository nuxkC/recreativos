package com.recre.app.feature.gestion.locales

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
import com.recre.app.feature.gestion.components.GestionFormScaffold
import com.recre.app.feature.gestion.components.GestionTextField
import com.recre.app.feature.gestion.resolveErrorRes
import com.recre.app.ui.components.SnackbarEstado
import com.recre.app.ui.components.mostrar

/**
 * Formulario de alta / edición de Local (T-68). Rediseño F3·P5: chrome y
 * botón propios (`GestionFormScaffold`), campos de la librería.
 */
@Composable
fun LocalFormScreen(
    onBack: () -> Unit,
    onGuardado: () -> Unit,
    viewModel: LocalFormViewModel = hiltViewModel(),
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

    GestionFormScaffold(
        titulo = stringResource(
            if (state.esEdicion) R.string.gestion_local_editar
            else R.string.gestion_local_nuevo,
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
            label = stringResource(R.string.gestion_local_nombre),
            value = state.nombre,
            onValueChange = viewModel::onNombreChange,
            error = state.errores["nombre"]?.let {
                stringResource(R.string.gestion_validacion_requerido)
            },
        )
        GestionTextField(
            label = stringResource(R.string.gestion_local_direccion),
            value = state.direccion,
            onValueChange = viewModel::onDireccionChange,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            GestionTextField(
                label = stringResource(R.string.gestion_local_cif),
                value = state.cifONif,
                onValueChange = viewModel::onCifChange,
                modifier = Modifier.weight(1f),
            )
            GestionTextField(
                label = stringResource(R.string.gestion_local_telefono),
                value = state.telefono,
                onValueChange = viewModel::onTelefonoChange,
                keyboardType = KeyboardType.Phone,
                modifier = Modifier.weight(1f),
            )
        }
        GestionTextField(
            label = stringResource(R.string.gestion_local_titular),
            value = state.titularNombre,
            onValueChange = viewModel::onTitularChange,
        )
        GestionTextField(
            label = stringResource(R.string.gestion_local_email),
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            keyboardType = KeyboardType.Email,
            error = state.errores["email"]?.let {
                stringResource(R.string.gestion_validacion_email)
            },
        )
        GestionTextField(
            label = stringResource(R.string.gestion_local_notas),
            value = state.notas,
            onValueChange = viewModel::onNotasChange,
            singleLine = false,
            minLines = 3,
        )
    }
}
