package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fila de `public.provincia` (referencia global INE, sin empresa_id). */
@Serializable
data class ProvinciaDto(
    val codigo: String,
    val nombre: String,
    @SerialName("comunidad_autonoma") val comunidadAutonoma: String,
)

/** Fila de `public.municipio`; `provincia_codigo` = FK a su provincia. */
@Serializable
data class MunicipioDto(
    val codigo: String,
    val nombre: String,
    @SerialName("provincia_codigo") val provinciaCodigo: String,
)
