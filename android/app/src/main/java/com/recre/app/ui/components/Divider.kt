package com.recre.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recre.app.ui.theme.RecreColors
import com.recre.app.ui.theme.RecreTheme
import com.recre.app.ui.theme.RecreType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// =====================================================================
// Atom C-DIVIDER-SEP — Divider / SubtotalSeparator (Compose · Material3).
// SSOT spec: .kiro/specs/recre/fase3-component-specs.md (linea 7400).
//
// Tres usos:
//  (1) RecreDivider        — regla plana 1dp edge-to-edge entre secciones/filas.
//  (2) RecreVerticalDivider — regla vertical 1dp (KPIs lado a lado).
//  (3) RecreLabeledDivider — regla con label centrado; con `amount` no nulo
//      se renderiza el subtotal "── subtotal billetes 1.000,00 € ──".
//
// El divisor es ESTRUCTURA PURA: color SIEMPRE = border (= outlineVariant),
// nunca un rol semantico (success/danger/warning/info) ni la marca secondary.
// La cifra del subtotal es dinero NEUTRO foreground (onSurface), Geist Mono
// tabular — el separador NO la tine de success.
//
// MONEY-SAFE: el importe entra como BigDecimal (jamas Double/Float). El formato
// es-ES "1.234,56 €" se deriva de setScale(2, HALF_UP) y el simbolo € va en
// color muted, los digitos en foreground, vIa AnnotatedString.
// =====================================================================

/**
 * Regla horizontal plana de 1dp. Color [color] = border (outlineVariant) por
 * defecto; no recibe rol semantico. Va edge-to-edge: el margen lo aporta el
 * contenedor, no el divisor.
 */
@Composable
fun RecreDivider(
    modifier: Modifier = Modifier,
    color: Color = RecreColors.current.border, // border = outlineVariant; NO rol semantico
) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = color)
}

/**
 * Regla horizontal PUNTEADA de 1dp — el corte "perforado" de un ticket/recibo
 * (estetica del detalle de historico, rediseño F0). Mismo rol que [RecreDivider]:
 * ESTRUCTURA PURA, color = border (nunca rol semantico). Decorativa: se excluye de
 * a11y. El guion/hueco van en px del propio trazo para que escale con la densidad.
 */
@Composable
fun RecreDottedDivider(
    modifier: Modifier = Modifier,
    color: Color = RecreColors.current.border,
) {
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .clearAndSetSemantics {}, // decorativa
    ) {
        val dash = 3.dp.toPx()
        val gap = 3.dp.toPx()
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), 0f),
        )
    }
}

/**
 * Regla vertical de 1dp (p. ej. entre KPIs lado a lado). Toma el alto del
 * contenedor. Color = border.
 */
@Composable
fun RecreVerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = RecreColors.current.border,
) {
    VerticalDivider(
        modifier = modifier.fillMaxHeight(),
        thickness = 1.dp,
        color = color,
    )
}

/**
 * Regla con [label] centrado entre dos reglas simetricas (weight 1f cada una).
 * Dos formas:
 *  - [amount] == null  → divisor con etiqueta neutra ("o introducir a mano").
 *  - [amount] != null  → subtotal: etiqueta muted + cifra neutra foreground
 *    (Geist Mono tabular) formateada money-safe desde [BigDecimal].
 *
 * El [label] es texto de UI: lo aporta el llamador (stringResource), no se
 * hardcodea aqui. Las reglas son decorativas (se excluyen de a11y); solo se
 * anuncia el texto, como un unico nodo (mergeDescendants).
 *
 * @param label etiqueta de la fila (neutra o descriptor del subtotal).
 * @param amount importe del subtotal money-safe; null = divisor con etiqueta.
 */
@Composable
fun RecreLabeledDivider(
    label: String,
    amount: BigDecimal? = null,
    modifier: Modifier = Modifier,
) {
    val colors = RecreColors.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}, // se anuncia el texto, no las reglas
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp), // label no toca las reglas
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f).clearAndSetSemantics {}, // decorativa
            thickness = 1.dp,
            color = colors.border,
        )
        if (amount == null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium, // caption 13sp/500
                color = colors.muted, // texto secundario, AA
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
                Spacer(Modifier.width(6.dp))
                // Subtotal money-safe vía el átomo MoneyText (rol NEUTRO): dígitos
                // foreground, € muted. Reutiliza el átomo, no reimplementa el formateo.
                MoneyText(amount = amount, size = MoneyTextSize.Inline)
            }
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
            thickness = 1.dp,
            color = colors.border,
        )
    }
}

// Sin motion propio: el separador es estructura. El recalculo del subtotal lo
// anima quien posea el dato (count-up), no este atom.

/** Simbolos es-ES: separador de miles '.', decimal ',' (formato "1.234,56"). */
private val esEsSymbols =
    DecimalFormatSymbols(Locale.forLanguageTag("es-ES")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

/**
 * Formatea un [amount] money-safe a "1.234,56 €" con los digitos en
 * [digitsColor] y el simbolo € (+ espacio) en [symbolColor], vIa
 * [AnnotatedString]. Half-up al centimo desde [BigDecimal]; NUNCA Double.
 */
private fun formatEurAnnotated(
    amount: BigDecimal,
    digitsColor: Color,
    symbolColor: Color,
): AnnotatedString {
    val scaled = amount.setScale(2, RoundingMode.HALF_UP)
    val formatter =
        DecimalFormat("#,##0.00", esEsSymbols).apply {
            isGroupingUsed = true
        }
    val digits = formatter.format(scaled) // opera sobre BigDecimal, sin perder precision
    return buildAnnotatedString {
        withStyle(SpanStyle(color = digitsColor)) { append(digits) }
        withStyle(SpanStyle(color = symbolColor)) { append(" €") }
    }
}

// ---------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------

@Preview(name = "Divider · light", showBackground = true)
@Composable
private fun PreviewDividerLight() {
    RecreTheme(darkTheme = false) {
        DividerPreviewContent()
    }
}

@Preview(name = "Divider · dark", showBackground = true)
@Composable
private fun PreviewDividerDark() {
    RecreTheme(darkTheme = true) {
        DividerPreviewContent()
    }
}

@Composable
private fun DividerPreviewContent() {
    androidx.compose.foundation.layout.Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RecreDivider()
        RecreDottedDivider()
        RecreLabeledDivider(label = "o introducir a mano")
        RecreLabeledDivider(
            label = "subtotal billetes",
            amount = BigDecimal("1000.00"),
        )
        RecreLabeledDivider(
            label = "subtotal monedas",
            amount = BigDecimal("1234.5"),
        )
    }
}
