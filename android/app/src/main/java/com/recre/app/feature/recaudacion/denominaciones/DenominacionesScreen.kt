package com.recre.app.feature.recaudacion.denominaciones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recre.app.R
import com.recre.app.core.calculo.DENOMINACIONES_PERMITIDAS
import com.recre.app.core.calculo.importesIguales
import com.recre.app.feature.recaudacion.RecaudacionFlowViewModel
import com.recre.app.feature.recaudacion.RecaudacionTestTags
import com.recre.app.feature.recaudacion.components.RecuperacionResumenCard
import com.recre.app.feature.recaudacion.components.formatEur
import java.math.BigDecimal

/**
 * Pantalla de desglose de denominaciones (T-55).
 *
 * Reutilizada dos veces dentro del flujo:
 *   - Modo `Total`: la suma debe coincidir con `cifras.bruto`.
 *   - Modo `Local`: la suma debe coincidir con `pagado_local` (parte_local
 *     menos lo recuperado de la deuda del local, T-215). Sin deuda o con
 *     pct=0, `pagado_local == parte_local` y el comportamiento es el de antes.
 *
 * Componente reutilizable: solo cambia el target esperado y el valor
 * inicial del map. La validación de "suma exacta" usa
 * [importesIguales] para no fallar por diferencias de escala (p. ej.
 * `5` vs `5.00`).
 *
 * El botón "Continuar" se habilita solo cuando la diferencia con el
 * target es exactamente 0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DenominacionesScreen(
    viewModel: RecaudacionFlowViewModel,
    modo: ModoDenominaciones,
    onContinuar: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cifras = state.cifras
    val target: BigDecimal? = when (modo) {
        ModoDenominaciones.Total -> cifras?.bruto
        // En modo Local el objetivo es lo que se entrega al local (pagado_local
        // = parte_local − recuperado). Si no hay plan, cae a parte_local.
        ModoDenominaciones.Local -> state.recuperacion?.pagadoLocal ?: cifras?.parteLocal
    }
    val map = when (modo) {
        ModoDenominaciones.Total -> state.denominacionesTotal
        ModoDenominaciones.Local -> state.denominacionesLocal
    }
    val totalActual = viewModel.sumarDesgloseDe(map)
    val diferencia = target?.subtract(totalActual)?.setScale(2, java.math.RoundingMode.HALF_UP)
    val cuadra = target != null && diferencia != null && importesIguales(diferencia, BigDecimal.ZERO)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (modo) {
                                ModoDenominaciones.Total ->
                                    stringResource(R.string.recaudacion_denominaciones_total_titulo)
                                ModoDenominaciones.Local ->
                                    stringResource(R.string.recaudacion_denominaciones_local_titulo)
                            },
                        )
                        if (target != null) {
                            Text(
                                text = stringResource(
                                    R.string.recaudacion_denominaciones_objetivo,
                                    formatEur(target.toPlainString()),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValuesAll(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Resumen de recuperación (solo modo Local y cuando hay deuda
                // que se amortiza con esta recaudación).
                val plan = state.recuperacion
                if (modo == ModoDenominaciones.Local && plan != null &&
                    plan.recuperadoTotal.signum() > 0
                ) {
                    item("recuperacion") {
                        RecuperacionResumenCard(
                            creditos = state.creditosAbiertos,
                            plan = plan,
                            ordenManual = state.ordenManual,
                            reordenable = true,
                            onSubir = viewModel::moverCreditoArriba,
                            onBajar = viewModel::moverCreditoAbajo,
                        )
                    }
                }
                items(DENOMINACIONES_PERMITIDAS, key = { it.toPlainString() }) { denominacion ->
                    val key = denominacion.toPlainString()
                    DenominacionRow(
                        denominacion = denominacion,
                        cantidad = map[key] ?: 0,
                        onCantidadChange = { nueva ->
                            when (modo) {
                                ModoDenominaciones.Total ->
                                    viewModel.onDenominacionTotalChange(key, nueva)
                                ModoDenominaciones.Local ->
                                    viewModel.onDenominacionLocalChange(key, nueva)
                            }
                        },
                    )
                }
            }
            Footer(
                target = target,
                totalActual = totalActual,
                diferencia = diferencia,
                cuadra = cuadra,
                onContinuar = onContinuar,
            )
        }
    }
}

@Composable
private fun DenominacionRow(
    denominacion: BigDecimal,
    cantidad: Int,
    onCantidadChange: (Int) -> Unit,
) {
    val texto = if (cantidad == 0) "" else cantidad.toString()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatEur(denominacion.toPlainString()),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(96.dp),
        )
        OutlinedTextField(
            value = texto,
            onValueChange = { raw ->
                val sanitized = raw.filter { it.isDigit() }.take(6)
                onCantidadChange(sanitized.toIntOrNull() ?: 0)
            },
            modifier = Modifier
                .weight(1f)
                .testTag(RecaudacionTestTags.denominacionCantidad(denominacion.toPlainString())),
            label = { Text(stringResource(R.string.recaudacion_label_cantidad)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatEur(
                denominacion.multiply(BigDecimal(cantidad))
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .toPlainString(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Footer(
    target: BigDecimal?,
    totalActual: BigDecimal,
    diferencia: BigDecimal?,
    cuadra: Boolean,
    onContinuar: () -> Unit,
) {
    HorizontalDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        FilaTotal(
            label = stringResource(R.string.recaudacion_denominaciones_total),
            value = formatEur(totalActual.toPlainString()),
            destacado = false,
        )
        if (target != null && diferencia != null) {
            FilaTotal(
                label = stringResource(R.string.recaudacion_denominaciones_diferencia),
                value = formatEur(diferencia.toPlainString()),
                destacado = !cuadra,
                modifier = Modifier.testTag(RecaudacionTestTags.DENOMINACIONES_DIFERENCIA),
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onContinuar,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RecaudacionTestTags.DENOMINACIONES_CONTINUAR),
            enabled = cuadra,
        ) {
            Text(stringResource(R.string.recaudacion_accion_continuar))
        }
    }
}

@Composable
private fun FilaTotal(label: String, value: String, destacado: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (destacado) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (destacado) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * Helper local para evitar dependencia con `androidx.compose.foundation.layout.PaddingValues`
 * con kwargs `horizontal=`/`vertical=` cuya signatura cambió entre
 * versiones de Compose. Mantiene el call site limpio.
 */
private fun PaddingValuesAll(
    horizontal: androidx.compose.ui.unit.Dp,
    vertical: androidx.compose.ui.unit.Dp,
) = androidx.compose.foundation.layout.PaddingValues(
    start = horizontal,
    end = horizontal,
    top = vertical,
    bottom = vertical,
)

enum class ModoDenominaciones {
    Total,
    Local,
}
