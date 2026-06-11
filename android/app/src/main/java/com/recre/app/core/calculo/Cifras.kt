package com.recre.app.core.calculo

import java.math.BigDecimal

/**
 * Resultado del cálculo de recaudación.
 *
 * Espejo de `CalculoRecaudacionResult` en TS, pero con `BigDecimal` en
 * lugar de `string`. La conversión a String solo se hace en la frontera
 * HTTP (cuando enviamos la recaudación al backend).
 *
 * Si `procede = false` (bruto < tasa_total), `neto`, `parteLocal` y
 * `parteEmpresa` valen `0.00` por convención: el flujo de UI debe
 * mostrar el aviso de "lectura no recaudada" en vez de pedir
 * denominaciones.
 */
data class Cifras(
    val procede: Boolean,
    val bruto: BigDecimal,
    val semanas: Int,
    val tasaSemanal: BigDecimal,
    val tasaTotal: BigDecimal,
    val neto: BigDecimal,
    val porcentajeLocal: BigDecimal,
    val parteLocal: BigDecimal,
    val parteEmpresa: BigDecimal,
    val valorCredito: BigDecimal,
    val baselineEntradas: Long,
    val baselineSalidas: Long,
    val deltaEntradas: Long,
    val deltaSalidas: Long,
    val creditos: Long,
    /**
     * Unidad de redondeo aplicada al bruto (0 = no se redondeó). Cuando es > 0,
     * [bruto] es el bruto redondeado (el desglose debe cuadrar con él) y
     * [brutoReal] conserva el valor antes de redondear.
     */
    val redondeoAplicado: Int = 0,
    val brutoReal: BigDecimal = bruto,
)
