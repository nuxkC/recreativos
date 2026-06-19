package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fila de la vista `public.v_recaudacion_historica` (Histórico v2, spec §6.5).
 *
 * No persiste en Room: el histórico es una vista de solo lectura que se
 * carga bajo demanda contra PostgREST. La vista deriva `local_id` /
 * `maquina_id` (y sus nombres/series) del SNAPSHOT INMUTABLE de la
 * instalación con la que se hizo cada recaudación, así que aunque la
 * máquina se mueva luego de local, la fila histórica conserva dónde
 * estaba. La RLS estricta (P2) fluye por `security_invoker`: el técnico
 * ve solo el histórico de sus locales asignados; owner/admin/gestor/
 * contable ven todo el de su empresa. Filtrable por `local_id` /
 * `maquina_id` desde el cliente.
 *
 * Decimales como `String` para no perder precisión Decimal antes de
 * formatear con `BigDecimal`. Misma convención que la web.
 */
@Serializable
data class RecaudacionHistoricaRow(
    val id: String,
    @SerialName("instalacion_id")
    val instalacionId: String,
    @SerialName("local_id")
    val localId: String,
    @SerialName("maquina_id")
    val maquinaId: String,
    @SerialName("licencia_id")
    val licenciaId: String? = null,
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
    // --- Columnas planas derivadas del snapshot por la vista v2 ---
    @SerialName("local_nombre")
    val localNombre: String,
    @SerialName("local_direccion")
    val localDireccion: String? = null,
    @SerialName("maquina_numero_serie")
    val maquinaNumeroSerie: String,
    @SerialName("maquina_modelo")
    val maquinaModelo: String? = null,
    @SerialName("maquina_fabricante")
    val maquinaFabricante: String? = null,
    @SerialName("licencia_numero")
    val licenciaNumero: String? = null,
)
