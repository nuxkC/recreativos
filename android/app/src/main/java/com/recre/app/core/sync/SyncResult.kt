package com.recre.app.core.sync

import java.time.Instant

/**
 * Resumen de una sincronización exitosa: contadores por tabla y timestamp.
 *
 * Útil para debugging y para los Snackbars de "X recaudaciones, Y locales
 * sincronizados" que aparecerán en T-65 (forzar sync desde Ajustes).
 */
data class SyncSummary(
    val empresaId: String,
    val locales: Int,
    val maquinas: Int,
    val licencias: Int,
    val instalacionesActivas: Int,
    val syncedAt: Instant,
)
