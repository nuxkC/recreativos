package com.recre.app.core.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO de la fila resultado de:
 *
 * ```
 * select rol, activo, empresa:empresa_id ( id, nombre, zona_horaria )
 *   from empresa_usuario
 *  where activo = true
 * ```
 *
 * Se mapea a [com.recre.app.core.session.Membresia] en el repositorio. La
 * UI nunca trabaja con DTOs de transporte: la frontera vive en `data/`.
 */
@Serializable
data class MembresiaDto(
    val rol: String,
    val activo: Boolean,
    val empresa: EmpresaDto,
)

@Serializable
data class EmpresaDto(
    val id: String,
    val nombre: String,
    @SerialName("zona_horaria")
    val zonaHoraria: String,
)
