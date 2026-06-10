package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs `Insert` y `Update` de las cuatro entidades del CRUD gestor en la
 * app del técnico (T-66..T-69). Espejan exactamente las columnas que la
 * web ya escribe desde sus Server Actions (`web/src/lib/{licencias,
 * maquinas,locales,instalaciones}/actions.ts`), incluyendo el manejo de
 * `null` para campos opcionales.
 *
 * Convenciones (heredadas del resto de DTOs):
 * - `numeric` viaja como `String` para preservar precisión (`valorCredito`,
 *   `tasaSemanal`, `porcentajeLocal`).
 * - `bigint` viaja como `Long`.
 * - `date` viaja como cadena ISO `YYYY-MM-DD`.
 * - `null` propagado como tal: PostgREST hace UPDATE/INSERT con NULL
 *   donde corresponda (importante para vaciar campos opcionales).
 */

// ---------------------------------------------------------- licencia

@Serializable
data class LicenciaInsertDto(
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
)

@Serializable
data class LicenciaUpdateDto(
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
)

// ---------------------------------------------------------- maquina

@Serializable
data class MaquinaInsertDto(
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
)

@Serializable
data class MaquinaUpdateDto(
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
)

// ---------------------------------------------------------- local

@Serializable
data class LocalInsertDto(
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
)

@Serializable
data class LocalUpdateDto(
    val nombre: String,
    val direccion: String? = null,
    @SerialName("cif_o_nif")
    val cifONif: String? = null,
    @SerialName("titular_nombre")
    val titularNombre: String? = null,
    val telefono: String? = null,
    val email: String? = null,
    val notas: String? = null,
)

// ---------------------------------------------------------- instalacion

/**
 * Alta de instalación (siempre estado `activa`). El cierre va por la
 * Edge Function `cerrar-instalacion` (T-23), no por UPDATE directo, para
 * que también libere los locks pendientes y aplique las validaciones de
 * coherencia en el backend.
 */
@Serializable
data class InstalacionInsertDto(
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
    val estado: String = "activa",
    val notas: String? = null,
)

/**
 * Update parcial de instalación. Las FKs (`maquina_id`, `licencia_id`,
 * `local_id`) son inmutables en edición — cambiarlas rompería la
 * baseline. Para reasignar: cerrar y crear nueva. Tampoco se actualiza
 * `estado` desde el form (eso pasa por la Edge Function).
 */
@Serializable
data class InstalacionUpdateDto(
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
    val notas: String? = null,
)

@Serializable
data class CerrarInstalacionRequest(
    @SerialName("instalacion_id")
    val instalacionId: String,
    @SerialName("fecha_fin")
    val fechaFin: String,
    val notas: String? = null,
)

/** Identificador devuelto tras cualquier insert (PostgREST con `select=id`). */
@Serializable
data class IdResponseDto(val id: String)
