package com.recre.app.feature.gestion

import com.recre.app.core.data.repository.FabricanteCatalogo
import com.recre.app.core.data.repository.ModeloCatalogo

/** Fabricantes del catálogo como opciones (id + etiqueta) para el autocomplete. */
fun fabricantesComoOpciones(fabricantes: List<FabricanteCatalogo>): List<FkOption> =
    fabricantes.map { FkOption(it.id, it.nombre) }

/**
 * Modelos del fabricante cuyo nombre coincide (laxo: trim + ignore-case), como
 * opciones. Si el fabricante es nuevo (no catalogado) o el nombre está vacío,
 * lista vacía: el usuario podrá teclear un modelo nuevo que la RPC creará bajo
 * ese fabricante al guardar.
 */
fun modelosDeFabricante(
    fabricanteNombre: String,
    fabricantes: List<FabricanteCatalogo>,
    modelos: List<ModeloCatalogo>,
): List<FkOption> {
    val objetivo = fabricanteNombre.trim()
    if (objetivo.isEmpty()) return emptyList()
    val fabId = fabricantes.firstOrNull { it.nombre.trim().equals(objetivo, ignoreCase = true) }?.id
        ?: return emptyList()
    return modelos.filter { it.fabricanteId == fabId }.map { FkOption(it.id, it.nombre) }
}
