-- =============================================================================
-- Planificación de recaudación — P3a: vista de agenda por local ("¿toca?").
--
-- Deriva, por local visible, su estado de agenda y su próxima fecha programada a
-- partir del calendario de P1 (cadencia + fecha de inicio). NO persiste nada y
-- NO cambia ninguna escritura ni cálculo de dinero.
--
-- security_invoker = true: la vista se evalúa con los permisos y la RLS del que
-- consulta, así la RLS estricta de P2 fluye sola (un técnico solo ve sus
-- locales; owner/admin/gestor/contable ven todo). El "hoy" y los límites de
-- ciclo se calculan en la zona horaria de la empresa (nunca la del dispositivo).
--
-- Regla (spec §4.2): para un local planificado con fecha de inicio F y cadencia
-- C semanas, S (fecha programada vigente) = F + floor((hoy−F)/(7C))·7C días.
-- "Atendido este ciclo" = recaudación firme o lectura_no_recaudada en CUALQUIER
-- instalación del local con fecha ∈ [S, hoy]. Estados: sin_planificar / al_dia
-- (hoy<F o atendido) / toca_hoy (S=hoy y no atendido) / atrasado (S<hoy y no
-- atendido).
-- =============================================================================

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
            -- Aún no ha empezado: la próxima programada es la propia F.
            WHEN b.hoy < b.fecha_inicio_recaudacion THEN b.fecha_inicio_recaudacion
            -- S = mayor fecha programada ≤ hoy (división entera = floor con hoy ≥ F).
            ELSE b.fecha_inicio_recaudacion
                 + (((b.hoy - b.fecha_inicio_recaudacion) / (7 * b.cadencia_semanas))
                    * (7 * b.cadencia_semanas))
        END AS fecha_programada_vigente
    FROM base b
)
SELECT
    c.local_id,
    c.empresa_id,
    c.nombre,
    c.operario_id,
    c.cadencia_semanas,
    c.fecha_inicio_recaudacion,
    c.fecha_programada_vigente,
    CASE
        WHEN c.cadencia_semanas IS NULL THEN 'sin_planificar'
        WHEN c.hoy < c.fecha_inicio_recaudacion THEN 'al_dia'
        WHEN EXISTS (
            SELECT 1
              FROM public.instalacion i
             WHERE i.local_id = c.local_id
               AND (
                   EXISTS (
                       SELECT 1 FROM public.recaudacion r
                        WHERE r.instalacion_id = i.id
                          AND r.estado = 'firme'
                          AND (r.fecha AT TIME ZONE c.tz)::date
                              BETWEEN c.fecha_programada_vigente AND c.hoy
                   )
                   OR EXISTS (
                       SELECT 1 FROM public.lectura_no_recaudada lnr
                        WHERE lnr.instalacion_id = i.id
                          AND (lnr.fecha AT TIME ZONE c.tz)::date
                              BETWEEN c.fecha_programada_vigente AND c.hoy
                   )
               )
        ) THEN 'al_dia'
        WHEN c.fecha_programada_vigente = c.hoy THEN 'toca_hoy'
        ELSE 'atrasado'
    END AS estado,
    (SELECT count(*) FROM public.instalacion i2
      WHERE i2.local_id = c.local_id AND i2.estado = 'activa') AS n_maquinas
FROM calc c;

COMMENT ON VIEW public.v_agenda_operario IS
    'Agenda por local: estado "¿toca?" (sin_planificar/al_dia/toca_hoy/atrasado) + fecha programada vigente, derivado del calendario (P1). security_invoker → respeta la RLS estricta (P2). "hoy" en empresa.zona_horaria. Spec §4.2.';

GRANT SELECT ON public.v_agenda_operario TO authenticated;
