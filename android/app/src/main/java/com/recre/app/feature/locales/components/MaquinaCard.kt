package com.recre.app.feature.locales.components

import com.recre.app.ui.components.formatEur

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.core.data.repository.MaquinaConInstalacion
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.OverflowAccion
import com.recre.app.ui.components.RecreDivider
import com.recre.app.ui.components.RecreGhostButton
import com.recre.app.ui.components.RecreOverflowMenu
import com.recre.app.ui.components.formatFechaHumana
import com.recre.app.ui.theme.GeistMono
import java.math.BigDecimal

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
    // Acciones SECUNDARIAS al overflow ⋮ (P3: la tarjeta lidera con estado+acción).
    // "Reportar avería" (T-222) siempre disponible: offline-first, aplica a
    // cualquier estado. "Cambio de placa" (T-61) requiere máquina instalada.
    val accionesSecundarias =
        buildList {
            add(OverflowAccion(stringResource(R.string.maquina_accion_reportar_averia), onReportarAveriaClick))
            if (maquina.estado == "instalada") {
                add(OverflowAccion(stringResource(R.string.maquina_accion_cambio_placa), onCambioPlacaClick))
            }
        }
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    // N8: el modelo es protagonista — «{modelo} · {serie}». Si no
                    // hay modelo, la serie queda como nombre principal.
                    Text(
                        text = listOfNotNull(
                            maquina.modelo?.takeIf { it.isNotBlank() },
                            maquina.numeroSerie,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!maquina.fabricante.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = maquina.fabricante,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                EstadoMaquinaBadge(estado = maquina.estado)
                RecreOverflowMenu(
                    contentDescription = stringResource(R.string.maquina_mas_acciones, maquina.numeroSerie),
                    acciones = accionesSecundarias,
                )
            }

            Spacer(Modifier.height(12.dp))
            RecreDivider()
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

            Spacer(Modifier.height(10.dp))
            // Baseline compacto en una línea mono: contadores + «últ. {fecha}».
            // Reemplaza el bloque verboso; el origen se cuelga del overflow.
            Text(
                text = stringResource(
                    R.string.maquina_baseline_compacto,
                    maquina.baselineEntradas,
                    maquina.baselineSalidas,
                    formatFechaHumana(maquina.baselineFecha, incluirHora = false),
                ),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = GeistMono,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(12.dp))
            // «Recaudar» por máquina como acción fantasma (la CTA con glow es el
            // «Recaudar todas» del pie). Mismo gating: instalada && !stale.
            RecreGhostButton(
                text = stringResource(R.string.maquina_accion_recaudar),
                onClick = onRecaudarClick,
                enabled = maquina.estado == "instalada" && !syncStale,
                fullWidth = true,
            )
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

// formatEur migrado al canónico de ui.components (money-safe, agrupación es-ES).

private fun formatPct(value: String): String {
    val decimal = runCatching { BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP) }
        .getOrElse { return value }
    val plain = decimal.toPlainString()
    val sinCeros = plain.trimEnd('0').trimEnd('.', ',')
    return "${sinCeros.ifEmpty { "0" }} %"
}
