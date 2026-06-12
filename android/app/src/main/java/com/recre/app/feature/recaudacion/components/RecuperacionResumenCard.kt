package com.recre.app.feature.recaudacion.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.core.calculo.CreditoAbierto
import com.recre.app.core.calculo.PlanRecuperacion
import java.math.BigDecimal

/**
 * Resumen de la recuperación de deuda durante una recaudación (T-215).
 *
 * Muestra cuánto se retiene de la parte_local para amortizar las deudas del
 * local y cuánto se le entrega en efectivo (`pagado_local`), más el reparto
 * por deuda en el orden de imputación. Si [reordenable] es `true`, ofrece
 * flechas para que el técnico priorice una deuda sobre otra.
 *
 * Es informativo: el servidor recalcula el plan como SSOT al persistir. Solo se
 * pinta cuando hay recuperación (`recuperadoTotal > 0`).
 */
@Composable
fun RecuperacionResumenCard(
    creditos: List<CreditoAbierto>,
    plan: PlanRecuperacion,
    ordenManual: List<String>?,
    reordenable: Boolean,
    onSubir: (String) -> Unit,
    onBajar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordenados = ordenarParaMostrar(creditos, ordenManual)
    val imputado = plan.asignaciones.associate { it.creditoId to it.importe }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.recaudacion_recuperacion_titulo),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            Fila(
                label = stringResource(R.string.recaudacion_recuperacion_retenido),
                value = formatEur(plan.recuperadoTotal.toPlainString()),
            )
            Fila(
                label = stringResource(R.string.recaudacion_recuperacion_entregado),
                value = formatEur(plan.pagadoLocal.toPlainString()),
                destacado = true,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.recaudacion_recuperacion_imputacion),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            ordenados.forEachIndexed { index, credito ->
                DeudaFila(
                    credito = credito,
                    importe = imputado[credito.id],
                    reordenable = reordenable,
                    puedeSubir = index > 0,
                    puedeBajar = index < ordenados.size - 1,
                    onSubir = { onSubir(credito.id) },
                    onBajar = { onBajar(credito.id) },
                )
            }
        }
    }
}

@Composable
private fun Fila(label: String, value: String, destacado: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = if (destacado) {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

@Composable
private fun DeudaFila(
    credito: CreditoAbierto,
    importe: BigDecimal?,
    reordenable: Boolean,
    puedeSubir: Boolean,
    puedeBajar: Boolean,
    onSubir: () -> Unit,
    onBajar: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (credito.tipo == "tolva") {
                    stringResource(R.string.recaudacion_recuperacion_tipo_tolva)
                } else {
                    stringResource(R.string.recaudacion_recuperacion_tipo_prestamo)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.recaudacion_recuperacion_saldo,
                    formatEur(credito.saldo.toPlainString()),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (importe != null) {
                "− " + formatEur(importe.toPlainString())
            } else {
                "—"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (reordenable) {
            IconButton(onClick = onSubir, enabled = puedeSubir, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.recaudacion_recuperacion_subir),
                )
            }
            IconButton(onClick = onBajar, enabled = puedeBajar, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.recaudacion_recuperacion_bajar),
                )
            }
        }
    }
}

/**
 * Orden efectivo de presentación: igual semántica que el SSOT
 * (`planificarRecuperacion`). Los ids del orden manual van primero en ese
 * orden; el resto conserva el orden base (tolva → FIFO) que ya trae la lista.
 */
private fun ordenarParaMostrar(
    creditos: List<CreditoAbierto>,
    ordenManual: List<String>?,
): List<CreditoAbierto> {
    if (ordenManual.isNullOrEmpty()) return creditos
    val porId = creditos.associateBy { it.id }
    val enManual = ordenManual.mapNotNull { porId[it] }
    val resto = creditos.filter { it.id !in ordenManual }
    return enManual + resto
}
