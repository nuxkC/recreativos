-- =============================================================================
-- Planificación de recaudación — corrige el indicador "¿toca?" a SEMANA + DÍA
-- objetivo, y añade el estado 'pendiente' (spec §4.2 revisada).
--
-- PROBLEMA de v_agenda_operario (20260618120000): marcaba 'atrasado' en cuanto
-- pasaba UN día del arranque del ciclo (S = F + floor((hoy−F)/(7C))·7C días en
-- aritmética de DÍAS), aunque el día/semana en que de verdad toca recaudar aún
-- no hubiese llegado. Ej.: F=miércoles, cada 2 semanas, hoy=lunes de la 2ª
-- semana → salía 'atrasado' cuando en realidad toca el miércoles de ESA semana.
--
-- NUEVO MODELO, anclado a SEMANA ISO (no a días contados desde F):
--   - Los ciclos se cuentan en semanas ISO entre la semana de F y la de hoy
--     (invariante del repo: semanas ISO solo vía semanas_iso_entre()).
--   - DÍA objetivo del ciclo vigente = el día de la semana de F, en la semana
--     objetivo del ciclo: F + ciclos·C·7 días (ciclos = semanas_iso(F,hoy) / C).
--   - Estados:
--       sin_planificar : sin calendario.
--       al_dia         : aún no empieza (hoy<F) o atendido en el ciclo.
--       pendiente      : es su semana objetivo pero aún no llegó su día (hoy<D).
--       toca_hoy       : hoy es el día objetivo (hoy=D) y sin atender.
--       atrasado       : pasó el día objetivo sin atender (hoy>D). Persiste entre
--                        semanas hasta que se recaude (en su semana off una
--                        bisemanal no atendida sigue 'atrasado', no desaparece).
--
-- La lógica de fechas se extrae a funciones IMMUTABLE para testearla de forma
-- DETERMINISTA (pgTAP) con fechas fijas, sin depender del día real del test.
-- NO cambia ninguna escritura ni cálculo de dinero. La vista mantiene sus
-- columnas (CREATE OR REPLACE compatible); 'pendiente' es un estado NUEVO.
-- =============================================================================

-- Día en que toca recaudar en el ciclo vigente: el weekday de F llevado a la
-- semana objetivo del ciclo actual. Las fechas ya llegan en la TZ de la empresa;
-- las anclamos a mediodía UTC para que semanas_iso_entre cuente semanas ISO sin
-- reintroducir desfases de zona (UTC no tiene DST y el mediodía evita bordes).
CREATE OR REPLACE FUNCTION public.fecha_objetivo_agenda(
    p_fecha_inicio date,
    p_cadencia     smallint,
    p_hoy          date
) RETURNS date
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT p_fecha_inicio
        + (
            (
                public.semanas_iso_entre(
                    (p_fecha_inicio + time '12:00') AT TIME ZONE 'UTC',
                    (p_hoy          + time '12:00') AT TIME ZONE 'UTC',
                    'UTC'
                ) / p_cadencia       -- ciclos completos (división entera de ints = floor)
            ) * p_cadencia * 7       -- → días a sumar a F (suma semanas enteras: preserva weekday)
          );
$$;

COMMENT ON FUNCTION public.fecha_objetivo_agenda(date, smallint, date) IS
    'Día en que toca recaudar en el ciclo vigente: el weekday de F en la semana objetivo del ciclo (F + ciclos·C·7, ciclos=semanas_iso(F,hoy)/C). Planificación §4.2.';

-- Estado de agenda de un local. Puro y determinista: dadas las fechas y si fue
-- atendido en el ciclo, decide el estado. La 'fecha objetivo' la calcula quien
-- llama (fecha_objetivo_agenda) para no recomputarla dos veces.
CREATE OR REPLACE FUNCTION public.estado_agenda(
    p_cadencia       smallint,
    p_fecha_inicio   date,
    p_hoy            date,
    p_fecha_objetivo date,
    p_atendido       boolean
) RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT CASE
        WHEN p_cadencia IS NULL        THEN 'sin_planificar'
        WHEN p_hoy < p_fecha_inicio    THEN 'al_dia'    -- aún no empieza
        WHEN p_atendido                THEN 'al_dia'    -- ya recaudado este ciclo
        WHEN p_hoy < p_fecha_objetivo  THEN 'pendiente' -- es su semana, aún no su día
        WHEN p_hoy = p_fecha_objetivo  THEN 'toca_hoy'
        ELSE 'atrasado'                                 -- pasó su día sin atender
    END;
