package com.recre.app.core.data.remote.dto

import com.recre.app.feature.cuadre.domain.CuadreSemanal
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Una fila de `v_cuadre_semanal_tecnico`: (semana, denominación) → neto llevado. */
@Serializable
data class CuadreSemanalRow(
    @SerialName("empresa_id") val empresaId: String,
    @SerialName("tecnico_id") val tecnicoId: String,
    @SerialName("semana_inicio") val semanaInicio: String,
    @Serializable(with = NumericStringSerializer::class)
    val denominacion: String,
    @SerialName("cantidad_neta") val cantidadNeta: Long,
    @SerialName("importe_neto")
    @Serializable(with = NumericStringSerializer::class)
    val importeNeto: String,
    @SerialName("num_recaudaciones") val numRecaudaciones: Int,
)

/** Mapeo puro: filas de una semana → modelo de dominio (total = Σ importe_neto). */
fun List<CuadreSemanalRow>.aCuadreSemanal(semanaInicio: LocalDate): CuadreSemanal {
    val esperado = associate { BigDecimal(it.denominacion) to it.cantidadNeta }
    val total = fold(BigDecimal.ZERO) { acc, r -> acc.add(BigDecimal(r.importeNeto)) }
    val num = firstOrNull()?.numRecaudaciones ?: 0
    return CuadreSemanal(
        semanaInicio = semanaInicio,
        numRecaudaciones = num,
        totalEsperado = total,
        esperadoPorDenominacion = esperado,
    )
}
