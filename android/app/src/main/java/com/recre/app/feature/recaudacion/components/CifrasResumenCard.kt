package com.recre.app.feature.recaudacion.components

import com.recre.app.ui.components.formatEur

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.core.calculo.Cifras
import com.recre.app.core.calculo.PlanRecuperacion
import com.recre.app.ui.components.Banda
import com.recre.app.ui.components.BandaTono
import com.recre.app.ui.components.FilaHairline
import com.recre.app.ui.theme.RecreColors

/**
 * Resumen visual de las cifras calculadas (T-56, único caller: confirmación).
 *
 * Neón N7: el desglose deja de ser una card `primaryContainer` y pasa a una
 * columna desnuda de [FilaHairline] sobre el fondo, con semántica de color en la
 * cifra (restas en `warningText`, hitos en `emphasis`, parte empresa en
 * `accentBright`). Si `cifras.procede=false`, el reparto se sustituye por un
 * aviso destacado de "lectura no recaudada" (Banda deuda), conservando la señal
 * de que no procede sin recalcular nada aquí.
 */
@Composable
fun CifrasResumenCard(
    cifras: Cifras,
    recuperacion: PlanRecuperacion? = null,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current

    if (!cifras.procede) {
        // Aviso destacado (no card cian): el bruto no cubre la tasa semanal.
        Column(modifier = modifier.fillMaxWidth()) {
            Banda(
                texto = AnnotatedString(
                    stringResource(R.string.recaudacion_no_procede_titulo) + ". " +
                        stringResource(R.string.recaudacion_no_procede_descripcion),
                ),
                icon = Icons.Filled.Info,
                tono = BandaTono.DEUDA,
            )
            Spacer(Modifier.height(12.dp))
            // Aunque no procede, el técnico ve por qué: créditos, bruto y tasa.
            FilaHairline(
                label = stringResource(R.string.recaudacion_label_creditos),
                value = cifras.creditos.toString(),
            )
            FilaHairline(
                label = brutoLabel(cifras),
                value = formatEur(cifras.bruto.toPlainString()),
            )
            FilaHairline(
                label = stringResource(R.string.recaudacion_label_tasa_total, cifras.semanas),
                value = "−${formatEur(cifras.tasaTotal.toPlainString())}",
                valueColor = colors.warningText,
                hairline = false,
            )
        }
        return
    }

    // Rama que procede: reparto completo. La última fila cierra sin hairline.
    val reponeTolva = cifras.reposicionTolva.signum() > 0
    val hayRecuperacion = recuperacion != null && recuperacion.recuperadoTotal.signum() > 0

    Column(modifier = modifier.fillMaxWidth()) {
        FilaHairline(
            label = stringResource(R.string.recaudacion_label_creditos),
            value = cifras.creditos.toString(),
        )
        FilaHairline(
            label = brutoLabel(cifras),
            value = formatEur(cifras.bruto.toPlainString()),
        )
        FilaHairline(
            label = stringResource(R.string.recaudacion_label_tasa_total, cifras.semanas),
            value = "−${formatEur(cifras.tasaTotal.toPlainString())}",
            valueColor = colors.warningText,
        )
        FilaHairline(
            label = stringResource(R.string.recaudacion_label_neto),
            value = formatEur(cifras.neto.toPlainString()),
            emphasis = true,
        )
        // T-225: la reposición de merma de tolva se devuelve a la máquina ANTES
        // del reparto; local y empresa reparten sobre la base, no sobre el neto.
        if (reponeTolva) {
            FilaHairline(
                label = stringResource(R.string.recaudacion_label_repuesto_tolva),
                value = "−" + formatEur(cifras.reposicionTolva.toPlainString()),
                valueColor = colors.warningText,
            )
            FilaHairline(
                label = stringResource(R.string.recaudacion_label_base_reparto),
                value = formatEur(cifras.baseReparto.toPlainString()),
                emphasis = true,
            )
        }
        FilaHairline(
            label = stringResource(
                R.string.recaudacion_label_parte_local,
                formatPct(cifras.porcentajeLocal.toPlainString()),
            ),
            value = formatEur(cifras.parteLocal.toPlainString()),
        )
        // T-217: si esta recaudación amortiza deuda, mostramos lo retenido y lo
        // entregado (pagado_local) antes de la parte de la empresa.
        if (hayRecuperacion) {
            FilaHairline(
                label = stringResource(R.string.recaudacion_recuperacion_retenido),
                value = "−" + formatEur(recuperacion.recuperadoTotal.toPlainString()),
                valueColor = colors.warningText,
            )
            FilaHairline(
                label = stringResource(R.string.recaudacion_recuperacion_entregado),
                value = formatEur(recuperacion.pagadoLocal.toPlainString()),
                emphasis = true,
            )
        }
        FilaHairline(
            label = stringResource(R.string.recaudacion_label_parte_empresa),
            value = formatEur(cifras.parteEmpresa.toPlainString()),
            valueColor = colors.accentBright,
            hairline = false,
        )
    }
}

/** Label del bruto con sufijo "(redondeado)" cuando se aplicó redondeo al alza. */
@Composable
private fun brutoLabel(cifras: Cifras): String =
    if (cifras.redondeoAplicado > 0) {
        stringResource(R.string.recaudacion_label_bruto) + " " +
            stringResource(R.string.recaudacion_label_redondeado)
    } else {
        stringResource(R.string.recaudacion_label_bruto)
    }

// formatEur migrado al canónico de ui.components (money-safe, agrupación es-ES);
// lo consumen también RecuperacionResumenCard y DenominacionesScreen vía import.

internal fun formatPct(value: String): String {
    val decimal = runCatching {
        java.math.BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP)
    }.getOrElse { return value }
    val plain = decimal.toPlainString()
    val sinCeros = plain.trimEnd('0').trimEnd('.', ',')
    return "${sinCeros.ifEmpty { "0" }} %"
}