$$;

COMMENT ON FUNCTION public.estado_agenda(smallint, date, date, date, boolean) IS
    'Estado de agenda: sin_planificar/al_dia/pendiente/toca_hoy/atrasado, anclado a la semana y día objetivo del ciclo. Planificación §4.2.';

-- Vista recreada usando las funciones. Mantiene columnas y orden (compatible con
-- CREATE OR REPLACE); fecha_programada_vigente pasa a ser el DÍA objetivo del
-- ciclo vigente. "hoy" en la TZ de la empresa; security_invoker → RLS de P2.
CREATE OR REPLACE VIEW public.v_agenda_operario
WITH (security_invoker = true) AS
WITH base AS (
    SELECT
        l.id          AS local_id,
        l.empresa_id,
        l.nombre,
        l.operario_id,
        l.cadencia_semanas,
        l.fecha_inicio_recaudacion,
        e.zona_horaria                            AS tz,
        (now() AT TIME ZONE e.zona_horaria)::date AS hoy
    FROM public.local l
    JOIN public.empresa e ON e.id = l.empresa_id
),
calc AS (
    SELECT b.*,
        CASE
            WHEN b.cadencia_semanas IS NULL THEN NULL
            ELSE public.fecha_objetivo_agenda(
                     b.fecha_inicio_recaudacion, b.cadencia_semanas, b.hoy)
        END AS fecha_objetivo
    FROM base b
),
atendido AS (
    SELECT c.*,
        -- Atendido este ciclo = recaudación firme o lectura_no_recaudada en
        -- cualquier instalación del local, desde el LUNES de la semana objetivo
        -- hasta hoy (permite recaudar cualquier día de la semana que toca, y
        -- limpia un 'atrasado' al recaudar tarde dentro de la ventana).
        CASE
            WHEN c.fecha_objetivo IS NULL THEN false
            ELSE EXISTS (
                SELECT 1
                  FROM public.instalacion i
                 WHERE i.local_id = c.local_id
                   AND (
                       EXISTS (
                           SELECT 1 FROM public.recaudacion r
                            WHERE r.instalacion_id = i.id
                              AND r.estado = 'firme'
                              AND (r.fecha AT TIME ZONE c.tz)::date
                                  BETWEEN date_trunc('week', c.fecha_objetivo::timestamp)::date AND c.hoy
                       )
                       OR EXISTS (
                           SELECT 1 FROM public.lectura_no_recaudada lnr
                            WHERE lnr.instalacion_id = i.id
                              AND (lnr.fecha AT TIME ZONE c.tz)::date
                                  BETWEEN date_trunc('week', c.fecha_objetivo::timestamp)::date AND c.hoy
                       )
                   )
            )
        END AS atendido_ciclo
    FROM calc c
)
SELECT
    a.local_id,
    a.empresa_id,
    a.nombre,
    a.operario_id,
    a.cadencia_semanas,
    a.fecha_inicio_recaudacion,
    a.fecha_objetivo AS fecha_programada_vigente,
    public.estado_agenda(
        a.cadencia_semanas, a.fecha_inicio_recaudacion, a.hoy,
        a.fecha_objetivo, a.atendido_ciclo) AS estado,
    (SELECT count(*) FROM public.instalacion i2
      WHERE i2.local_id = a.local_id AND i2.estado = 'activa') AS n_maquinas
FROM atendido a;

COMMENT ON VIEW public.v_agenda_operario IS
    'Agenda por local: estado "¿toca?" (sin_planificar/al_dia/pendiente/toca_hoy/atrasado) + día objetivo del ciclo vigente, anclado a semana ISO. security_invoker → RLS estricta (P2). "hoy" en empresa.zona_horaria. Spec §4.2.';

GRANT SELECT ON public.v_agenda_operario TO authenticated;
