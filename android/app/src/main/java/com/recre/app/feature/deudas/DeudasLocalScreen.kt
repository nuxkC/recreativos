package com.recre.app.feature.deudas

import com.recre.app.ui.components.ListSkeleton
import com.recre.app.ui.components.formatEur
import com.recre.app.ui.components.recreSharedBounds

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.FieldText
import com.recre.app.ui.components.RecreDetailTopBar
import com.recre.app.ui.components.RecrePrimaryButton
import com.recre.app.ui.components.RecreSnackbarHost
import com.recre.app.ui.components.RecreTextButton
import com.recre.app.ui.components.RecreTonalButton
import com.recre.app.ui.theme.RecreShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.data.local.entity.CreditoLocalEntity
import com.recre.app.core.data.repository.RecuperacionLedger
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Ficha de deudas (tolva + préstamos) de un local (T-215).
 *
 * Muestra el saldo agregado y las deudas abiertas (offline, desde cache) y
 * permite a gestor+ registrar abonos en efectivo, dar préstamos y ajustar el %
 * de recuperación; condonar requiere admin. Las escrituras necesitan conexión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeudasLocalScreen(
    localId: String,
    onBack: () -> Unit,
    viewModel: DeudasLocalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    val errorMsg = state.errorCode?.let { stringResource(deudaErrorRes(it)) }
    LaunchedEffect(errorMsg) {
        val msg = errorMsg ?: return@LaunchedEffect
        snackbarHost.showSnackbar(msg)
        viewModel.consumirError()
    }
    val okMsg = state.mensajeOkRes?.let { stringResource(it) }
    LaunchedEffect(okMsg) {
        val msg = okMsg ?: return@LaunchedEffect
        snackbarHost.showSnackbar(msg)
        viewModel.consumirMensaje()
    }

    Scaffold(
        topBar = {
            RecreDetailTopBar(
                titulo = state.nombreLocal.ifBlank { stringResource(R.string.deudas_titulo) },
                onBack = onBack,
                // T-244: par compartido con el nombre de la card de la lista de deudas.
                tituloModifier = Modifier.recreSharedBounds("deuda-nombre-$localId"),
            )
        },
        snackbarHost = { RecreSnackbarHost(snackbarHost) },
    ) { padding ->
        if (!state.cargado) {
            // T-241: esqueleto de carga en vez de spinner (plan §3.1).
            ListSkeleton(
                loadingLabel = stringResource(R.string.cargando),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.online) {
                item("offline") {
                    Text(
                        text = stringResource(R.string.deudas_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item("saldo") { SaldoCard(state) }
            item("porcentaje") {
                PorcentajeCard(state, onEditar = viewModel::abrirPorcentaje)
            }
            if (state.esGestor) {
                item("nuevo-prestamo") {
                    RecreTonalButton(
                        text = stringResource(R.string.deudas_nuevo_prestamo),
                        onClick = viewModel::abrirNuevoPrestamo,
                        enabled = state.online && !state.operando,
                        fullWidth = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item("deudas-titulo") {
                Text(
                    text = stringResource(R.string.deudas_seccion_deudas),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (state.creditos.isEmpty()) {
                item("sin-deudas") {
                    Text(
                        text = stringResource(R.string.deudas_sin_deudas),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.creditos, key = { it.creditoId }) { credito ->
                    DeudaCard(
                        credito = credito,
                        esGestor = state.esGestor,
                        esAdmin = state.esAdmin,
                        online = state.online && !state.operando,
                        onPago = { viewModel.abrirPago(credito) },
                        onCondonar = { viewModel.abrirCondonar(credito) },
                    )
                }
            }

            item("ledger-titulo") {
                Text(
                    text = stringResource(R.string.deudas_ledger_titulo),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.ledger.isEmpty()) {
                item("ledger-vacio") {
                    Text(
                        text = stringResource(R.string.deudas_ledger_vacio),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.ledger, key = { it.id }) { entrada -> LedgerRow(entrada) }
            }
        }
    }

    // -- Diálogos -----------------------------------------------------------
    when (state.dialog) {
        DeudaDialog.NUEVO_PRESTAMO -> NuevoPrestamoDialog(
            onConfirmar = viewModel::crearPrestamo,
            onCancelar = viewModel::cerrarDialog,
        )
        DeudaDialog.PAGO -> state.creditoSeleccionado?.let { c ->
            PagoDialog(
                credito = c,
                onConfirmar = { importe, notas -> viewModel.registrarPago(c.creditoId, importe, notas) },
                onCancelar = viewModel::cerrarDialog,
            )
        }
        DeudaDialog.CONDONAR -> state.creditoSeleccionado?.let { c ->
            CondonarDialog(
                credito = c,
                onConfirmar = { notas -> viewModel.condonar(c.creditoId, notas) },
                onCancelar = viewModel::cerrarDialog,
            )
        }
        DeudaDialog.PORCENTAJE -> PorcentajeDialog(
            actual = state.porcentajeLocal,
            porcentajeEmpresa = state.porcentajeEmpresa,
            onConfirmar = viewModel::setPorcentaje,
            onCancelar = viewModel::cerrarDialog,
        )
        DeudaDialog.NINGUNO -> Unit
    }
}

@Composable
private fun SaldoCard(state: DeudasLocalUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RecreShapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.deudas_saldo_total),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = eur(state.saldoTotal),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.deudas_saldo_tolva, eur(state.saldoTolva)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.deudas_saldo_prestamo, eur(state.saldoPrestamo)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.deudas_num_deudas, state.creditos.size),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PorcentajeCard(state: DeudasLocalUiState, onEditar: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.deudas_porcentaje_titulo),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        R.string.deudas_porcentaje_efectivo,
                        state.porcentajeEfectivo,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (state.porcentajeLocal == null) {
                        stringResource(R.string.deudas_porcentaje_heredado, state.porcentajeEmpresa)
                    } else {
                        stringResource(R.string.deudas_porcentaje_override)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.esGestor) {
                RecreTextButton(
                    text = stringResource(R.string.deudas_porcentaje_editar),
                    onClick = onEditar,
                    enabled = state.online && !state.operando,
                )
            }
        }
    }
}

@Composable
private fun DeudaCard(
    credito: CreditoLocalEntity,
    esGestor: Boolean,
    esAdmin: Boolean,
    online: Boolean,
    onPago: () -> Unit,
    onCondonar: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = if (credito.tipo == "tolva") {
                    stringResource(R.string.deudas_tipo_tolva)
                } else {
                    stringResource(R.string.deudas_tipo_prestamo)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.deudas_deuda_saldo, eur(BigDecimal(credito.saldo))),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.deudas_deuda_fecha, credito.fecha),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (esGestor) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecrePrimaryButton(
                        text = stringResource(R.string.deudas_pago_accion),
                        onClick = onPago,
                        enabled = online,
                        fullWidth = false,
                    )
                    if (esAdmin) {
                        RecreTonalButton(
                            text = stringResource(R.string.deudas_condonar_accion),
                            onClick = onCondonar,
                            enabled = online,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(entrada: RecuperacionLedger) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (entrada.origen == "efectivo") {
                    stringResource(R.string.deudas_ledger_origen_efectivo)
                } else {
                    stringResource(R.string.deudas_ledger_origen_recaudacion)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = entrada.fecha.take(10),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = "− " + eur(entrada.importe), style = MaterialTheme.typography.bodyMedium)
    }
}

// -- Diálogos ---------------------------------------------------------------

@Composable
private fun NuevoPrestamoDialog(
    onConfirmar: (principal: String, notas: String?) -> Unit,
    onCancelar: () -> Unit,
) {
    var principal by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    // El concepto es obligatorio al dar de alta un préstamo (T-216).
    val valido = normalizarImporte(principal) != null && notas.isNotBlank()
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.deudas_nuevo_prestamo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldText(
                    value = principal,
                    onValueChange = { principal = it },
                    label = stringResource(R.string.deudas_prestamo_principal),
                    keyboardType = KeyboardType.Decimal,
                )
                FieldText(
                    value = notas,
                    onValueChange = { notas = it },
                    label = stringResource(R.string.deudas_prestamo_concepto),
                    description = stringResource(R.string.deudas_prestamo_concepto_ayuda),
                    singleLine = false,
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            RecreTextButton(
                text = stringResource(R.string.gestion_guardar),
                onClick = { onConfirmar(normalizarImporte(principal)!!, notas.trim()) },
                enabled = valido,
            )
        },
        dismissButton = {
            RecreTextButton(text = stringResource(R.string.action_cancel), onClick = onCancelar)
        },
    )
}

@Composable
private fun PagoDialog(
    credito: CreditoLocalEntity,
    onConfirmar: (importe: String, notas: String?) -> Unit,
    onCancelar: () -> Unit,
) {
    var importe by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    val valido = normalizarImporte(importe) != null
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.deudas_pago_titulo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.deudas_pago_saldo,
                        eur(BigDecimal(credito.saldo)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FieldText(
                    value = importe,
                    onValueChange = { importe = it },
                    label = stringResource(R.string.deudas_pago_importe),
                    keyboardType = KeyboardType.Decimal,
                )
                FieldText(
                    value = notas,
                    onValueChange = { notas = it },
                    label = stringResource(R.string.deudas_pago_notas),
                    singleLine = false,
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            RecreTextButton(
                text = stringResource(R.string.gestion_guardar),
                onClick = { onConfirmar(normalizarImporte(importe)!!, notas.ifBlank { null }) },
                enabled = valido,
            )
        },
        dismissButton = {
            RecreTextButton(text = stringResource(R.string.action_cancel), onClick = onCancelar)
        },
    )
}

@Composable
private fun CondonarDialog(
    credito: CreditoLocalEntity,
    onConfirmar: (notas: String?) -> Unit,
    onCancelar: () -> Unit,
) {
    var notas by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.deudas_condonar_titulo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.deudas_condonar_descripcion,
                        eur(BigDecimal(credito.saldo)),
                    ),
                )
                FieldText(
                    value = notas,
                    onValueChange = { notas = it },
                    label = stringResource(R.string.deudas_prestamo_notas),
                    singleLine = false,
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            RecreTextButton(
                text = stringResource(R.string.deudas_condonar_confirmar),
                onClick = { onConfirmar(notas.ifBlank { null }) },
            )
        },
        dismissButton = {
            RecreTextButton(text = stringResource(R.string.action_cancel), onClick = onCancelar)
        },
    )
}

@Composable
private fun PorcentajeDialog(
    actual: Int?,
    porcentajeEmpresa: Int,
    onConfirmar: (porcentaje: Int?) -> Unit,
    onCancelar: () -> Unit,
) {
    var heredar by remember { mutableStateOf(actual == null) }
    var valor by remember { mutableStateOf((actual ?: porcentajeEmpresa).toString()) }
    val parsed = valor.toIntOrNull()
    val valido = heredar || (parsed != null && parsed in 0..100)
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.deudas_porcentaje_titulo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = heredar, onCheckedChange = { heredar = it })
                    Text(
                        text = stringResource(
                            R.string.deudas_porcentaje_heredar,
                            porcentajeEmpresa,
                        ),
                    )
                }
                if (!heredar) {
                    FieldText(
                        value = valor,
                        onValueChange = { valor = it.filter { c -> c.isDigit() }.take(3) },
                        label = stringResource(R.string.deudas_porcentaje_valor),
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
        },
        confirmButton = {
            RecreTextButton(
                text = stringResource(R.string.gestion_guardar),
                onClick = { onConfirmar(if (heredar) null else parsed) },
                enabled = valido,
            )
        },
        dismissButton = {
            RecreTextButton(text = stringResource(R.string.action_cancel), onClick = onCancelar)
        },
    )
}

private fun deudaErrorRes(code: String): Int = when (code) {
    "deuda_sin_permiso" -> R.string.deudas_error_sin_permiso
    "deuda_importe_supera_saldo" -> R.string.deudas_error_importe_supera_saldo
    "deuda_operacion_invalida" -> R.string.deudas_error_operacion_invalida
    "deuda_no_encontrada" -> R.string.deudas_error_no_encontrada
    "network" -> R.string.deudas_error_network
    "auth" -> R.string.deudas_error_sin_permiso
    else -> R.string.deudas_error_generic
}

/** Formato de euros es-ES: «1.234,50 €». Delega en el canónico money-safe. */
private fun eur(v: BigDecimal): String = formatEur(v)

/**
 * Normaliza un importe monetario tecleado (`"0,20"` / `" 30.5 "`) al string
 * canónico `"X.YY"`. Devuelve `null` si no es un número > 0. La validación
 * definitiva (saldo, rango) la hace el servidor.
 */
private fun normalizarImporte(raw: String): String? {
    val limpio = raw.trim().replace(",", ".")
    if (limpio.isEmpty()) return null
    val dec = limpio.toBigDecimalOrNull() ?: return null
    if (dec <= BigDecimal.ZERO) return null
    return dec.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
