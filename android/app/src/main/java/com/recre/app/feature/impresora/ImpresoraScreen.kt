package com.recre.app.feature.impresora

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.Eyebrow
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecreGhostButton
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreSnackbarHost
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.theme.GeistMono
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreShapes
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.printer.PrinterDevice
import com.recre.app.core.printer.PrinterError
import com.recre.app.core.printer.PrinterModelId
import com.recre.app.core.printer.PrinterProfile

/**
 * Pantalla "Vincular impresora" (T-62).
 *
 * Flujo:
 *  1. Si Android >= 12 y no hay BLUETOOTH_CONNECT, se muestra un CTA
 *     que dispara el `RequestPermission` launcher.
 *  2. Si Bluetooth está apagado, se muestra un CTA que abre los
 *     ajustes Bluetooth del sistema.
 *  3. Lista los dispositivos emparejados; cada Card tiene "Probar" y
 *     "Usar como impresora".
 *  4. Si ya hay una impresora persistida, se marca con un check y se
 *     ofrece "Quitar".
 *
 * No descubrimos dispositivos no emparejados — la AGPTEK PT210 se
 * empareja una única vez desde Ajustes (PIN 0000) y desde ahí en
 * adelante aparece en bondedDevices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpresoraScreen(
    onBack: () -> Unit,
    viewModel: ImpresoraViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permisoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onPermisoConcedido() else viewModel.refrescar()
    }

    // Cuando vuelve de los ajustes Bluetooth refrescamos el estado.
    LaunchedEffect(Unit) {
        viewModel.refrescar()
    }

    state.mensaje?.let { msg ->
        val texto = mensajeATexto(msg)
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(texto)
            viewModel.consumirMensaje()
        }
    }

    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = stringResource(R.string.impresora_titulo),
                onBack = onBack,
            )
        },
        snackbarHost = { RecreSnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            !state.tienePermiso -> SinPermiso(
                padding = padding,
                onSolicitar = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permisoLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        viewModel.refrescar()
                    }
                },
            )

            !state.bluetoothActivo -> BluetoothApagado(
                padding = padding,
                onAbrirAjustes = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                onReintentar = viewModel::refrescar,
            )

            else -> ListaEmparejados(
                padding = padding,
                emparejados = state.emparejados,
                seleccionada = state.seleccionada,
                perfilSeleccionado = state.perfilSeleccionado,
                perfilesDisponibles = state.perfilesDisponibles,
                probandoMac = state.probandoMac,
                onSeleccionar = viewModel::seleccionar,
                onSeleccionarPerfil = viewModel::seleccionarPerfil,
                onProbar = viewModel::probarImpresion,
                onLimpiar = viewModel::limpiarSeleccion,
                onAbrirAjustes = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SinPermiso(padding: PaddingValues, onSolicitar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.impresora_permiso_titulo),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.impresora_permiso_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        RecrePrimaryButton(
            text = stringResource(R.string.impresora_permiso_conceder),
            onClick = onSolicitar,
            fullWidth = false,
        )
    }
}

@Composable
private fun BluetoothApagado(
    padding: PaddingValues,
    onAbrirAjustes: () -> Unit,
    onReintentar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.impresora_bluetooth_apagado_titulo),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.impresora_bluetooth_apagado_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        RecrePrimaryButton(
            text = stringResource(R.string.impresora_bluetooth_abrir_ajustes),
            onClick = onAbrirAjustes,
            fullWidth = false,
        )
        Spacer(Modifier.height(8.dp))
        RecreTextButton(
            text = stringResource(R.string.impresora_reintentar),
            onClick = onReintentar,
        )
    }
}

@Composable
private fun ListaEmparejados(
    padding: PaddingValues,
    emparejados: List<PrinterDevice>,
    seleccionada: PrinterDevice?,
    perfilSeleccionado: PrinterProfile,
    perfilesDisponibles: List<PrinterProfile>,
    probandoMac: String?,
    onSeleccionar: (PrinterDevice) -> Unit,
    onSeleccionarPerfil: (PrinterProfile) -> Unit,
    onProbar: (PrinterDevice) -> Unit,
    onLimpiar: () -> Unit,
    onAbrirAjustes: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        // N9: subtítulo mono de cabecera; solo se llega aquí con el BT activo.
        Eyebrow(
            text = stringResource(R.string.impresora_bluetooth_activado),
            color = RecreColors.current.accentBright,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ModeloCard(
            perfilSeleccionado = perfilSeleccionado,
            perfilesDisponibles = perfilesDisponibles,
            onSeleccionarPerfil = onSeleccionarPerfil,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (emparejados.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.impresora_sin_emparejados_titulo),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.impresora_sin_emparejados_descripcion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                RecrePrimaryButton(
                    text = stringResource(R.string.impresora_abrir_ajustes_sistema),
                    onClick = onAbrirAjustes,
                    fullWidth = false,
                )
            }
            return
        }

        if (seleccionada != null) {
            SeleccionadaActualCard(
                seleccionada = seleccionada,
                onLimpiar = onLimpiar,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // N9: eyebrow de sección para la lista del sistema.
            item {
                Eyebrow(text = stringResource(R.string.impresora_emparejadas_titulo))
            }
            items(emparejados, key = PrinterDevice::mac) { device ->
                EmparejadoCard(
                    device = device,
                    activa = seleccionada?.mac == device.mac,
                    probando = probandoMac == device.mac,
                    bloqueada = probandoMac != null && probandoMac != device.mac,
                    onSeleccionar = { onSeleccionar(device) },
                    onProbar = { onProbar(device) },
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                // N9: pie de pantalla como acción ghost (antes RecreTextButton).
                RecreGhostButton(
                    text = stringResource(R.string.impresora_abrir_ajustes_sistema),
                    onClick = onAbrirAjustes,
                    fullWidth = true,
                    mini = true,
                )
            }
        }
    }
}

@Composable
private fun ModeloCard(
    perfilSeleccionado: PrinterProfile,
    perfilesDisponibles: List<PrinterProfile>,
    onSeleccionarPerfil: (PrinterProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column {
            Text(
                text = stringResource(R.string.impresora_modelo_titulo),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.impresora_modelo_descripcion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            perfilesDisponibles.forEach { perfil ->
                val seleccionado = perfil.id == perfilSeleccionado.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = seleccionado,
                            onClick = { onSeleccionarPerfil(perfil) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = seleccionado,
                        onClick = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = perfilANombre(perfil.id),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun perfilANombre(id: PrinterModelId): String = when (id) {
    PrinterModelId.PT210 -> stringResource(R.string.impresora_modelo_pt210)
    PrinterModelId.GENERICA_58 -> stringResource(R.string.impresora_modelo_generica_58)
    PrinterModelId.GENERICA_80 -> stringResource(R.string.impresora_modelo_generica_80)
    PrinterModelId.EPSON_TM_T20 -> stringResource(R.string.impresora_modelo_epson_tm_t20)
    PrinterModelId.XPRINTER_58 -> stringResource(R.string.impresora_modelo_xprinter_58)
}

@Composable
private fun SeleccionadaActualCard(
    seleccionada: PrinterDevice,
    onLimpiar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RecreShapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // N9: eyebrow «Impresora vinculada».
                Eyebrow(
                    text = stringResource(R.string.impresora_actual_titulo),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = seleccionada.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                // N9: MAC en Geist Mono.
                Text(
                    text = seleccionada.mac,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistMono),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // N9: «Quitar» como enlace ghost mini en acento inline.
            RecreGhostButton(
                text = stringResource(R.string.impresora_quitar),
                onClick = onLimpiar,
                mini = true,
            )
        }
    }
}

@Composable
private fun EmparejadoCard(
    device: PrinterDevice,
    activa: Boolean,
    probando: Boolean,
    bloqueada: Boolean,
    onSeleccionar: () -> Unit,
    onProbar: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (activa) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RecreShapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // N9 radio-fila: radio en acento cian + MAC en Geist Mono. La fila entera
            // es seleccionable (Role.RadioButton); «Usar» está deshabilitada si ya
            // es la activa, conservando el gating (bloqueada/probando).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = activa,
                        enabled = !bloqueada && !probando,
                        role = Role.RadioButton,
                        onClick = onSeleccionar,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = activa,
                    onClick = null,
                    enabled = !bloqueada && !probando,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = RecreColors.current.accentBright,
                        unselectedColor = RecreColors.current.accentBright,
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                    // N9: MAC en Geist Mono.
                    Text(
                        text = device.mac,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (probando) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.impresora_probando))
                        }
                    }
                }
                // N9: «Usar»/«Probar» como enlaces ghost mini en acento inline.
                RecreGhostButton(
                    text = stringResource(R.string.impresora_usar),
                    onClick = onSeleccionar,
                    enabled = !bloqueada && !probando && !activa,
                    mini = true,
                )
                Spacer(Modifier.width(8.dp))
                RecreGhostButton(
                    text = stringResource(R.string.impresora_probar_corto),
                    onClick = onProbar,
                    enabled = !bloqueada && !probando,
                    mini = true,
                )
            }
        }
    }
}

@Composable
private fun mensajeATexto(mensaje: ImpresoraMensaje): String = when (mensaje) {
    ImpresoraMensaje.PruebaOk -> stringResource(R.string.impresora_msg_prueba_ok)
    ImpresoraMensaje.SeleccionGuardada -> stringResource(R.string.impresora_msg_guardada)
    is ImpresoraMensaje.PruebaError -> when (mensaje.error) {
        PrinterError.BluetoothNoDisponible ->
            stringResource(R.string.impresora_error_bluetooth)
        PrinterError.SinPermiso ->
            stringResource(R.string.impresora_error_permiso)
        PrinterError.SinImpresora ->
            stringResource(R.string.impresora_error_sin_impresora)
        PrinterError.ModeloNoSoportado ->
            stringResource(R.string.impresora_error_modelo)
        PrinterError.NoEmparejada ->
            stringResource(R.string.impresora_error_no_emparejada)
        is PrinterError.ConexionFallida ->
            stringResource(R.string.impresora_error_conexion)
        is PrinterError.ImpresionFallida ->
            stringResource(R.string.impresora_error_impresion)
    }
}
