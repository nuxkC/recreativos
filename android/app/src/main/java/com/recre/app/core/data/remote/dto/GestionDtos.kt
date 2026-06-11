package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parámetros de las RPCs `crear/actualizar/eliminar_<entidad>` (SECURITY
 * DEFINER) del CRUD gestor en la app del técnico (T-66..T-69). La escritura
 * directa a las tablas está REVOCADA: todo pasa por la función, que valida
 * rol (gestor) + tenant. Los nombres serializados son los de los argumentos
 * SQL (`p_*`); PostgREST mapea los parámetros con nombre.
 *
 * Convenciones:
 * - Campos opcionales declarados como `String?` SIN valor por defecto, para que
 *   kotlinx serialice SIEMPRE el campo (como `null` o valor). Si se omitieran,
 *   PostgREST fallaría por parámetro ausente (la función no les pone DEFAULT
 *   salvo `p_notas`, que es el último argumento).
 * - `numeric` viaja como número vía [NumericStringSerializer] (`valorCredito`).
 * - `bigint` viaja como `Long`.
 * - `date` viaja como cadena ISO `YYYY-MM-DD`.
 */

// ---------------------------------------------------------- licencia

@Serializable
data class CrearLicenciaParams(
    @SerialName("p_empresa_id")
    val empresaId: String,
    @SerialName("p_numero")
    val numero: String,
    @SerialName("p_tipo")
    val tipo: String?,
    @SerialName("p_fecha_expedicion")
    val fechaExpedicion: String?,
    @SerialName("p_fecha_caducidad")
    val fechaCaducidad: String?,
    @SerialName("p_comunidad_autonoma")
    val comunidadAutonoma: String?,
    @SerialName("p_estado")
    val estado: String,
    @SerialName("p_notas")
    val notas: String?,
)

@Serializable
data class ActualizarLicenciaParams(
    @SerialName("p_id")
    val id: String,
    @SerialName("p_numero")
    val numero: String,
    @SerialName("p_tipo")
    val tipo: String?,
    @SerialName("p_fecha_expedicion")
    val fechaExpedicion: String?,
    @SerialName("p_fecha_caducidad")
    val fechaCaducidad: String?,
    @SerialName("p_comunidad_autonoma")
    val comunidadAutonoma: String?,
    @SerialName("p_estado")
    val estado: String,
    @SerialName("p_notas")
    val notas: String?,
)

@Serializable
data class EliminarLicenciaParams(
    @SerialName("p_id")
    val id: String,
)

// ---------------------------------------------------------- maquina

@Serializable
data class CrearMaquinaParams(
    @SerialName("p_empresa_id")
    val empresaId: String,
    @SerialName("p_numero_serie")
    val numeroSerie: String,
    @SerialName("p_modelo")
    val modelo: String?,
    @SerialName("p_fabricante")
    val fabricante: String?,
    @SerialName("p_valor_credito")
    @Serializable(with = NumericStringSerializer::class)
    val valorCredito: String,
    @SerialName("p_contador_entradas_inicial")
    val contadorEntradasInicial: Long,
    @SerialName("p_contador_salidas_inicial")
    val contadorSalidasInicial: Long,
    @SerialName("p_estado")
    val estado: String,
    @SerialName("p_notas")
    val notas: String?,
)

@Serializable
data class ActualizarMaquinaParams(
    @SerialName("p_id")
    val id: String,
    @SerialName("p_numero_serie")
    val numeroSerie: String,
    @SerialName("p_modelo")
    val modelo: String?,
    @SerialName("p_fabricante")
    val fabricante: String?,
    @SerialName("p_valor_credito")
    @Serializable(with = NumericStringSerializer::class)
    val valorCredito: String,
    @SerialName("p_contador_entradas_inicial")
    val contadorEntradasInicial: Long,
    @SerialName("p_contador_salidas_inicial")
    val contadorSalidasInicial: Long,
    @SerialName("p_estado")
    val estado: String,
    @SerialName("p_notas")
    val notas: String?,
)

@Serializable
data class EliminarMaquinaParams(
    @SerialName("p_id")
    val id: String,
)

// ---------------------------------------------------------- local

@Serializable
data class CrearLocalParams(
    @SerialName("p_empresa_id")
    val empresaId: String,
    @SerialName("p_nombre")
    val nombre: String,
    @SerialName("p_direccion")
    val direccion: String?,
    @SerialName("p_cif_o_nif")
    val cifONif: String?,
    @SerialName("p_titular_nombre")
    val titularNombre: String?,
    @SerialName("p_telefono")
    val telefono: String?,
    @SerialName("p_email")
    val email: String?,
    @SerialName("p_notas")
    val notas: String?,
)

@Serializable
data class ActualizarLocalParams(
    @SerialName("p_id")
    val id: String,
    @SerialName("p_nombre")
    val nombre: String,
    @SerialName("p_direccion")
    val direccion: String?,
    @SerialName("p_cif_o_nif")
    val cifONif: String?,
    @SerialName("p_titular_nombre")
    val titularNombre: String?,
    @SerialName("p_telefono")
    val telefono: String?,
    @SerialName("p_email")
    val email: String?,
    @SerialName("p_notas")
    val notas: String?,
)

@Serializable
data class EliminarLocalParams(
    @SerialName("p_id")
    val id: String,
)

// ---------------------------------------------------------- instalacion

/**
 * Parámetros de la RPC `crear_instalacion` (SECURITY DEFINER). La escritura
 * directa a la tabla está revocada: el alta pasa SIEMPRE por la función, que
 * valida rol+tenant y DERIVA la base de contadores de la máquina (por eso la
 * base no es parámetro). Estado siempre `activa`.
 */
@Serializable
data class CrearInstalacionParams(
    @SerialName("p_empresa_id")
    val empresaId: String,
    @SerialName("p_maquina_id")
    val maquinaId: String,
    @SerialName("p_licencia_id")
    val licenciaId: String,
    @SerialName("p_local_id")
    val localId: String,
    @SerialName("p_fecha_inicio")
    val fechaInicio: String,
    @SerialName("p_tasa_semanal")
    @Serializable(with = NumericStringSerializer::class)
    val tasaSemanal: String,
    @SerialName("p_porcentaje_local")
    @Serializable(with = NumericStringSerializer::class)
    val porcentajeLocal: String,
    @SerialName("p_notas")
    val notas: String? = null,
)

/**
 * Parámetros de la RPC `actualizar_instalacion`. Las FKs (`maquina_id`,
 * `licencia_id`, `local_id`) y la base de contadores son inmutables; para
 * reasignar máquina: cerrar y crear nueva. El cierre va por la Edge Function.
 */
@Serializable
data class ActualizarInstalacionParams(
    @SerialName("p_id")
    val id: String,
    @SerialName("p_fecha_inicio")
    val fechaInicio: String,
    @SerialName("p_tasa_semanal")
    @Serializable(with = NumericStringSerializer::class)
    val tasaSemanal: String,
    @SerialName("p_porcentaje_local")
    @Serializable(with = NumericStringSerializer::class)
    val porcentajeLocal: String,
    @SerialName("p_notas")
    val notas: String? = null,
)

/** Parámetros de la RPC `eliminar_instalacion`. */
@Serializable
data class EliminarInstalacionParams(
    @SerialName("p_id")
    val id: String,
)

@Serializable
data class CerrarInstalacionRequest(
    @SerialName("instalacion_id")
    val instalacionId: String,
    @SerialName("fecha_fin")
    val fechaFin: String,
    val notas: String? = null,
)
