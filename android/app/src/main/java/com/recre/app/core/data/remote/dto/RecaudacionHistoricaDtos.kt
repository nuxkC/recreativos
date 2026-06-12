package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fila joinada de `public.recaudacion` que consume la pantalla
 * "Mis recaudaciones" (T-63).
 *
 * No persiste en Room: el histórico es una vista de solo lectura que se
 * carga bajo demanda contra Postgrest. La RLS ya filtra por empresa
 * activa; añadimos `tecnico_id = auth.uid()` en el query para mostrar
 * solo las recaudaciones del técnico autenticado, como exige T-63.
 *
 * Decimales como `String` para no perder precisión Decimal antes de
 * formatear con `BigDecimal`. Misma convención que la web.
 */
@Serializable
data class RecaudacionHistoricaRow(
    val id: String,
    @SerialName("instalacion_id")
    val instalacionId: String,
    @SerialName("tecnico_id")
    val tecnicoId: String,
    val fecha: String,
    @SerialName("contador_entradas_anterior")
    val contadorEntradasAnterior: Long,
    @SerialName("contador_salidas_anterior")
    val contadorSalidasAnterior: Long,
    @SerialName("contador_entradas_actual")
    val contadorEntradasActual: Long,
    @SerialName("contador_salidas_actual")
    val contadorSalidasActual: Long,
    @SerialName("valor_credito_aplicado")
    @Serializable(with = NumericStringSerializer::class)
    val valorCreditoAplicado: String,
    @SerialName("recaudacion_bruta")
    @Serializable(with = NumericStringSerializer::class)
    val recaudacionBruta: String,
    @SerialName("semanas_aplicadas")
    val semanasAplicadas: Int,
    @SerialName("tasa_semanal_aplicada")
    @Serializable(with = NumericStringSerializer::class)
    val tasaSemanalAplicada: String,
    @SerialName("tasa_total_aplicada")
    @Serializable(with = NumericStringSerializer::class)
    val tasaTotalAplicada: String,
    @SerialName("recaudacion_neta")
    @Serializable(with = NumericStringSerializer::class)
    val recaudacionNeta: String,
    @SerialName("porcentaje_local_aplicado")
    @Serializable(with = NumericStringSerializer::class)
    val porcentajeLocalAplicado: String,
    @SerialName("parte_local")
    @Serializable(with = NumericStringSerializer::class)
    val parteLocal: String,
    @SerialName("parte_empresa")
    @Serializable(with = NumericStringSerializer::class)
    val parteEmpresa: String,
    @SerialName("reposicion_tolva")
    @Serializable(with = NumericStringSerializer::class)
    val reposicionTolva: String = "0.00",
    @SerialName("desglose_total")
    val desgloseTotal: List<DenominacionItemDto>,
    @SerialName("desglose_local")
    val desgloseLocal: List<DenominacionItemDto>,
    @SerialName("firma_url")
    val firmaUrl: String? = null,
    @SerialName("pdf_url")
    val pdfUrl: String? = null,
    val conflicto: Boolean,
    @SerialName("revisado_en")
    val revisadoEn: String? = null,
    val estado: String,
    @SerialName("motivo_anulacion")
    val motivoAnulacion: String? = null,
    @SerialName("anulada_en")
    val anuladaEn: String? = null,
    val instalacion: InstalacionResumenDto? = null,
)

/**
 * Resumen joinado por la query: licencia, máquina y local con los
 * mínimos imprescindibles para pintar la fila.
 */
@Serializable
data class InstalacionResumenDto(
    val id: String,
    val licencia: LicenciaResumenDto? = null,
    val maquina: MaquinaResumenDto? = null,
    val local: LocalResumenDto? = null,
)

@Serializable
data class LicenciaResumenDto(
    val id: String,
    val numero: String,
)

@Serializable
data class MaquinaResumenDto(
    val id: String,
    @SerialName("numero_serie")
    val numeroSerie: String,
    val modelo: String? = null,
    val fabricante: String? = null,
)

@Serializable
data class LocalResumenDto(
    val id: String,
    val nombre: String,
    val direccion: String? = null,
)
