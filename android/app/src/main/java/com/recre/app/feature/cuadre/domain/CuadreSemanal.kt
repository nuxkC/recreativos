package com.recre.app.feature.cuadre.domain

import java.math.BigDecimal
import java.time.LocalDate

/** Denominaciones del cuadre, de mayor a menor (mismo conjunto validado en BBDD). */
val DENOMINACIONES_CUADRE: List<BigDecimal> = listOf(
    "50", "20", "10", "5", "2", "1", "0.50", "0.20", "0.10",
).map(::BigDecimal)

/** Una fila del cuadre: lo que deberías llevar vs lo que cuentas de una denominación. */
data class LineaCuadre(
    val denominacion: BigDecimal,
    val cantidadEsperada: Long,
    val cantidadContada: Long,
) {
    val delta: Long get() = cantidadContada - cantidadEsperada
    val importeEsperado: BigDecimal get() = denominacion.multiply(BigDecimal(cantidadEsperada))
}

/** Esperado de una semana (lado servidor). */
data class CuadreSemanal(
    val semanaInicio: LocalDate,
    val numRecaudaciones: Int,
    val totalEsperado: BigDecimal,
    val esperadoPorDenominacion: Map<BigDecimal, Long>,
)

enum class VeredictoCuadre { CUADRA, SOBRA, FALTA }

/** Resultado de comparar el recuento físico contra el esperado. */
data class DiferenciaCuadre(
    val totalEsperado: BigDecimal,
    val totalContado: BigDecimal,
    val diferencia: BigDecimal,
    val veredicto: VeredictoCuadre,
    val lineas: List<LineaCuadre>,
)

/**
 * Compara el esperado (servidor) con el contado (recuento físico) sobre el
 * conjunto fijo de denominaciones. Las denominaciones ausentes en un mapa
 * cuentan como 0. El total € es la cifra autoritativa del veredicto.
 */
fun calcularDiferencia(
    esperadoPorDenominacion: Map<BigDecimal, Long>,
    contadoPorDenominacion: Map<BigDecimal, Long>,
): DiferenciaCuadre {
    val lineas = DENOMINACIONES_CUADRE.map { den ->
        LineaCuadre(
            denominacion = den,
            cantidadEsperada = esperadoPorDenominacion.entries
                .firstOrNull { it.key.compareTo(den) == 0 }?.value ?: 0L,
            cantidadContada = contadoPorDenominacion.entries
                .firstOrNull { it.key.compareTo(den) == 0 }?.value ?: 0L,
        )
    }
    val totalEsperado = lineas.fold(BigDecimal.ZERO) { acc, l ->
        acc.add(l.denominacion.multiply(BigDecimal(l.cantidadEsperada)))
    }
    val totalContado = lineas.fold(BigDecimal.ZERO) { acc, l ->
        acc.add(l.denominacion.multiply(BigDecimal(l.cantidadContada)))
    }
    val diferencia = totalContado.subtract(totalEsperado)
    val veredicto = when (diferencia.signum()) {
        0 -> VeredictoCuadre.CUADRA
        1 -> VeredictoCuadre.SOBRA
        else -> VeredictoCuadre.FALTA
    }
    return DiferenciaCuadre(totalEsperado, totalContado, diferencia, veredicto, lineas)
}
