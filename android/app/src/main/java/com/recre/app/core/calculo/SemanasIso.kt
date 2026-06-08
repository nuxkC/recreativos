package com.recre.app.core.calculo

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Cuenta las semanas ISO de calendario entre dos instantes en una zona
 * horaria dada. **Excluye la semana de referencia, incluye la actual.**
 *
 * Espejo Kotlin de la función SQL `public.semanas_iso_entre` (T-13a):
 *
 * ```sql
 * GREATEST(0,
 *   ((date_trunc('week', hasta AT TIME ZONE tz))::date
 *    - (date_trunc('week', desde AT TIME ZONE tz))::date) / 7
 * )
 * ```
 *
 * La SQL trunca al lunes ISO; aquí hacemos lo mismo con
 * `TemporalAdjusters.previousOrSame(MONDAY)` y dividimos los días entre 7.
 *
 * Casos cubiertos por los tests pgTAP de T-18 y replicados en
 * `SemanasIsoTest.kt`:
 * - Misma semana → 0 (la tasa ya está pagada)
 * - Cambio de año ISO 2025-W52 → 2026-W01
 * - DST (último domingo de marzo en Europe/Madrid)
 * - Año con 53 semanas ISO (2020)
 * - Fechas invertidas → 0 (nunca negativo)
 */
fun semanasIsoEntre(
    desde: Instant,
    hasta: Instant,
    zoneId: ZoneId = ZONA_HORARIA_DEFAULT,
): Int {
    val desdeLunes = desde.atZone(zoneId)
        .toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val hastaLunes = hasta.atZone(zoneId)
        .toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dias = ChronoUnit.DAYS.between(desdeLunes, hastaLunes).toInt()
    return maxOf(0, dias / 7)
}

/**
 * Zona horaria por defecto. Espejo de `ZONA_HORARIA_DEFAULT` en TS.
 *
 * Cuando el flujo del técnico tenga la empresa cargada (T-51) usaremos su
 * `zona_horaria` real en lugar de este default; el default solo cubre el
 * arranque antes de tener `EmpresaParams` en cache.
 */
val ZONA_HORARIA_DEFAULT: ZoneId = ZoneId.of("Europe/Madrid")
