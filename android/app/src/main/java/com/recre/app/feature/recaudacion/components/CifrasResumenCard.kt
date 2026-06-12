package com.recre.app.feature.recaudacion.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.core.calculo.Cifras
import com.recre.app.core.calculo.PlanRecuperacion

/**
 * Resumen visual de las cifras calculadas. Reutilizada por la pantalla de
 * contadores (T-54) y por la confirmación (T-56).
 *
 * Si `cifras.procede=false`, el bloque muestra un aviso destacado de
 * "lectura no recaudada" en lugar del reparto, manteniendo igualmente las
 * cifras de bruto y tasa para que el técnico vea por qué no procede.
 */
@Composable
fun CifrasResumenCard(
    cifras: Cifras,
    recuperacion: PlanRecuperacion? = null,
    modifier: Modifier = Modifier,
) {
    val esWarning = !cifras.procede
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (esWarning) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (esWarning) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (esWarning) {
                Text(
                    text = stringResource(R.string.recaudacion_no_procede_titulo),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.recaudacion_no_procede_descripcion),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
            } else {
                Text(
                    text = stringResource(R.string.recaudacion_cifras_titulo),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
            }

            FilaCifra(
                label = stringResource(R.string.recaudacion_label_creditos),
                value = cifras.creditos.toString(),
                muted = false,
            )
            val brutoLabel = if (cifras.redondeoAplicado > 0) {
                stringResource(R.string.recaudacion_label_bruto) + " " +
                    stringResource(R.string.recaudacion_label_redondeado)
            } else {
                stringResource(R.string.recaudacion_label_bruto)
            }
            FilaCifra(
                label = brutoLabel,
                value = formatEur(cifras.bruto.toPlainString()),
                muted = false,
            )
            FilaCifra(
                label = stringResource(
                    R.string.recaudacion_label_tasa_total,
                    cifras.semanas,
                ),
                value = "−${formatEur(cifras.tasaTotal.toPlainString())}",
                muted = false,
            )

            if (cifras.procede) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
                FilaCifra(
                    label = stringResource(R.string.recaudacion_label_neto),
                    value = formatEur(cifras.neto.toPlainString()),
                    muted = false,
                    bold = true,
                )
                Spacer(Modifier.height(8.dp))
                // T-225: si la recaudación repone merma de tolva (§5.6), se
                // devuelve a la máquina ANTES del reparto; local y empresa
                // reparten sobre la base, no sobre el neto.
                if (cifras.reposicionTolva.signum() > 0) {
                    FilaCifra(
                        label = stringResource(R.string.recaudacion_label_repuesto_tolva),
                        value = "− " + formatEur(cifras.reposicionTolva.toPlainString()),
                        muted = false,
                    )
                    FilaCifra(
                        label = stringResource(R.string.recaudacion_label_base_reparto),
                        value = formatEur(cifras.baseReparto.toPlainString()),
                        muted = false,
                        bold = true,
                    )
                }
                FilaCifra(
                    label = stringResource(
                        R.string.recaudacion_label_parte_local,
                        formatPct(cifras.porcentajeLocal.toPlainString()),
                    ),
                    value = formatEur(cifras.parteLocal.toPlainString()),
                    muted = false,
                )
                // T-217: si esta recaudación amortiza deuda del local, mostramos
                // cuánto se retiene y cuánto se le entrega (pagado_local).
                if (recuperacion != null && recuperacion.recuperadoTotal.signum() > 0) {
                    FilaCifra(
                        label = stringResource(R.string.recaudacion_recuperacion_retenido),
                        value = "− " + formatEur(recuperacion.recuperadoTotal.toPlainString()),
                        muted = false,
                    )
                    FilaCifra(
                        label = stringResource(R.string.recaudacion_recuperacion_entregado),
                        value = formatEur(recuperacion.pagadoLocal.toPlainString()),
                        muted = false,
                        bold = true,
                    )
                }
                FilaCifra(
                    label = stringResource(R.string.recaudacion_label_parte_empresa),
                    value = formatEur(cifras.parteEmpresa.toPlainString()),
                    muted = false,
                )
            }
        }
    }
}

@Composable
private fun FilaCifra(
    label: String,
    value: String,
    muted: Boolean,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (bold) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (muted) Color.Unspecified else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (muted) 0.7f else 1f),
        )
        Text(
            text = value,
            style = if (bold) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

internal fun formatEur(value: String): String {
    val decimal = runCatching {
        java.math.BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP)
    }.getOrElse { return value }
    return "${decimal.toPlainString().replace('.', ',')} €"
}

internal fun formatPct(value: String): String {
    val decimal = runCatching {
        java.math.BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP)
    }.getOrElse { return value }
    val plain = decimal.toPlainString()
    val sinCeros = plain.trimEnd('0').trimEnd('.', ',')
    return "${sinCeros.ifEmpty { "0" }} %"
}
