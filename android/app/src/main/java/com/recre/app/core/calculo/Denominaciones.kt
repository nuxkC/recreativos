package com.recre.app.core.calculo

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Denominaciones permitidas por las máquinas recreativas (en euros).
 *
 * Espejo Kotlin de `supabase/functions/_shared/constants.ts`
 * (`DENOMINACIONES_PERMITIDAS`). NO duplicar estos valores en otros sitios:
 * cualquier punto que necesite esta lista debe importar este símbolo.
 *
 * Las representamos como `BigDecimal` con escala 2 para no usar nunca
 * `Double` en cifras monetarias.
 */
val DENOMINACIONES_PERMITIDAS: List<BigDecimal> = listOf(
    "0.10", "0.20", "0.50", "1.00", "2.00", "5.00", "10.00", "20.00", "50.00",
).map { BigDecimal(it).setScale(2, RoundingMode.UNNECESSARY) }

/**
 * Item del desglose de denominaciones: tantas piezas de tantos euros.
 *
 * `denominacion` se modela como `BigDecimal` para forzar la coherencia con
 * `DENOMINACIONES_PERMITIDAS` y evitar errores con `Double`.
 */
data class DenominacionItem(
    val denominacion: BigDecimal,
    val cantidad: Int,
) {
    init {
        require(cantidad >= 0) { "La cantidad no puede ser negativa: $cantidad" }
    }

    val subtotal: BigDecimal
        get() = denominacion.multiply(BigDecimal(cantidad)).setScale(2, RoundingMode.HALF_UP)
}

/**
 * Suma el valor económico de un desglose de denominaciones.
 *
 * Espejo de `sumarDesglose` en TS. Usa `BigDecimal` con `HALF_UP` para
 * no introducir errores de coma flotante al acumular.
 */
fun sumarDesglose(desglose: List<DenominacionItem>): BigDecimal {
    var total = BigDecimal.ZERO
    for (item in desglose) {
        total = total.add(item.denominacion.multiply(BigDecimal(item.cantidad)))
    }
    return total.setScale(2, RoundingMode.HALF_UP)
}

/**
 * Compara dos importes en `BigDecimal` ignorando diferencias de escala
 * (`0` y `0.00` se consideran iguales).
 */
fun importesIguales(a: BigDecimal, b: BigDecimal): Boolean = a.compareTo(b) == 0
