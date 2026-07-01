package com.recre.app.feature.gestion

/**
 * Opción de un desplegable de clave foránea: `id` estable para casar/filtrar +
 * `label` visible. Común a los formularios del gestor (instalaciones, máquinas…).
 */
data class FkOption(val id: String, val label: String)
