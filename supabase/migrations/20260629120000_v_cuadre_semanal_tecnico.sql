-- =============================================================================
-- v_cuadre_semanal_tecnico — caja semanal del técnico.
--
-- Por (semana ISO en TZ de la empresa, denominación), el efectivo de la empresa
-- que el técnico se llevó = Σ(desglose_total) − Σ(desglose_local) de sus
-- recaudaciones FIRMES (estado='firme'). No recalcula dinero: agrega lo ya
-- persistido. security_invoker -> respeta la RLS estricta (P2); además filtra
-- tecnico_id = auth.uid() para que sea la caja PROPIA del técnico.
-- =============================================================================

CREATE OR REPLACE VIEW public.v_cuadre_semanal_tecnico
WITH (security_invoker = true) AS
WITH piezas AS (
    -- Total con signo +, local con signo − (mismo grano: pieza por denominación).
    SELECT
        r.empresa_id,
        r.tecnico_id,
        date_trunc('week', r.fecha AT TIME ZONE e.zona_horaria)::date AS semana_inicio,
        r.id AS recaudacion_id,
        (d.item->>'denominacion')::numeric(5, 2) AS denominacion,
        (d.item->>'cantidad')::bigint            AS cantidad
    FROM public.recaudacion r
    JOIN public.empresa e ON e.id = r.empresa_id
    CROSS JOIN LATERAL jsonb_array_elements(r.desglose_total) AS d(item)
    WHERE r.estado = 'firme' AND r.tecnico_id = (SELECT auth.uid())
    UNION ALL
    SELECT
        r.empresa_id,
        r.tecnico_id,
        date_trunc('week', r.fecha AT TIME ZONE e.zona_horaria)::date AS semana_inicio,
        r.id AS recaudacion_id,
        (d.item->>'denominacion')::numeric(5, 2)  AS denominacion,
        -((d.item->>'cantidad')::bigint)          AS cantidad
    FROM public.recaudacion r
    JOIN public.empresa e ON e.id = r.empresa_id
    CROSS JOIN LATERAL jsonb_array_elements(r.desglose_local) AS d(item)
    WHERE r.estado = 'firme' AND r.tecnico_id = (SELECT auth.uid())
),
neto AS (
    SELECT empresa_id, tecnico_id, semana_inicio, denominacion,
           SUM(cantidad)::bigint AS cantidad_neta
    FROM piezas
    GROUP BY empresa_id, tecnico_id, semana_inicio, denominacion
),
conteo AS (
    SELECT r.empresa_id, r.tecnico_id,
           date_trunc('week', r.fecha AT TIME ZONE e.zona_horaria)::date AS semana_inicio,
           COUNT(*)::bigint AS num_recaudaciones
    FROM public.recaudacion r
    JOIN public.empresa e ON e.id = r.empresa_id
    WHERE r.estado = 'firme' AND r.tecnico_id = (SELECT auth.uid())
    GROUP BY r.empresa_id, r.tecnico_id, date_trunc('week', r.fecha AT TIME ZONE e.zona_horaria)::date
)
SELECT
    n.empresa_id,
    n.tecnico_id,
    n.semana_inicio,
    n.denominacion,
    n.cantidad_neta,
    (n.denominacion * n.cantidad_neta)::numeric(10, 2) AS importe_neto,
    c.num_recaudaciones
FROM neto n
JOIN conteo c USING (empresa_id, tecnico_id, semana_inicio);

COMMENT ON VIEW public.v_cuadre_semanal_tecnico IS
    'Caja semanal del técnico: neto llevado Σ(desglose_total−desglose_local) por (semana ISO en TZ empresa, denominación) de sus recaudaciones firmes. security_invoker + tecnico_id=auth.uid().';

GRANT SELECT ON public.v_cuadre_semanal_tecnico TO authenticated;
