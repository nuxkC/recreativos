-- =============================================================================
-- T-16 — Vistas operativas y agregados.
--
-- Todas las vistas usan `security_invoker = true` para heredar la RLS del
-- usuario que consulta (Postgres 15+). Así no hay riesgo de filtración a
-- través de una vista que ignore el contexto del caller.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- v_instalacion_actual: instalación activa con joins a máquina, local,
-- licencia y baseline calculada en tiempo real.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW public.v_instalacion_actual
WITH (security_invoker = true) AS
SELECT
    i.id                       AS instalacion_id,
    i.empresa_id,
    i.maquina_id,
    i.licencia_id,
    i.local_id,
    i.fecha_inicio,
    i.tasa_semanal,
    i.porcentaje_local,
    i.contador_entradas_base,
    i.contador_salidas_base,
    i.estado,
    m.numero_serie            AS maquina_numero_serie,
    m.modelo                  AS maquina_modelo,
    m.fabricante              AS maquina_fabricante,
    m.valor_credito           AS maquina_valor_credito,
    l.nombre                  AS local_nombre,
    l.direccion               AS local_direccion,
    lic.numero                AS licencia_numero,
    lic.tipo                  AS licencia_tipo,
    lic.fecha_caducidad       AS licencia_fecha_caducidad,
    bl.entradas               AS baseline_entradas,
    bl.salidas                AS baseline_salidas,
    bl.fecha_referencia       AS baseline_fecha,
    bl.origen                 AS baseline_origen,
    bl.referencia_id          AS baseline_referencia_id
FROM public.instalacion i
JOIN public.maquina m ON m.id = i.maquina_id
JOIN public.local l ON l.id = i.local_id
JOIN public.licencia lic ON lic.id = i.licencia_id
LEFT JOIN LATERAL public.obtener_baseline(i.id, now()) AS bl ON true
WHERE i.estado = 'activa';

COMMENT ON VIEW public.v_instalacion_actual IS
    'Instalaciones activas con sus joins habituales y baseline pre-calculada.';

-- -----------------------------------------------------------------------------
-- v_recaudaciones_por_local_mes: agregados por local y mes (zona horaria
-- de la empresa) para informes y dashboards.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW public.v_recaudaciones_por_local_mes
WITH (security_invoker = true) AS
SELECT
    r.empresa_id,
    i.local_id,
    date_trunc('month', (r.fecha AT TIME ZONE e.zona_horaria)) AS mes_local,
    count(*)                            AS num_recaudaciones,
    sum(r.recaudacion_bruta)            AS bruto_total,
    sum(r.tasa_total_aplicada)          AS tasa_total,
    sum(r.recaudacion_neta)             AS neto_total,
    sum(r.parte_local)                  AS parte_local_total,
    sum(r.parte_empresa)                AS parte_empresa_total
FROM public.recaudacion r
JOIN public.instalacion i ON i.id = r.instalacion_id
JOIN public.empresa e ON e.id = r.empresa_id
WHERE r.estado = 'firme'
GROUP BY r.empresa_id, i.local_id, date_trunc('month', (r.fecha AT TIME ZONE e.zona_horaria));

COMMENT ON VIEW public.v_recaudaciones_por_local_mes IS
    'Suma de recaudaciones firme por (empresa, local, mes) en zona horaria de la empresa.';

-- -----------------------------------------------------------------------------
-- v_recaudaciones_por_maquina_mes: igual pero por máquina.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW public.v_recaudaciones_por_maquina_mes
WITH (security_invoker = true) AS
SELECT
    r.empresa_id,
    i.maquina_id,
    date_trunc('month', (r.fecha AT TIME ZONE e.zona_horaria)) AS mes_local,
    count(*)                            AS num_recaudaciones,
    sum(r.recaudacion_bruta)            AS bruto_total,
    sum(r.tasa_total_aplicada)          AS tasa_total,
    sum(r.recaudacion_neta)             AS neto_total,
    sum(r.parte_local)                  AS parte_local_total,
    sum(r.parte_empresa)                AS parte_empresa_total
FROM public.recaudacion r
JOIN public.instalacion i ON i.id = r.instalacion_id
JOIN public.empresa e ON e.id = r.empresa_id
WHERE r.estado = 'firme'
GROUP BY r.empresa_id, i.maquina_id, date_trunc('month', (r.fecha AT TIME ZONE e.zona_horaria));

COMMENT ON VIEW public.v_recaudaciones_por_maquina_mes IS
    'Suma de recaudaciones firme por (empresa, maquina, mes) en zona horaria de la empresa.';

-- -----------------------------------------------------------------------------
-- v_alertas_pendientes
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW public.v_alertas_pendientes
WITH (security_invoker = true) AS
SELECT
    a.id,
    a.empresa_id,
    a.tipo,
    a.referencia_id,
    a.mensaje,
    a.destinatario_usuario_id,
    a.creada_en
FROM public.alerta a
WHERE a.leida = false;

COMMENT ON VIEW public.v_alertas_pendientes IS
    'Alertas no leídas de cualquier empresa accesible al usuario actual.';
