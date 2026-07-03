package com.recre.app.feature.locales

/**
 * Dirección de un local para mostrar en SOLO-LECTURA, DERIVADA de los campos
 * estructurados (T-277; ya no existe el texto libre). Devuelve null si no hay
 * nada que mostrar.
 *
 * En móvil NO se resuelve el nombre de provincia/municipio (son códigos INE que
 * exigirían red); se compone con lo disponible offline: calle + código postal +
 * comunidad autónoma. Es el espejo degradado del formateador de la web (que sí
 * resuelve nombres en el detalle).
 */
fun formatearDireccionLocal(
    calle: String?,
    codigoPostal: String?,
    comunidadAutonoma: String?,
): String? {
    val partes = buildList {
        if (!calle.isNullOrBlank()) add(calle.trim())
        if (!codigoPostal.isNullOrBlank()) add(codigoPostal.trim())
        if (!comunidadAutonoma.isNullOrBlank()) add(comunidadAutonoma.trim())
    }
    return if (partes.isNotEmpty()) partes.joinToString(", ") else null
}
