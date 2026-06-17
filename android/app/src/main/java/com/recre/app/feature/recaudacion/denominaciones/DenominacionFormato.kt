package com.recre.app.feature.recaudacion.denominaciones

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val ES =
    DecimalFormatSymbols(Locale.forLanguageTag("es-ES")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

/**
 * Etiqueta del VALOR FACIAL de una denominación a partir de su clave money-safe
 * (`"0.10"`, `"1.00"`, `"50.00"`). Euros enteros sin decimales ("1 €", "50 €"),
 * sub-euro con coma ("0,10 €"). Sólo presentación: NO interviene en el cálculo.
 */
fun etiquetaFacialDenominacion(key: String): String {
    val valor = BigDecimal(key)
    val esEntero = valor.stripTrailingZeros().scale() <= 0
    val patron = if (esEntero) "#,##0" else "#,##0.00"
    return DecimalFormat(patron, ES).format(valor) + " €"
}
