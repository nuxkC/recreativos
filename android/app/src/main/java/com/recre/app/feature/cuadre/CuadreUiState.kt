package com.recre.app.feature.cuadre

import com.recre.app.feature.cuadre.domain.CuadreSemanal
import com.recre.app.feature.cuadre.domain.DiferenciaCuadre
import com.recre.app.feature.cuadre.domain.calcularDiferencia
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Estado de la pantalla de cuadre semanal.
 *
 * El cuadre compara el "esperado" del servidor (recaudaciones de la semana) con
 * el recuento físico que teclea el técnico, pero solo tiene sentido si la cola
 * de subida está vacía: con recaudaciones sin subir el esperado estaría
 * incompleto, así que ese caso bloquea ([BloqueadoPorPendientes]) en vez de
 * mostrar una diferencia falsa.
 */
sealed interface CuadreUiState {

    /** Cargando el esperado por primera vez. */
    data object Cargando : CuadreUiState

    /** No se pudo cargar el esperado (sin red) y aún no hay dato previo. */
    data object SinConexion : CuadreUiState

    /**
     * Hay recaudaciones sin subir: el cuadre no es fiable hasta drenar la cola.
     *
     * @param reintentables filas que la cola aún reintentará sola.
     * @param fallidas filas terminales ('fallida') que no reintenta; requieren
     *   acción del técnico.
     */
    data class BloqueadoPorPendientes(
        val reintentables: Int,
        val fallidas: Int,
    ) : CuadreUiState

    /** La semana no tiene recaudaciones: nada que cuadrar. */
    data class Vacio(val semana: LocalDate) : CuadreUiState

    /** Cuadre listo: esperado cargado y comparado con el recuento físico. */
    data class Listo(
        val semana: LocalDate,
        val numRecaudaciones: Int,
        val diferencia: DiferenciaCuadre,
    ) : CuadreUiState
}

/**
 * Decide el estado de pantalla a partir de las cuatro fuentes del cuadre.
 *
 * Función pura (sin Hilt, Room ni red) para ser testeable directamente; el
 * ViewModel se limita a alimentarla con los valores ya observados. El orden de
 * las reglas es la prioridad:
 *  1. Cola con pendientes → bloquea (el esperado no es fiable todavía).
 *  2. Esperado aún no cargado → [CuadreUiState.Cargando] (`null` por carga en
 *     curso, que el VM resuelve a [CuadreUiState.SinConexion] si la carga falla).
 *  3. Semana sin recaudaciones → vacía.
 *  4. En otro caso → listo, con la diferencia recuento vs esperado.
 */
fun construirEstado(
    cuadre: CuadreSemanal?,
    contado: Map<BigDecimal, Long>,
    pendientes: Int,
    fallidas: Int,
    semana: LocalDate,
): CuadreUiState =
    when {
        pendientes > 0 -> CuadreUiState.BloqueadoPorPendientes(
            reintentables = pendientes - fallidas,
            fallidas = fallidas,
        )
        cuadre == null -> CuadreUiState.Cargando
        cuadre.numRecaudaciones == 0 -> CuadreUiState.Vacio(semana)
        else -> CuadreUiState.Listo(
            semana = semana,
            numRecaudaciones = cuadre.numRecaudaciones,
            diferencia = calcularDiferencia(cuadre.esperadoPorDenominacion, contado),
        )
    }
