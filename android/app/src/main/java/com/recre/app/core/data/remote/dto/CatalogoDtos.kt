package com.recre.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fila de `public.fabricante` (catálogo global, sin empresa_id). */
@Serializable
data class FabricanteDto(
    val id: String,
    val nombre: String,
)

/** Fila de `public.modelo`; `fabricante_id` = FK a su fabricante. */
@Serializable
data class ModeloDto(
    val id: String,
    val nombre: String,
    @SerialName("fabricante_id") val fabricanteId: String,
)
