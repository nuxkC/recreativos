package com.recre.app.feature.gestion

import com.recre.app.core.data.repository.Municipio
import com.recre.app.core.data.repository.Provincia

/**
 * Provincias de una CCAA como opciones (id=código INE, label=nombre), ordenadas
 * por nombre. CCAA vacía → lista vacía (provincia deshabilitada hasta elegir CCAA).
 * Match laxo (trim + ignore-case) contra la lista de oro.
 */
fun provinciasDeCcaa(comunidadAutonoma: String, provincias: List<Provincia>): List<FkOption> {
    if (comunidadAutonoma.isBlank()) return emptyList()
    // Igualdad EXACTA (como la web): la lista de oro y provincia.comunidad_autonoma
    // son byte-idénticas y el validador server-side compara exacto. Así el cliente
    // no ofrece una provincia que el servidor rechazaría por coherencia.
    return provincias
        .filter { it.comunidadAutonoma == comunidadAutonoma }
        .sortedBy { it.nombre.lowercase() }
        .map { FkOption(it.codigo, it.nombre) }
}

/** Municipios (ya filtrados por provincia en el servidor) como opciones, ordenados. */
fun municipiosComoOpciones(municipios: List<Municipio>): List<FkOption> =
    municipios.sortedBy { it.nombre.lowercase() }.map { FkOption(it.codigo, it.nombre) }
