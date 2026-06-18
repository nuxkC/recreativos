-- =============================================================================
-- Histórico v2 — vista v_recaudacion_historica (spec §6.5).
--
-- Verifica: (a) el RBAC estricto fluye por la vista (security_invoker) — un
-- técnico solo ve el histórico de sus locales asignados, owner ve todo;
-- (b) la vista es filtrable por local_id / maquina_id; (c) local/máquina se
-- derivan del SNAPSHOT INMUTABLE (instalacion del recaudo), no del estado
-- actual; (d) al reasignar el local, la visibilidad cambia pero el local
-- derivado de la fila histórica NO.
--
-- Setup como superusuario; las aserciones bajan a rol authenticated + jwt para
-- que la RLS aplique. Namespace de UUID: d1709…. BEGIN..ROLLBACK, sin seed.sql.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(10);

-- --- SETUP (superusuario) ----------------------------------------------------
INSERT INTO auth.users (id) VALUES
    ('d1709000-0000-0000-0000-0000000000a1'),   -- owner (ve-todo)
    ('d1709000-0000-0000-0000-0000000000a2'),   -- tecA
    ('d1709000-0000-0000-0000-0000000000a3');   -- tecB

INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('d1709000-0000-0000-0000-000000000001', 'Test Empresa Histórico', 'UTC', now(), now() + interval '30 days');

INSERT INTO public.usuario (id, nombre_completo) VALUES
    ('d1709000-0000-0000-0000-0000000000a1', 'Owner'),
    ('d1709000-0000-0000-0000-0000000000a2', 'Tecnico A'),
    ('d1709000-0000-0000-0000-0000000000a3', 'Tecnico B');

INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('d1709000-0000-0000-0000-000000000001', 'd1709000-0000-0000-0000-0000000000a1', 'owner',   true),
    ('d1709000-0000-0000-0000-000000000001', 'd1709000-0000-0000-0000-0000000000a2', 'tecnico', true),
    ('d1709000-0000-0000-0000-000000000001', 'd1709000-0000-0000-0000-0000000000a3', 'tecnico', true);

-- L1 -> tecA, L2 -> tecB.
INSERT INTO public.local (id, empresa_id, nombre, operario_id) VALUES
    ('d1709000-0000-0000-0000-000000000031', 'd1709000-0000-0000-0000-000000000001', 'Local 1', 'd1709000-0000-0000-0000-0000000000a2'),
    ('d1709000-0000-0000-0000-000000000032', 'd1709000-0000-0000-0000-000000000001', 'Local 2', 'd1709000-0000-0000-0000-0000000000a3');

INSERT INTO public.licencia (id, empresa_id, numero) VALUES
    ('d1709000-0000-0000-0000-000000000051', 'd1709000-0000-0000-0000-000000000001', 'LIC-1'),
    ('d1709000-0000-0000-0000-000000000052', 'd1709000-0000-0000-0000-000000000001', 'LIC-2');

INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial) VALUES
    ('d1709000-0000-0000-0000-000000000041', 'd1709000-0000-0000-0000-000000000001', 'M-1', 0.20, 1000, 400),
    ('d1709000-0000-0000-0000-000000000042', 'd1709000-0000-0000-0000-000000000001', 'M-2', 0.20, 1000, 400);

INSERT INTO public.instalacion (id, empresa_id, maquina_id, licencia_id, local_id,
                                fecha_inicio, tasa_semanal, porcentaje_local, estado) VALUES
    ('d1709000-0000-0000-0000-000000000061', 'd1709000-0000-0000-0000-000000000001',
     'd1709000-0000-0000-0000-000000000041', 'd1709000-0000-0000-0000-000000000051',
     'd1709000-0000-0000-0000-000000000031', '2026-06-01', 50.00, 50.00, 'activa'),
    ('d1709000-0000-0000-0000-000000000062', 'd1709000-0000-0000-0000-000000000001',
     'd1709000-0000-0000-0000-000000000042', 'd1709000-0000-0000-0000-000000000052',
     'd1709000-0000-0000-0000-000000000032', '2026-06-01', 50.00, 50.00, 'activa');

