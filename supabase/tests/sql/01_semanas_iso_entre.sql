-- =============================================================================
-- T-18 — Tests de `semanas_iso_entre`.
--
-- Casos del `.kiro/specs/recre/design.md §5.2` más bordes adicionales.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(10);

-- 1) Viernes W20 -> Lunes W21 (caso del design)
SELECT is(
    public.semanas_iso_entre(
        '2026-05-15 12:00+02'::timestamptz,
        '2026-05-18 09:00+02'::timestamptz,
        'Europe/Madrid'
    ),
    1,
    'Vie W20 -> Lun W21 = 1 semana'
);

-- 2) Mismo día, ambos en W22 -> 0 semanas (ya pagada)
SELECT is(
    public.semanas_iso_entre(
        '2026-05-25 12:00+02'::timestamptz,
        '2026-05-29 12:00+02'::timestamptz,
        'Europe/Madrid'
    ),
    0,
    'Mié W22 -> Vie W22 (misma semana) = 0'
);

-- 3) Vie W22 -> Lun W25 (3 semanas: W23, W24, W25)
SELECT is(
    public.semanas_iso_entre(
        '2026-05-29 12:00+02'::timestamptz,
        '2026-06-15 09:00+02'::timestamptz,
        'Europe/Madrid'
    ),
    3,
    'Vie W22 -> Lun W25 = 3 semanas'
);

-- 4) Instalación Vie W20 -> primera recaudación Mié W22 = 2 semanas
SELECT is(
    public.semanas_iso_entre(
        '2026-05-15 12:00+02'::timestamptz,
        '2026-05-27 12:00+02'::timestamptz,
        'Europe/Madrid'
    ),
    2,
    'Instalación Vie W20 -> primera recaudación Mié W22 = 2 semanas'
);

-- 5) Fecha invertida (hasta < desde): el resultado nunca es negativo
SELECT is(
    public.semanas_iso_entre(
        '2026-05-29 12:00+02'::timestamptz,
        '2026-05-15 12:00+02'::timestamptz,
        'Europe/Madrid'
    ),
    0,
    'Hasta < desde -> 0 (no negativo)'
);

-- 6) Mismo timestamp -> 0
SELECT is(
    public.semanas_iso_entre(
        '2026-05-19 22:00+02'::timestamptz,
        '2026-05-19 22:00+02'::timestamptz,
        'Europe/Madrid'
    ),
    0,
    'Mismo timestamp -> 0 semanas'
);

-- 7) Cambio de año ISO (W52 2025 -> W01 2026)
-- 2025-12-26 (Vie, W52) -> 2026-01-05 (Lun, W02 ISO 2026)
SELECT is(
    public.semanas_iso_entre(
        '2025-12-26 10:00+01'::timestamptz,
        '2026-01-05 10:00+01'::timestamptz,
        'Europe/Madrid'
    ),
    2,
    'Cambio de año ISO: Vie W52-2025 -> Lun W02-2026 = 2 semanas'
);

-- 8) Cambio horario verano (último domingo de marzo, +1 -> +2 en Madrid)
-- 2026-03-27 (Vie W13) -> 2026-04-06 (Lun W15) = 2 semanas
SELECT is(
    public.semanas_iso_entre(
        '2026-03-27 10:00+01'::timestamptz,
        '2026-04-06 10:00+02'::timestamptz,
        'Europe/Madrid'
    ),
    2,
    'Cambio horario verano: 2 semanas correctas pese a TZ shift'
);

-- 9) Año especial con 53 semanas ISO (2020): cruzar W52->W53->W01
-- Tomamos un caso seguro: 2020-12-21 Lun W52 -> 2021-01-04 Lun W01 (W01-2021)
-- W53 existe en 2020. Distancia: 2020-12-21 (W52-Mon), 2020-12-28 (W53-Mon),
-- 2021-01-04 (W01-2021-Mon) -> 2 semanas distintas tras la referencia.
SELECT is(
    public.semanas_iso_entre(
        '2020-12-21 10:00+01'::timestamptz,
        '2021-01-04 10:00+01'::timestamptz,
        'Europe/Madrid'
    ),
    2,
    'Año con 53 semanas ISO: cruce correcto W52-2020 -> W01-2021'
);

-- 10) Default tz aplica si no se pasa (Europe/Madrid)
SELECT is(
    public.semanas_iso_entre(
        '2026-05-15 12:00+02'::timestamptz,
        '2026-05-18 09:00+02'::timestamptz
    ),
    1,
    'Default tz Europe/Madrid funciona como tercer parámetro implícito'
);

SELECT * FROM finish();
ROLLBACK;
