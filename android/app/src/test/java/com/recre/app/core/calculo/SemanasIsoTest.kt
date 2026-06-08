package com.recre.app.core.calculo

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Replica los 10 casos del test pgTAP `01_semanas_iso_entre.sql` para
 * garantizar que la implementación Kotlin produce el mismo resultado que
 * la función SQL `public.semanas_iso_entre`.
 *
 * Si en el futuro la SQL cambia su semántica, este test fallará y nos
 * obligará a actualizar el SSOT cliente al mismo tiempo.
 */
class SemanasIsoTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    private fun parse(iso: String): Instant = java.time.OffsetDateTime.parse(iso).toInstant()

    @Test
    fun `vie W20 a lun W21 = 1 semana`() {
        assertEquals(
            1,
            semanasIsoEntre(
                desde = parse("2026-05-15T12:00+02:00"),
                hasta = parse("2026-05-18T09:00+02:00"),
                zoneId = madrid,
            ),
        )
    }

    @Test
    fun `misma semana W22 = 0 semanas`() {
        assertEquals(
            0,
            semanasIsoEntre(
                desde = parse("2026-05-25T12:00+02:00"),
                hasta = parse("2026-05-29T12:00+02:00"),
                zoneId = madrid,
            ),
        )
    }

    @Test
    fun `vie W22 a lun W25 = 3 semanas`() {
        assertEquals(
            3,
            semanasIsoEntre(
                desde = parse("2026-05-29T12:00+02:00"),
                hasta = parse("2026-06-15T09:00+02:00"),
                zoneId = madrid,
            ),
        )
    }

    @Test
    fun `instalacion vie W20 a primera recaudacion mie W22 = 2 semanas`() {
        assertEquals(
            2,
            semanasIsoEntre(
                desde = parse("2026-05-15T12:00+02:00"),
                hasta = parse("2026-05-27T12:00+02:00"),
                zoneId = madrid,
            ),
        )
    }

    @Test
    fun `fechas invertidas devuelven 0 nunca negativo`() {
        assertEquals(
            0,
            semanasIsoEntre(
                desde = parse("2026-05-29T12:00+02:00"),
                hasta = parse("2026-05-15T12:00+02:00"),
                zoneId = madrid,
            ),
        )
    }

    @Test
    fun `mismo timestamp = 0`() {
        val t = parse("2026-05-19T22:00+02:00")
        assertEquals(0, semanasIsoEntre(t, t, madrid))
    }

    @Test
    fun `cambio de anio ISO W52-2025 a W02-2026 = 2 semanas`() {
        assertEquals(
            2,
            semanasIsoEntre(
                desde = parse("2025-12-26T10:00+01:00"),
                hasta = parse("2026-01-05T10:00+01:00"),
                zoneId = madrid,
            ),
        )
    }

    @Test
    fun `cambio horario verano respeta cuenta de semanas`() {
        // 2026-03-27 (Vie W13) -> 2026-04-06 (Lun W15)
        assertEquals(
            2,
            semanasIsoEntre(
                desde = parse("2026-03-27T10:00+01:00"),
                hasta = parse("2026-04-06T10:00+02:00"),
                zoneId = madrid,
            ),
        )
    }

    @Test
    fun `anio con 53 semanas ISO 2020 cruza correctamente`() {
        // 2020-12-21 Lun W52 -> 2021-01-04 Lun W01-2021 = 2 semanas
        assertEquals(
            2,
            semanasIsoEntre(
                desde = parse("2020-12-21T10:00+01:00"),
                hasta = parse("2021-01-04T10:00+01:00"),
                zoneId = madrid,
            ),
        )
    }

    @Test
    fun `default tz Europe Madrid`() {
        assertEquals(
            1,
            semanasIsoEntre(
                desde = parse("2026-05-15T12:00+02:00"),
                hasta = parse("2026-05-18T09:00+02:00"),
            ),
        )
    }
}
