package com.recre.app.feature.locales.components

import com.recre.app.ui.components.formatEur

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.core.data.repository.MaquinaConInstalacion
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Tarjeta de una máquina dentro del detalle del local.
 *
 * Layout:
 * - Header: número de serie + modelo + estado badge
 * - Datos: licencia · tasa semanal · % local
 * - Baseline: entradas / salidas / fecha relativa / origen
 * - Botón principal: "Recaudar" — se deshabilita si la máquina no está
 *   instalada o si el sync está stale (T-59 obliga a sincronizar antes
 *   de recaudar para no trabajar sobre baseline antigua).
 * - Botón secundario: "Cambio de placa" (T-61) — siempre disponible
 *   para máquinas instaladas, no se ve afectado por el stale flag
 *   porque la operación requiere conexión y es una alta nueva.
 */
@Composable
fun MaquinaCard(
    maquina: MaquinaConInstalacion,
    syncStale: Boolean,
    onRecaudarClick: () -> Unit,
    onCambioPlacaClick: () -> Unit,
    onReportarAveriaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = maquina.numeroSerie,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!maquina.modelo.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = listOfNotNull(maquina.fabricante, maquina.modelo)
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                EstadoMaquinaBadge(estado = maquina.estado)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            DatoLinea(
                label = stringResource(R.string.maquina_label_licencia),
                value = maquina.licenciaNumero,
            )
            DatoLinea(
                label = stringResource(R.string.maquina_label_tasa_semanal),
                value = formatEur(maquina.tasaSemanal),
            )
            DatoLinea(
                label = stringResource(R.string.maquina_label_porcentaje_local),
                value = formatPct(maquina.porcentajeLocal),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.maquina_baseline_titulo),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            DatoLinea(
                label = stringResource(R.string.maquina_baseline_entradas),
                value = maquina.baselineEntradas.toString(),
            )
            DatoLinea(
                label = stringResource(R.string.maquina_baseline_salidas),
                value = maquina.baselineSalidas.toString(),
            )
            DatoLinea(
                label = stringResource(R.string.maquina_baseline_fecha),
                value = formatRelative(maquina.baselineFecha),
            )
            DatoLinea(
                label = stringResource(R.string.maquina_baseline_origen),
                value = labelOrigen(maquina.baselineOrigen),
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRecaudarClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = maquina.estado == "instalada" && !syncStale,
            ) {
                Text(stringResource(R.string.maquina_accion_recaudar))
            }
            // Acciones secundarias. "Reportar avería" (T-222) está siempre
            // disponible: es offline-first (se encola y sube luego) y aplica a
            // cualquier estado de la máquina. "Cambio de placa" (T-61) requiere
            // máquina instalada y red (produce una baseline nueva).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReportarAveriaClick) {
                    Text(stringResource(R.string.maquina_accion_reportar_averia))
                }
                if (maquina.estado == "instalada") {
                    TextButton(onClick = onCambioPlacaClick) {
                        Text(stringResource(R.string.maquina_accion_cambio_placa))
                    }
                }
            }
        }
    }
}

@Composable
private fun DatoLinea(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun labelOrigen(origen: String): String = when (origen) {
    // Valores reales de la columna `baseline_origen` (T-12 / T-13).
    "instalacion_base" -> stringResource(R.string.maquina_baseline_origen_instalacion)
    "recaudacion_anterior" -> stringResource(R.string.maquina_baseline_origen_recaudacion)
    "cambio_placa" -> stringResource(R.string.maquina_baseline_origen_cambio_placa)
    else -> origen
}

// formatEur migrado al canónico de ui.components (money-safe, agrupación es-ES).

private fun formatPct(value: String): String {
    val decimal = runCatching { BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP) }
        .getOrElse { return value }
    val plain = decimal.toPlainString()
    val sinCeros = plain.trimEnd('0').trimEnd('.', ',')
    return "${sinCeros.ifEmpty { "0" }} %"
}

@Composable
private fun formatRelative(instant: Instant): String {
    val now = Instant.now()
    val minutes = Duration.between(instant, now).toMinutes()
    return when {
        minutes < 1 -> stringResource(R.string.relativo_ahora)
        minutes < 60 -> stringResource(R.string.relativo_minutos, minutes)
        minutes < 60 * 24 -> stringResource(R.string.relativo_horas, minutes / 60)
        minutes < 60 * 24 * 30 -> stringResource(R.string.relativo_dias, minutes / (60 * 24))
        else -> stringResource(R.string.relativo_meses, minutes / (60 * 24 * 30))
    }
}