-- R1 en L1 (la hizo tecA), R2 en L2 (la hizo tecB). Importes money-valid.
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, recuperado_total, idempotency_key, baseline_origen) VALUES
    ('d1709000-0000-0000-0000-000000000071', 'd1709000-0000-0000-0000-000000000001',
     'd1709000-0000-0000-0000-000000000061', 'd1709000-0000-0000-0000-0000000000a2', now(),
     1000, 400, 1500, 600, 0.20, 100.00, 1, 0.00, 0.00, 100.00, 50.00, 50.00, 50.00,
     '[{"denominacion": 2, "cantidad": 50}]'::jsonb, '[{"denominacion": 2, "cantidad": 25}]'::jsonb,
     0.00, 'hist-test-r1', 'instalacion_base'),
    ('d1709000-0000-0000-0000-000000000072', 'd1709000-0000-0000-0000-000000000001',
     'd1709000-0000-0000-0000-000000000062', 'd1709000-0000-0000-0000-0000000000a3', now(),
     1000, 400, 1500, 600, 0.20, 100.00, 1, 0.00, 0.00, 100.00, 50.00, 50.00, 50.00,
     '[{"denominacion": 2, "cantidad": 50}]'::jsonb, '[{"denominacion": 2, "cantidad": 25}]'::jsonb,
     0.00, 'hist-test-r2', 'instalacion_base');

-- --- tecA: solo ve el histórico de SU local (L1) ------------------------------
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claims" TO '{"sub": "d1709000-0000-0000-0000-0000000000a2", "role": "authenticated"}';

SELECT is(
    (SELECT count(*)::int FROM public.v_recaudacion_historica),
    1,
    'tecA ve exactamente 1 recaudación (la de su local L1)');

SELECT is(
    (SELECT local_id FROM public.v_recaudacion_historica),
    'd1709000-0000-0000-0000-000000000031'::uuid,
    'tecA: la fila deriva local_id = L1 (del snapshot de la instalación)');

SELECT is(
    (SELECT maquina_id FROM public.v_recaudacion_historica),
    'd1709000-0000-0000-0000-000000000041'::uuid,
    'tecA: la fila deriva maquina_id = M1');

SELECT is(
    (SELECT count(*)::int FROM public.v_recaudacion_historica
      WHERE local_id = 'd1709000-0000-0000-0000-000000000032'),
    0,
    'tecA: filtrar por el local de tecB (L2) no devuelve nada (RLS)');

SELECT is(
    (SELECT count(*)::int FROM public.v_recaudacion_historica
      WHERE maquina_id = 'd1709000-0000-0000-0000-000000000041'),
    1,
    'tecA: filtrar por su máquina (M1) devuelve su recaudación');

-- --- owner: ve TODO el histórico de la empresa --------------------------------
SET LOCAL "request.jwt.claims" TO '{"sub": "d1709000-0000-0000-0000-0000000000a1", "role": "authenticated"}';

SELECT is(
    (SELECT count(*)::int FROM public.v_recaudacion_historica),
    2,
    'owner ve las 2 recaudaciones de la empresa');

SELECT is(
    (SELECT count(*)::int FROM public.v_recaudacion_historica
      WHERE local_id = 'd1709000-0000-0000-0000-000000000032'),
    1,
    'owner: filtrar por L2 devuelve la recaudación de ese local');

-- --- Reasignar L1 a tecB: la visibilidad cambia, el snapshot histórico NO -----
RESET ROLE;
UPDATE public.local SET operario_id = 'd1709000-0000-0000-0000-0000000000a3'
    WHERE id = 'd1709000-0000-0000-0000-000000000031';

SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claims" TO '{"sub": "d1709000-0000-0000-0000-0000000000a2", "role": "authenticated"}';
SELECT is(
    (SELECT count(*)::int FROM public.v_recaudacion_historica),
    0,
    'tras reasignar L1, tecA ya no ve su histórico (RBAC reactivo)');

SET LOCAL "request.jwt.claims" TO '{"sub": "d1709000-0000-0000-0000-0000000000a3", "role": "authenticated"}';
SELECT is(
    (SELECT count(*)::int FROM public.v_recaudacion_historica),
    2,
    'tecB ahora ve L1 (reasignado) y L2: 2 recaudaciones');

SELECT is(
    (SELECT local_id FROM public.v_recaudacion_historica
      WHERE id = 'd1709000-0000-0000-0000-000000000071'),
    'd1709000-0000-0000-0000-000000000031'::uuid,
    'la fila histórica de R1 sigue derivando L1 (inmutable), pese a la reasignación');

SELECT * FROM finish();
ROLLBACK;
