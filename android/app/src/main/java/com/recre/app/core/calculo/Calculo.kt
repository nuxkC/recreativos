package com.recre.app.core.calculo

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Single Source of Truth Kotlin del cálculo de recaudación.
 *
 * Espejo bit-a-bit de `supabase/functions/_shared/calculo.ts`:
 *
 *   1. créditos_netos = (Δentradas) − (Δsalidas)
 *   2. bruto = créditos × valor_credito  → setScale(2, HALF_UP)
 *   3. tasa_total = semanas × tasa_semanal  → setScale(2, HALF_UP)
 *   4. si bruto < tasa_total → procede=false (no se recauda)
 *   5. neto = bruto − tasa_total  (sin redondear; ya en céntimos)
 *   6. parte_local = neto × % / 100  → setScale(2, HALF_UP)
 *   7. parte_empresa = neto − parte_local  (la empresa absorbe el redondeo)
 *
 * El servidor recalcula todo igualmente al persistir (T-21) — esta
 * implementación se usa para:
 *   - Preview en vivo cuando el técnico introduce los contadores (T-54).
 *   - Validar suma de denominaciones contra `bruto` y `parte_local` (T-55).
 *   - Funcionar offline.
 *
 * Si los redondeos no coinciden bit-a-bit con el servidor, la fila quedará
 * marcada como conflicto en la base de datos (T-21 detecta deltas) y la
 * web ofrecerá la resolución manual (T-37).
 */
fun calcularRecaudacion(input: CalcularInput): Cifras {
    val valorCredito = input.valorCredito.setScale(SCALE, RoundingMode.HALF_UP)
    val tasaSemanal = input.tasaSemanal.setScale(SCALE, RoundingMode.HALF_UP)
    val porcentajeLocal = input.porcentajeLocal.setScale(SCALE, RoundingMode.HALF_UP)

    val deltaEntradas = input.contadorEntradasActual - input.baselineEntradas
    val deltaSalidas = input.contadorSalidasActual - input.baselineSalidas
    val creditos = deltaEntradas - deltaSalidas

    val bruto = valorCredito.multiply(BigDecimal(creditos))
        .setScale(SCALE, RoundingMode.HALF_UP)
    val tasaTotal = tasaSemanal.multiply(BigDecimal(input.semanas))
        .setScale(SCALE, RoundingMode.HALF_UP)

    if (bruto < tasaTotal) {
        return Cifras(
            procede = false,
            bruto = bruto,
            semanas = input.semanas,
            tasaSemanal = tasaSemanal,
            tasaTotal = tasaTotal,
            neto = ZERO,
            porcentajeLocal = porcentajeLocal,
            parteLocal = ZERO,
            parteEmpresa = ZERO,
            valorCredito = valorCredito,
            baselineEntradas = input.baselineEntradas,
            baselineSalidas = input.baselineSalidas,
            deltaEntradas = deltaEntradas,
            deltaSalidas = deltaSalidas,
            creditos = creditos,
        )
    }

    val neto = bruto.subtract(tasaTotal) // ya en céntimos
    val parteLocal = neto.multiply(porcentajeLocal)
        .divide(BigDecimal(100), SCALE, RoundingMode.HALF_UP)
    val parteEmpresa = neto.subtract(parteLocal) // absorbe el centavo

    return Cifras(
        procede = true,
        bruto = bruto,
        semanas = input.semanas,
        tasaSemanal = tasaSemanal,
        tasaTotal = tasaTotal,
        neto = neto,
        porcentajeLocal = porcentajeLocal,
        parteLocal = parteLocal,
        parteEmpresa = parteEmpresa,
        valorCredito = valorCredito,
        baselineEntradas = input.baselineEntradas,
        baselineSalidas = input.baselineSalidas,
        deltaEntradas = deltaEntradas,
        deltaSalidas = deltaSalidas,
        creditos = creditos,
    )
}

/** Entrada del cálculo. Mantiene la baseline + contadores actuales + parámetros de la instalación. */
data class CalcularInput(
    val baselineEntradas: Long,
    val baselineSalidas: Long,
    val contadorEntradasActual: Long,
    val contadorSalidasActual: Long,
    val valorCredito: BigDecimal,
    val tasaSemanal: BigDecimal,
    val porcentajeLocal: BigDecimal,
    val semanas: Int,
)

private const val SCALE = 2
private val ZERO: BigDecimal = BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY)
