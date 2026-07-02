package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs de las tablas e inventario que sincroniza T-51.
 *
 * Convenciones:
 * - `numeric` viaja como `String` para preservar precisión decimal.
 * - `bigint` viaja como `Long` (nuestros contadores reales nunca se
 *   acercan a `Long.MAX_VALUE`).
 * - `date` viaja como cadena ISO (`YYYY-MM-DD`).
 * - `timestamptz` viaja como cadena ISO 8601 con offset; lo parseamos a
 *   `Instant` en el mapper.
 *
 * Los nombres camelCase + `@SerialName` mantienen el espejo snake_case
 * con las columnas reales de PostgREST sin contaminar el resto de la app.
 */

@Serializable
data class EmpresaFullDto(
    val id: String,
    val nombre: String,
    val cif: String? = null,
    val direccion: String? = null,
    val telefono: String? = null,
    val email: String? = null,
    @SerialName("logo_url")
    val logoUrl: String? = null,
    @SerialName("zona_horaria")
    val zonaHoraria: String,
    @SerialName("ticket_cabecera")
    val ticketCabecera: String? = null,
    @SerialName("ticket_pie")
    val ticketPie: String? = null,
    @SerialName("redondeo_recaudacion")
    val redondeoRecaudacion: Int = 0,
    @SerialName("porcentaje_recuperacion")
    val porcentajeRecuperacion: Int = 0,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class LocalDto(
    val id: String,
    @SerialName("empresa_id")
    val empresaId: String,
    val nombre: String,
    val direccion: String? = null,
    @SerialName("cif_o_nif")
    val cifONif: String? = null,
    @SerialName("titular_nombre")
    val titularNombre: String? = null,
    val telefono: String? = null,
    val email: String? = null,
    val notas: String? = null,
    @SerialName("comunidad_autonoma")
    val comunidadAutonoma: String? = null,
    @SerialName("provincia_codigo")
    val provinciaCodigo: String? = null,
    @SerialName("municipio_codigo")
    val municipioCodigo: String? = null,
    val calle: String? = null,
    @SerialName("codigo_postal")
    val codigoPostal: String? = null,
    @SerialName("porcentaje_recuperacion")
    val porcentajeRecuperacion: Int? = null,
    @SerialName("updated_at")
    val updatedAt: String,
)

/**
 * Fila de la vista `public.v_credito_local_saldo` (T-215). Solo se descargan
 * las deudas con `estado = 'abierto'`: las que necesita el preview offline de
 * recuperación y la ficha de deudas. Importes como `String` (precisión
 * `numeric(10,2)`); `fecha` ISO `YYYY-MM-DD`.
 */
@Serializable
data class CreditoLocalSaldoDto(
    @SerialName("credito_id")
    val creditoId: String,
    @SerialName("empresa_id")
    val empresaId: String,
    @SerialName("local_id")
    val localId: String,
    val tipo: String,
    @SerialName("instalacion_id")
    val instalacionId: String? = null,
    @Serializable(with = NumericStringSerializer::class)
    val principal: String,
    @SerialName("tipo_interes")
    @Serializable(with = NumericStringSerializer::class)
    val tipoInteres: String,
    val fecha: String,
    val estado: String,
    val notas: String? = null,
    @Serializable(with = NumericStringSerializer::class)
    val recuperado: String,
    @Serializable(with = NumericStringSerializer::class)
    val saldo: String,
)

@Serializable
data class MaquinaDto(
    val id: String,
    @SerialName("empresa_id")
    val empresaId: String,
    @SerialName("numero_serie")
    val numeroSerie: String,
    val modelo: String? = null,
    val fabricante: String? = null,
    @SerialName("valor_credito")
    @Serializable(with = NumericStringSerializer::class)
    val valorCredito: String,
    @SerialName("contador_entradas_inicial")
    val contadorEntradasInicial: Long,
    @SerialName("contador_salidas_inicial")
    val contadorSalidasInicial: Long,
    val estado: String,
    val notas: String? = null,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class LicenciaDto(
    val id: String,
    @SerialName("empresa_id")
    val empresaId: String,
    val numero: String,
    val tipo: String? = null,
    @SerialName("fecha_expedicion")
    val fechaExpedicion: String? = null,
    @SerialName("fecha_caducidad")
    val fechaCaducidad: String? = null,
    @SerialName("comunidad_autonoma")
    val comunidadAutonoma: String? = null,
    val estado: String,
    val notas: String? = null,
    @SerialName("updated_at")
    val updatedAt: String,
)

/**
 * Fila de la vista `public.v_instalacion_actual`. La vista ya hace los
 * joins a maquina/local/licencia y el `LATERAL JOIN obtener_baseline`,
 * así que con un único `select * from v_instalacion_actual` traemos toda
 * la info que necesita la app del técnico.
 */
@Serializable
data class InstalacionActivaDto(
    @SerialName("instalacion_id")
    val instalacionId: String,
    @SerialName("empresa_id")
    val empresaId: String,
    @SerialName("maquina_id")
    val maquinaId: String,
    @SerialName("licencia_id")
    val licenciaId: String,
    @SerialName("local_id")
    val localId: String,
    @SerialName("fecha_inicio")
    val fechaInicio: String,
    @SerialName("tasa_semanal")
    @Serializable(with = NumericStringSerializer::class)
    val tasaSemanal: String,
    @SerialName("porcentaje_local")
    @Serializable(with = NumericStringSerializer::class)
    val porcentajeLocal: String,
    @SerialName("contador_entradas_base")
    val contadorEntradasBase: Long,
    @SerialName("contador_salidas_base")
    val contadorSalidasBase: Long,
    val estado: String,
    @SerialName("baseline_entradas")
    val baselineEntradas: Long,
    @SerialName("baseline_salidas")
    val baselineSalidas: Long,
    @SerialName("baseline_fecha")
    val baselineFecha: String,
    @SerialName("baseline_origen")
    val baselineOrigen: String,
    @SerialName("baseline_referencia_id")
    val baselineReferenciaId: String? = null,
)

/**
 * Fila de la vista `public.v_instalacion_tolva` (T-223): la merma de tolva
 * pendiente de reponer por instalación. La app la usa para descontar la
 * reposición en la previa de recaudación (§5.6), igual que la deuda.
 */
@Serializable
data class TolvaPendienteDto(
    @SerialName("instalacion_id")
    val instalacionId: String,
    @SerialName("pendiente")
    @Serializable(with = NumericStringSerializer::class)
    val pendiente: String,
)
