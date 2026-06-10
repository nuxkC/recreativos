package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fila de `public.alerta` (T-12). Se consume en T-64 para listar las
 * notificaciones in-app que el técnico ha recibido — principalmente
 * resoluciones de conflictos creadas por T-26b (`resolver-conflicto`)
 * y anulaciones por T-26a (`anular-recaudacion`).
 *
 * Las alertas son globales por empresa: cualquier miembro las ve. Para
 * el técnico filtramos client-side por `referencia_id` cuando aplique
 * (recaudaciones suyas) — en este PR mostramos todas las pendientes
 * de la empresa para mantener el alcance contenido.
 */
@Serializable
data class AlertaDto(
    val id: String,
    val tipo: String,
    val mensaje: String,
    @SerialName("referencia_id")
    val referenciaId: String? = null,
    @SerialName("creada_en")
    val creadaEn: String,
    val leida: Boolean = false,
)
