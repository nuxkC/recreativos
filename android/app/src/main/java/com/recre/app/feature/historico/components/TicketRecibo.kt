package com.recre.app.feature.historico.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.R
import com.recre.app.core.data.repository.EstadoHistorico
import com.recre.app.core.data.repository.RecaudacionHistorica
import com.recre.app.ui.components.AppCard
import com.recre.app.ui.components.MoneyText
import com.recre.app.ui.components.MoneyTextSize
import com.recre.app.ui.components.RecreDottedDivider
import com.recre.app.ui.theme.RecrePapelTicket
import com.recre.app.ui.theme.RecrePapelTinta
import com.recre.app.ui.theme.RecreShapes
import com.recre.app.ui.theme.RecreTheme
import com.recre.app.ui.theme.RecreType
import java.math.BigDecimal
import java.time.Instant

// =====================================================================
// TicketRecibo · estética de "recibo térmico" para el detalle de histórico (F2a).
// SSOT: docs/superpowers/specs/2026-06-17-rediseno-ui-android-design.md §6.2.
//
// Emula el papel del ticket impreso: cabecera centrada, secciones separadas por
// líneas PUNTEADAS (RecreDottedDivider) y cifras en Geist Mono tabular (MoneyText,
// money-safe desde BigDecimal). Reproduce SOLO los datos que expone
// RecaudacionHistorica (fecha, local/máquina/licencia, bruto/neto/partes, estado);
// no inventa contadores ni firma (no están en el modelo del histórico). El estado
// anulada/conflicto se comunica con icono+texto+color (nunca solo color, P8).
// =====================================================================

/**
 * @param recaudacion datos del histórico a pintar como recibo.
 * @param fechaTexto fecha ya formateada por el llamador (reusa el formateador del
 *   detalle para no duplicar la TZ de la empresa).
 */
@Composable
fun TicketRecibo(
    recaudacion: RecaudacionHistorica,
    fechaTexto: String,
    modifier: Modifier = Modifier,
) {
    PapelDelTicket(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cabecera centrada (la "cabecera del papel").
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.historico_ticket_titulo).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = fechaTexto,
                    style = RecreType.cifraCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(12.dp))
            RecreDottedDivider()
            Spacer(Modifier.height(12.dp))

            FilaDato(stringResource(R.string.historico_detalle_local), recaudacion.localNombre)
            FilaDato(
                label = stringResource(R.string.historico_detalle_maquina),
                value = listOfNotNull(recaudacion.maquinaSerie, recaudacion.maquinaModelo).joinToString(" · "),
            )
            recaudacion.licenciaNumero?.let {
                FilaDato(stringResource(R.string.historico_detalle_licencia), it)
            }

            if (recaudacion.estado == EstadoHistorico.Anulada) {
                Spacer(Modifier.height(12.dp))
                EstadoBanner(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titulo = stringResource(R.string.historico_detalle_anulada_titulo),
                    detalle = recaudacion.motivoAnulacion?.ifBlank { null }
                        ?: stringResource(R.string.historico_detalle_anulada_sin_motivo),
                    conIcono = false,
                )
            }
            if (recaudacion.conflictoPendiente) {
                Spacer(Modifier.height(12.dp))
                EstadoBanner(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    titulo = stringResource(R.string.historico_detalle_conflicto_titulo),
                    detalle = stringResource(R.string.historico_detalle_conflicto_descripcion),
                    conIcono = true,
                )
            }

            Spacer(Modifier.height(12.dp))
            RecreDottedDivider()
            Spacer(Modifier.height(12.dp))

            // Cifras en mono tabular (MoneyText). Neto destacado (es el dato héroe).
            FilaCifra(stringResource(R.string.historico_label_bruto), recaudacion.bruto)
            FilaCifra(stringResource(R.string.historico_label_neto), recaudacion.neto, destacado = true)
            Spacer(Modifier.height(6.dp))
            RecreDottedDivider()
            Spacer(Modifier.height(6.dp))
            FilaCifra(stringResource(R.string.historico_label_parte_local), recaudacion.parteLocal)
            FilaCifra(stringResource(R.string.historico_label_parte_empresa), recaudacion.parteEmpresa)
        }
    }
}

/**
 * Envuelve el contenido del recibo en PAPEL FIJO. Fuerza el tema claro (para que
 * TODO lo que lee M3 —incl. MoneyText, cuyo dígito resuelve a onSurface— siga
 * siendo legible sobre el papel también en modo oscuro) y re-tinta surface→papel /
 * onSurface→tinta con los dos tokens fijos del recibo. Así el ticket es papel claro
 * sobre la sala oscura en ambos modos, sin tocar los átomos que pinta dentro.
 */
@Composable
private fun PapelDelTicket(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    RecreTheme(darkTheme = false) {
        val esquemaPapel =
            MaterialTheme.colorScheme.copy(
                surface = RecrePapelTicket,
                onSurface = RecrePapelTinta,
            )
        MaterialTheme(colorScheme = esquemaPapel) {
            AppCard(modifier = modifier, content = content)
        }
    }
}

@Composable
private fun FilaDato(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FilaCifra(label: String, value: BigDecimal, destacado: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (destacado) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MoneyText(
            amount = value,
            size = if (destacado) MoneyTextSize.Medium else MoneyTextSize.Inline,
        )
    }
}

@Composable
private fun EstadoBanner(
    color: Color,
    contentColor: Color,
    titulo: String,
    detalle: String,
    conIcono: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        contentColor = contentColor,
        shape = RecreShapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (conIcono) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(text = detalle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------------------------------------------------------------
// Preview (light / dark). Datos de muestra; fecha fija (sin now()).
// ---------------------------------------------------------------------

private val MUESTRA =
    RecaudacionHistorica(
        id = "1",
        fecha = Instant.parse("2026-06-17T10:30:00Z"),
        estado = EstadoHistorico.Firme,
        conflictoPendiente = false,
        localId = "local-1",
        maquinaId = "maquina-1",
        localNombre = "Bar Manolo",
        maquinaSerie = "0042-AB",
        maquinaModelo = "Cirsa Unidesa",
        licenciaNumero = "LIC-128",
        bruto = BigDecimal("1234.56"),
        neto = BigDecimal("900.00"),
        parteLocal = BigDecimal("450.00"),
        parteEmpresa = BigDecimal("450.00"),
        tieneTicketPdf = true,
        motivoAnulacion = null,
    )

@Preview(name = "TicketRecibo · light", showBackground = true)
@Composable
private fun TicketReciboLightPreview() {
    RecreTheme(darkTheme = false) {
        TicketRecibo(recaudacion = MUESTRA, fechaTexto = "17 jun 2026, 10:30", modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "TicketRecibo · dark", showBackground = true)
@Composable
private fun TicketReciboDarkPreview() {
    RecreTheme(darkTheme = true) {
        TicketRecibo(recaudacion = MUESTRA, fechaTexto = "17 jun 2026, 10:30", modifier = Modifier.padding(16.dp))
    }
}
