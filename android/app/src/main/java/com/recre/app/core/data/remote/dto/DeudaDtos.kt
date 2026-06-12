package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs de la ficha de deudas del local (T-215).
 *
 * - Lectura del libro mayor: [RecuperacionRowDto] (tabla `recuperacion`, RLS
 *   solo-lectura para `authenticated`).
 * - Escritura vía RPCs `SECURITY DEFINER` (la escritura directa está revocada):
 *   los nombres serializados son los argumentos SQL `p_*`. `numeric` viaja como
 *   número vía [NumericStringSerializer]; `date` como `YYYY-MM-DD`.
 */

/**
 * Fila del libro mayor de abonos. Se mapean todas las columnas para que el
 * decode no dependa de `ignoreUnknownKeys`.
 */
@Serializable
data class RecuperacionRowDto(
    val id: String,
    @SerialName("empresa_id")
    val empresaId: String,
    @SerialName("local_id")
    val localId: String,
    @SerialName("credito_id")
    val creditoId: String,
    val origen: String,
    @Serializable(with = NumericStringSerializer::class)
    val importe: String,
    @SerialName("recaudacion_id")
    val recaudacionId: String? = null,
    val fecha: String,
    @SerialName("usuario_id")
    val usuarioId: String? = null,
    val notas: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
)

/** Parámetros de `crear_prestamo`. tipo_interes y fecha usan los DEFAULT SQL. */
@Serializable
data class CrearPrestamoParams(
    @SerialName("p_empresa_id")
    val empresaId: String,
    @SerialName("p_local_id")
    val localId: String,
    @SerialName("p_principal")
    @Serializable(with = NumericStringSerializer::class)
    val principal: String,
    @SerialName("p_notas")
    val notas: String?,
)

/** Parámetros de `registrar_recuperacion_efectivo` (abono manual). */
@Serializable
data class RegistrarRecuperacionEfectivoParams(
    @SerialName("p_credito_id")
    val creditoId: String,
    @SerialName("p_importe")
    @Serializable(with = NumericStringSerializer::class)
    val importe: String,
    @SerialName("p_notas")
    val notas: String?,
)

/** Parámetros de `condonar_credito` (acción de admin). */
@Serializable
data class CondonarCreditoParams(
    @SerialName("p_credito_id")
    val creditoId: String,
    @SerialName("p_notas")
    val notas: String?,
)

/** Parámetros de `set_porcentaje_recuperacion_local`. `null` = heredar empresa. */
@Serializable
data class SetPorcentajeRecuperacionLocalParams(
    @SerialName("p_local_id")
    val localId: String,
    @SerialName("p_porcentaje")
    val porcentaje: Int?,
)
