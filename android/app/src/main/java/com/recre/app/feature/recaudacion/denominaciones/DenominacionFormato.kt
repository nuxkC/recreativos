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

/**
 * Cara CORTA para el tile físico: billetes «5»…«50» (entero pelado), monedas
 * «1€»/«2€» y sub-euro «10c»/«20c»/«50c». Solo presentación.
 */
fun etiquetaCaraDenominacion(key: String): String {
    val v = BigDecimal(key)
    return when {
        v >= BigDecimal("5.00") -> v.toBigInteger().toString() // billete: 5,10,20,50
        v >= BigDecimal("1.00") -> "${v.toBigInteger()}€" // moneda euro: 1€,2€
        else -> "${v.movePointRight(2).toBigInteger()}c" // moneda céntimo: 10c,20c,50c
    }
}

/** true si la denominación es billete (≥5€) → cara rectangular; false = moneda (círculo). */
fun esBillete(key: String): Boolean = BigDecimal(key) >= BigDecimal("5.00")
