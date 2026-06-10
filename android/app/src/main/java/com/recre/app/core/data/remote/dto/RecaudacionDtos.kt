package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload que `crear-recaudacion` espera (espejo de
 * `_shared/schemas.ts:CrearRecaudacionInputSchema`).
 *
 * Convenciones idénticas al servidor:
 * - `denominacion` y `cantidad` como `Double`/`Int` (PostgREST + Edge
 *   Function los aceptan así; en cliente los construimos desde
 *   `BigDecimal.toDouble()` solo a la hora de serializar — el cálculo
 *   monetario nunca pasa por `Double`).
 * - `firma_base64` y `foto_*_base64` sin el prefijo `data:image/png;base64,`.
 * - `baseline_origen` ∈ {`recaudacion_anterior`, `cambio_placa`,
 *   `instalacion_base`}.
 */
@Serializable
data class CrearRecaudacionRequest(
    @SerialName("instalacion_id")
    val instalacionId: String,
    val fecha: String,
    @SerialName("contador_entradas_actual")
    val contadorEntradasActual: Long,
    @SerialName("contador_salidas_actual")
    val contadorSalidasActual: Long,
    @SerialName("desglose_total")
    val desgloseTotal: List<DenominacionItemDto>,
    @SerialName("desglose_local")
    val desgloseLocal: List<DenominacionItemDto>,
    @SerialName("firma_base64")
    val firmaBase64: String,
    @SerialName("foto_entradas_base64")
    val fotoEntradasBase64: String? = null,
    @SerialName("foto_salidas_base64")
    val fotoSalidasBase64: String? = null,
    @SerialName("ocr_entradas_valor")
    val ocrEntradasValor: Long? = null,
    @SerialName("ocr_salidas_valor")
    val ocrSalidasValor: Long? = null,
    val observaciones: String? = null,
    @SerialName("dispositivo_id")
    val dispositivoId: String? = null,
    @SerialName("idempotency_key")
    val idempotencyKey: String,
    @SerialName("baseline_origen")
    val baselineOrigen: String,
    @SerialName("baseline_id")
    val baselineId: String? = null,
    @SerialName("baseline_entradas")
    val baselineEntradas: Long,
    @SerialName("baseline_salidas")
    val baselineSalidas: Long,
)

@Serializable
data class DenominacionItemDto(
    val denominacion: Double,
    val cantidad: Int,
)

/**
 * Respuesta de `crear-recaudacion`. Solo extraemos lo que el cliente
 * necesita: el id remoto y el flag de conflicto. La row completa la
 * obtendrá el técnico cuando T-63 (mis recaudaciones) la pida.
 */
@Serializable
data class CrearRecaudacionResponse(
    val recaudacion: RecaudacionRowDto,
    @SerialName("pdf_signed_url")
    val pdfSignedUrl: String? = null,
    val conflicto: Boolean = false,
    val reusada: Boolean = false,
)

@Serializable
data class RecaudacionRowDto(
    val id: String,
    @SerialName("idempotency_key")
    val idempotencyKey: String? = null,
    val conflicto: Boolean = false,
)
