package com.recre.app.feature.locales

/**
 * Dirección de un local para mostrar en SOLO-LECTURA. Prioriza la dirección
 * estructurada (calle, código postal, comunidad autónoma) y cae al `direccion`
 * de texto libre si no hay estructura. Devuelve null si no hay nada.
 *
 * En móvil NO se resuelve el nombre de provincia/municipio (son códigos INE que
 * exigirían red); se muestran los campos ya disponibles offline. Es el espejo
 * degradado del formateador de la web (que sí resuelve nombres en el detalle).
 */
fun formatearDireccionLocal(
    calle: String?,
    codigoPostal: String?,
    comunidadAutonoma: String?,
    direccionLibre: String?,
): String? {
    val partes = buildList {
        if (!calle.isNullOrBlank()) add(calle.trim())
        if (!codigoPostal.isNullOrBlank()) add(codigoPostal.trim())
        if (!comunidadAutonoma.isNullOrBlank()) add(comunidadAutonoma.trim())
    }
    return if (partes.isNotEmpty()) partes.joinToString(", ") else direccionLibre?.trim()?.ifBlank { null }
}
