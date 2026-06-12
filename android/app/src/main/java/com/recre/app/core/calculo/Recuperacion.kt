package com.recre.app.core.calculo

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Espejo Kotlin del SSOT de RECUPERACIÓN de deuda (`_shared/recuperacion.ts`,
 * T-214/T-215). Permite calcular OFFLINE, durante una recaudación, cuánto se
 * retiene de la `parte_local` para amortizar las deudas del local (tolva y
 * préstamos) y cuánto se le entrega en mano (`pagado_local`).
 *
 * El servidor recalcula este mismo plan como SSOT al persistir
 * (`crear-recaudacion`), igual que [Calculo] replica `calculo.ts`. Mismas
 * entradas → mismas salidas: cliente y servidor coinciden.
 *
 * Reglas (ver design.md §5.5):
 *   1. objetivo     = round(parte_local × pct / 100, 2)
 *   2. recuperado   = min(objetivo, Σ saldos)   (nunca más de lo que se debe)
 *   3. imputación   = tolva primero, luego FIFO (deuda más antigua); el técnico
 *      puede reordenar manualmente pasando `orden` (lista de credito_id).
 *   4. pagado_local = parte_local − recuperado   (lo que se lleva el local)
 *
 * Determinista y puro: sin estado, sin reloj, sin red.
 */

/** Una deuda viva del local candidata a recuperación. */
data class CreditoAbierto(
    val id: String,
    /** `tolva` | `prestamo`. La tolva se imputa antes que los préstamos. */
    val tipo: String,
    /** Saldo vivo de la deuda. */
    val saldo: BigDecimal,
    /** Fecha ISO `YYYY-MM-DD` de la deuda (orden FIFO). */
    val fecha: String,
)

/** Importe imputado a una deuda concreta dentro del plan. */
data class AsignacionRecuperacion(
    val creditoId: String,
    val importe: BigDecimal,
)

/** Resultado del reparto: cuánto se retiene, cuánto se entrega y a qué deudas. */
data class PlanRecuperacion(
    /** Total retenido de la parte_local. */
    val recuperadoTotal: BigDecimal,
    /** Lo que se lleva el local = parte_local − recuperado_total. */
    val pagadoLocal: BigDecimal,
    /** Reparto del total entre las deudas, en el orden de imputación. */
    val asignaciones: List<AsignacionRecuperacion>,
)

private const val SCALE = 2

/**
 * Calcula el plan de recuperación para una recaudación.
 *
 * @param parteLocal              parte del local antes de recuperar.
 * @param porcentajeRecuperacion  % a retener (0..100). El caller resuelve el
 *                                COALESCE(local, empresa) antes de llamar.
 * @param creditos                deudas abiertas del local.
 * @param orden                   orden manual opcional (lista de credito_id):
 *                                los listados se imputan primero en ese orden;
 *                                el resto sigue el orden por defecto
 *                                (tolva → FIFO).
 */
fun planificarRecuperacion(
    parteLocal: BigDecimal,
    porcentajeRecuperacion: Int,
    creditos: List<CreditoAbierto>,
    orden: List<String>? = null,
): PlanRecuperacion {
    val objetivo = parteLocal
        .multiply(BigDecimal(porcentajeRecuperacion))
        .divide(BigDecimal(100), SCALE, RoundingMode.HALF_UP)
    val saldoTotal = creditos.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.saldo) }

    var restante = objetivo.min(saldoTotal)
    if (restante < BigDecimal.ZERO) restante = BigDecimal.ZERO

    val asignaciones = mutableListOf<AsignacionRecuperacion>()
    for (c in ordenarCreditos(creditos, orden)) {
        if (restante <= BigDecimal.ZERO) break
        if (c.saldo <= BigDecimal.ZERO) continue
        val imp = c.saldo.min(restante).setScale(SCALE, RoundingMode.HALF_UP)
        if (imp <= BigDecimal.ZERO) continue
        asignaciones.add(AsignacionRecuperacion(c.id, imp))
        restante = restante.subtract(imp)
    }

    val recuperado = asignaciones
        .fold(BigDecimal.ZERO) { acc, a -> acc.add(a.importe) }
        .setScale(SCALE, RoundingMode.HALF_UP)
    val pagado = parteLocal.subtract(recuperado).setScale(SCALE, RoundingMode.HALF_UP)

    return PlanRecuperacion(
        recuperadoTotal = recuperado,
        pagadoLocal = pagado,
        asignaciones = asignaciones,
    )
}

/**
 * Orden de imputación: tolva antes que préstamo; dentro de cada tipo, FIFO
 * (fecha asc, desempate por id). Si se pasa `orden`, esos ids van primero en el
 * orden indicado y el resto mantiene el orden por defecto. `sortedWith` es
 * estable, así que los no listados conservan el orden base.
 */
private fun ordenarCreditos(
    creditos: List<CreditoAbierto>,
    orden: List<String>?,
): List<CreditoAbierto> {
    val base = creditos.sortedWith(
        compareBy<CreditoAbierto> { if (it.tipo == "tolva") 0 else 1 }
            .thenBy { it.fecha }
            .thenBy { it.id },
    )
    if (orden.isNullOrEmpty()) return base

    val rank = orden.withIndex().associate { (i, id) -> id to i }
    return base.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
}
