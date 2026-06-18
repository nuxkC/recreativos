-- =============================================================================
-- Histórico v2 — vista de recaudaciones filtrable por local/máquina (spec §6.5).
--
-- El histórico (Android) leía la tabla `recaudacion` cruda y filtraba por
-- `tecnico_id` (solo "las mías"). v2 expone una vista que:
--   1) deriva el LOCAL y la MÁQUINA de cada fila del SNAPSHOT INMUTABLE de cuando
--      se hizo (vía recaudacion.instalacion_id -> instalacion.local_id/maquina_id),
--      aunque la máquina se haya movido de local después;
--   2) es filtrable por `local_id` / `maquina_id` desde el cliente (PostgREST);
--   3) aplica RBAC por rol vía security_invoker: la RLS estricta de P2
--      (recaudacion_select = usuario_ve_todo_empresa OR usuario_ve_instalacion)
--      fluye sola -> un técnico solo ve el histórico de sus locales asignados;
--      owner/admin/gestor/contable ven todo el de su empresa.
--
-- JOIN a instalacion SIN filtrar estado: las instalaciones cerradas también
-- cuelgan histórico y no deben perderse. Las FK NOT NULL de instalacion a
-- local/maquina/licencia garantizan que los INNER JOIN nunca pierden filas.
-- NO cambia ninguna escritura ni cálculo de dinero.
-- =============================================================================

CREATE OR REPLACE VIEW public.v_recaudacion_historica
WITH (security_invoker = true) AS
SELECT
    r.*,
    i.local_id,
    i.maquina_id,
    i.licencia_id,
    l.nombre         AS local_nombre,
    l.direccion      AS local_direccion,
    m.numero_serie   AS maquina_numero_serie,
    m.modelo         AS maquina_modelo,
    m.fabricante     AS maquina_fabricante,
    lic.numero       AS licencia_numero
FROM public.recaudacion r
JOIN public.instalacion i   ON i.id   = r.instalacion_id
JOIN public.local       l   ON l.id   = i.local_id
JOIN public.maquina     m   ON m.id   = i.maquina_id
JOIN public.licencia    lic ON lic.id = i.licencia_id;

COMMENT ON VIEW public.v_recaudacion_historica IS
    'Histórico de recaudaciones con local/máquina derivados del snapshot inmutable (instalacion_id). Filtrable por local_id/maquina_id. security_invoker -> respeta la RLS estricta (P2). Spec §6.5.';

GRANT SELECT ON public.v_recaudacion_historica TO authenticated;
