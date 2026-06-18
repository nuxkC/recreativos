package com.recre.app.feature.averias

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.FieldText
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreSnackbarHost
import com.recre.app.ui.components.RecreTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R

/**
 * Pantalla de reporte de avería por el técnico (T-222). Offline-first: al
 * guardar, el reporte se encola y se sube al recuperar la red (como la
 * recaudación). Captura categoría + descripción + recambios + fuera de servicio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportarAveriaScreen(
    onGuardado: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReportarAveriaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val errorMessage = state.errorCode?.let { stringResource(resolveAveriaErrorRes(it)) }

    LaunchedEffect(state.guardado) {
        if (state.guardado) onGuardado()
    }
    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message = msg)
        viewModel.consumeError()
    }

    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = stringResource(R.string.averia_reportar_titulo),
                onBack = onBack,
            )
        },
        snackbarHost = { RecreSnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (state.maquinaNumeroSerie.isNotBlank()) {
                Text(
                    text = stringResource(R.string.averia_reportar_maquina, state.maquinaNumeroSerie),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.averia_campo_categoria),
                style = MaterialTheme.typography.titleSmall,
            )
            CategoriaAveria.entries.forEach { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onCategoria(cat) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.categoria == cat,
                        onClick = { viewModel.onCategoria(cat) },
                    )
                    Text(stringResource(cat.labelRes), style = MaterialTheme.typography.bodyMedium)
                }
            }

            FieldText(
                value = state.descripcion,
                onValueChange = viewModel::onDescripcion,
                label = stringResource(R.string.averia_campo_descripcion),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.averia_campo_fuera_servicio),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.averia_campo_fuera_servicio_ayuda),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.poneFueraServicio,
                    onCheckedChange = viewModel::onToggleFueraServicio,
                )
            }

            // Merma de tolva (§5.6): solo si la máquina está instalada; sin
            // instalación activa la propia `crear_averia` rechazaría el premio.
            if (state.maquinaInstalada) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.averia_campo_afecta_tolva),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.averia_campo_afecta_tolva_ayuda),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.afectaTolva,
                            onCheckedChange = viewModel::onToggleAfectaTolva,
                        )
                    }
                    if (state.afectaTolva) {
                        FieldText(
                            value = state.importeTolva,
                            onValueChange = viewModel::onImporteTolva,
                            label = stringResource(R.string.averia_campo_importe_tolva),
                            isError = state.importeTolva.isNotBlank() &&
                                state.importeTolvaNormalizado == null,
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            RecambiosEditor(
                recambios = state.recambios,
                onAdd = viewModel::addRecambio,
                onRemove = viewModel::removeRecambio,
            )

            FieldText(
                value = state.notas,
                onValueChange = viewModel::onNotas,
                label = stringResource(R.string.averia_campo_notas),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            RecrePrimaryButton(
                text = stringResource(R.string.averia_reportar_guardar),
                onClick = viewModel::onGuardar,
                enabled = state.canGuardar,
            )
            Text(
                text = stringResource(R.string.averia_reportar_offline_ayuda),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Editor inline de recambios: lista lo añadido y permite agregar uno nuevo. */
@Composable
private fun RecambiosEditor(
    recambios: List<RecambioFormItem>,
    onAdd: (pieza: String, cantidad: String) -> Boolean,
    onRemove: (Int) -> Unit,
) {
    AppCard {
        Column {
            Text(
                stringResource(R.string.averia_recambios_titulo),
                style = MaterialTheme.typography.titleSmall,
            )
            if (recambios.isEmpty()) {
                Text(
                    stringResource(R.string.averia_recambios_vacio),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            recambios.forEachIndexed { index, r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildString {
                            append(r.pieza)
                            append("  ×")
                            append(r.cantidad)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            var pieza by remember { mutableStateOf("") }
            var cantidad by remember { mutableStateOf("1") }

            FieldText(
                value = pieza,
                onValueChange = { pieza = it },
                label = stringResource(R.string.averia_recambio_pieza),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FieldText(
                value = cantidad,
                onValueChange = { cantidad = it.filter { c -> c.isDigit() }.take(4) },
                label = stringResource(R.string.averia_recambio_cantidad),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            RecreTonalButton(
                text = stringResource(R.string.averia_recambio_anadir),
                onClick = {
                    if (onAdd(pieza, cantidad)) {
                        pieza = ""
                        cantidad = "1"
                    }
                },
                leadingIcon = Icons.Default.Add,
                fullWidth = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
