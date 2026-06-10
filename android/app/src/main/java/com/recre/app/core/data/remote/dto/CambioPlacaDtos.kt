package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload que `crear-cambio-placa` espera (espejo de
 * `_shared/schemas.ts:CrearCambioPlacaInputSchema`).
 *
 * Contadores con `Long` (los datos reales no se acercan a `Long.MAX_VALUE`).
 * Fecha como ISO `YYYY-MM-DD` (z.string().date() en el server).
 */
@Serializable
data class CrearCambioPlacaRequest(
    @SerialName("instalacion_id")
    val instalacionId: String,
    val fecha: String,
    @SerialName("contador_entradas_nuevo")
    val contadorEntradasNuevo: Long = 0,
    @SerialName("contador_salidas_nuevo")
    val contadorSalidasNuevo: Long = 0,
    val motivo: String? = null,
    @SerialName("numero_serie_placa_anterior")
    val numeroSeriePlacaAnterior: String? = null,
    @SerialName("numero_serie_placa_nueva")
    val numeroSeriePlacaNueva: String? = null,
    @SerialName("foto_base64")
    val fotoBase64: String? = null,
    val notas: String? = null,
)

/**
 * Respuesta de `crear-cambio-placa`. El cliente solo necesita el id
 * para pintar feedback; el resto se actualizará vía sync inicial cuando
 * el `SyncManager` traiga la nueva baseline.
 */
@Serializable
data class CrearCambioPlacaResponse(
    val id: String,
    @SerialName("instalacion_id")
    val instalacionId: String,
)
