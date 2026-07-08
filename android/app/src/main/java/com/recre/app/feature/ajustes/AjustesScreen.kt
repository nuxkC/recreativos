package com.recre.app.feature.ajustes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.Pip
import com.recre.app.ui.components.RecreBottomBar
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.components.RecreTopBar
import com.recre.app.ui.components.RecreTopBarActions
import com.recre.app.ui.components.SegmentOption
import com.recre.app.ui.components.SegmentedControl
import com.recre.app.ui.components.TopLevelDestination
import com.recre.app.ui.theme.RecreShapes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.local.TamanoUi
import com.recre.app.core.printer.PrinterDevice
import com.recre.app.core.printer.PrinterModelId
import com.recre.app.core.printer.PrinterProfile
import java.time.Duration
import java.time.Instant

/**
 * Pantalla de Ajustes (T-65 · pestañas T-235).
 *
 * Config pura del técnico autenticado, organizada en dos pestañas
 * (`SegmentedControl`):
 *
 *  - **Cuenta**: email del usuario; empresa activa + "Cambiar de empresa" (si
 *    hay >1 membresía); cerrar sesión (con confirmación).
 *  - **Dispositivo**: sincronización (última sync, badge de stale, "Sincronizar
 *    ahora") e impresora Bluetooth (T-62; atajo a [ImpresoraScreen]).
 *
 * Los atajos a Histórico/Alertas se retiran: Histórico es una pestaña del bottom
 * nav y las alertas viven en la campana del top bar global (T-234).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    onSelectTab: (TopLevelDestination) -> Unit,
    onImpresoraClick: () -> Unit,
    onAlertasClick: () -> Unit,
    onIncidenciasClick: () -> Unit,
    viewModel: AjustesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var mostrandoConfirmacionLogout by remember { mutableStateOf(false) }
    var seccionActiva by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            RecreTopBar(
                titulo = stringResource(R.string.ajustes_titulo),
                actions = { RecreTopBarActions(onAlertasClick = onAlertasClick, onIncidenciasClick = onIncidenciasClick) },
            )
        },
        bottomBar = {
            RecreBottomBar(current = TopLevelDestination.AJUSTES, onSelect = onSelectTab)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedControl(
                options = listOf(
                    SegmentOption(stringResource(R.string.ajustes_tab_cuenta)),
                    SegmentOption(stringResource(R.string.ajustes_tab_dispositivo)),
                ),
                selectedIndex = seccionActiva,
                onSelect = { seccionActiva = it },
                groupLabel = stringResource(R.string.ajustes_secciones),
                modifier = Modifier.fillMaxWidth(),
            )

            // Cuenta: identidad, empresa y sesión. Dispositivo: sync e impresora.
            // Los atajos a Histórico/Alertas se retiran (T-235): Histórico es una
            // pestaña y las alertas viven en la campana del top bar.
            when (seccionActiva) {
                0 -> {
                    CuentaCard(email = state.emailUsuario)
                    EmpresaCard(
                        nombre = state.empresaNombre,
                        rol = state.rolEnEmpresa,
                        multiple = state.multipleEmpresas,
                        onCambiar = viewModel::cambiarEmpresa,
                    )
                    SesionCard(onLogoutClick = { mostrandoConfirmacionLogout = true })
                }
                else -> {
                    SyncCard(
                        ultimaSync = state.ultimaSync,
                        sincronizando = state.sincronizando,
                        stale = state.syncStale,
                        onSincronizar = viewModel::forzarSync,
                    )
                    ImpresoraCard(
                        impresora = state.impresoraSeleccionada,
                        perfil = state.perfilImpresora,
                        onAccion = onImpresoraClick,
                    )
                    TamanoCard(
                        tamano = state.tamanoUi,
                        onSelect = viewModel::setTamano,
                    )
                }
            }
        }
    }

    if (mostrandoConfirmacionLogout) {
        AlertDialog(
            onDismissRequest = { mostrandoConfirmacionLogout = false },
            title = { Text(stringResource(R.string.ajustes_logout_titulo)) },
            text = { Text(stringResource(R.string.ajustes_logout_descripcion)) },
            confirmButton = {
                RecreTextButton(
                    text = stringResource(R.string.auth_signout),
                    onClick = {
                        mostrandoConfirmacionLogout = false
                        viewModel.cerrarSesion()
                    },
                )
            },
            dismissButton = {
                RecreTextButton(
                    text = stringResource(R.string.ajustes_logout_cancelar),
                    onClick = { mostrandoConfirmacionLogout = false },
                )
            },
        )
    }
}

// ---------------------------------------------------------- secciones

@Composable
private fun CuentaCard(email: String?) {
    if (email == null) return
    SeccionCard(
        titulo = stringResource(R.string.ajustes_seccion_cuenta),
    ) {
        ItemRow(
            icon = Icons.Default.Person,
            primary = email,
            secondary = stringResource(R.string.ajustes_cuenta_email_descripcion),
        )
    }
}

@Composable
private fun EmpresaCard(
    nombre: String,
    rol: String,
    multiple: Boolean,
    onCambiar: () -> Unit,
) {
    SeccionCard(
        titulo = stringResource(R.string.ajustes_seccion_empresa),
        actions = if (multiple) {
            listOf(stringResource(R.string.ajustes_cambiar_empresa) to onCambiar)
        } else {
            emptyList()
        },
    ) {
        ItemRow(
            icon = Icons.Default.SwitchAccount,
            primary = nombre.ifEmpty { "—" },
            secondary = rol,
        )
    }
}

@Composable
private fun SyncCard(
    ultimaSync: Instant?,
    sincronizando: Boolean,
    stale: Boolean,
    onSincronizar: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (stale) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (stale) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RecreShapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ajustes_seccion_sync),
                style = MaterialTheme.typography.titleSmall,
                color = if (stale) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = if (stale) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatUltimaSync(sincronizando, ultimaSync),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (stale) {
                        Text(
                            text = stringResource(R.string.ajustes_sync_stale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                if (sincronizando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                RecreTextButton(
                    text = stringResource(R.string.sync_force),
                    onClick = onSincronizar,
                    enabled = !sincronizando,
                    leadingIcon = Icons.Default.Refresh,
                )
            }
        }
    }
}

@Composable
private fun ImpresoraCard(
    impresora: PrinterDevice?,
    perfil: PrinterProfile,
    onAccion: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = stringResource(R.string.ajustes_seccion_impresora),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (impresora != null) {
                        Text(text = impresora.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = impresora.mac,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.ajustes_impresora_ninguna),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.ajustes_impresora_ninguna_descripcion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.ajustes_impresora_modelo,
                            perfilANombre(perfil.id),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                RecreTextButton(
                    text = if (impresora == null) {
                        stringResource(R.string.ajustes_impresora_vincular)
                    } else {
                        stringResource(R.string.ajustes_impresora_cambiar)
                    },
                    onClick = onAccion,
                )
            }
        }
    }
}



@Composable
private fun TamanoCard(
    tamano: TamanoUi,
    onSelect: (TamanoUi) -> Unit,
) {
    SeccionCard(titulo = stringResource(R.string.ajustes_seccion_tamano)) {
        Text(
            text = stringResource(R.string.ajustes_tamano_descripcion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SegmentedControl(
            options = listOf(
                SegmentOption(stringResource(R.string.ajustes_tamano_compacto)),
                SegmentOption(stringResource(R.string.ajustes_tamano_estandar)),
                SegmentOption(stringResource(R.string.ajustes_tamano_grande)),
            ),
            selectedIndex = tamano.ordinal,
            onSelect = { onSelect(TamanoUi.values()[it]) },
            groupLabel = stringResource(R.string.ajustes_seccion_tamano),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SesionCard(onLogoutClick: () -> Unit) {
    SeccionCard(titulo = stringResource(R.string.ajustes_seccion_sesion)) {
        ItemRow(
            icon = Icons.AutoMirrored.Filled.Logout,
            primary = stringResource(R.string.auth_signout),
            secondary = stringResource(R.string.ajustes_sesion_descripcion),
            onClick = onLogoutClick,
        )
    }
}

// ---------------------------------------------------------- helpers

@Composable
private fun SeccionCard(
    titulo: String,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
    content: @Composable () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            content()
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    actions.forEach { (label, onClick) ->
                        RecreTextButton(text = label, onClick = onClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    icon: ImageVector,
    primary: String,
    secondary: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
    val rowModifier = if (onClick != null) baseModifier.clickable(onClick = onClick) else baseModifier
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pip(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = primary, style = MaterialTheme.typography.bodyLarge)
            secondary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun formatUltimaSync(sincronizando: Boolean, ultima: Instant?): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return when {
        sincronizando -> stringResource(R.string.sync_in_progress)
        ultima == null -> stringResource(R.string.sync_never)
        else -> {
            val minutes = Duration.between(ultima, Instant.now()).toMinutes()
            when {
                minutes < 1 -> context.getString(R.string.sync_just_now)
                minutes < 60 -> context.getString(R.string.sync_minutes_ago, minutes)
                minutes < 60 * 24 -> context.getString(R.string.sync_hours_ago, minutes / 60)
                else -> context.getString(R.string.sync_days_ago, minutes / (60 * 24))
            }
        }
    }
}
