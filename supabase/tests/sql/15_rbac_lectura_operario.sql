-- =============================================================================
-- Planificación P2 — RBAC de lectura estricto por operario.
--
-- Verifica que un TÉCNICO solo LEE sus locales asignados y todo lo que cuelga de
-- ellos; owner/admin/gestor/contable (ve-todo) ven todo. Cubre: inventario
-- (local/instalacion/maquina/licencia), recaudación, cambio_placa, lectura,
-- deudas (credito_local/recuperacion), avería (incl. snapshot local_id NULL), y
-- que REASIGNAR un local transfiere la visibilidad.
--
-- pgTAP corre como superusuario (salta RLS). Para que la RLS se aplique en las
-- aserciones se hace `SET LOCAL ROLE authenticated` (rol normal, sin BYPASSRLS)
-- + `SET LOCAL request.jwt.claims` por usuario. El SETUP (INSERTs) va ANTES, como
-- superusuario (las escrituras directas están revocadas para authenticated).
-- Namespace de UUID: c1807… (P2). BEGIN..ROLLBACK, sin depender de seed.sql.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(23);

-- --- SETUP (como superusuario) -----------------------------------------------
INSERT INTO auth.users (id) VALUES
    ('c1807000-0000-0000-0000-0000000000a1'),   -- owner (ve-todo)
    ('c1807000-0000-0000-0000-0000000000a2'),   -- tecA (técnico de L1)
    ('c1807000-0000-0000-0000-0000000000a3'),   -- tecB (técnico de L2)
    ('c1807000-0000-0000-0000-0000000000a4');   -- contable (ve-todo financiero)

INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin)
    VALUES ('c1807000-0000-0000-0000-000000000001', 'Test Empresa P2', now(), now() + interval '30 days');

INSERT INTO public.usuario (id, nombre_completo) VALUES
    ('c1807000-0000-0000-0000-0000000000a1', 'Owner'),
    ('c1807000-0000-0000-0000-0000000000a2', 'Tecnico A'),
    ('c1807000-0000-0000-0000-0000000000a3', 'Tecnico B'),
    ('c1807000-0000-0000-0000-0000000000a4', 'Contable');

INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('c1807000-0000-0000-0000-000000000001', 'c1807000-0000-0000-0000-0000000000a1', 'owner',    true),
    ('c1807000-0000-0000-0000-000000000001', 'c1807000-0000-0000-0000-0000000000a2', 'tecnico',  true),
    ('c1807000-0000-0000-0000-000000000001', 'c1807000-0000-0000-0000-0000000000a3', 'tecnico',  true),
    ('c1807000-0000-0000-0000-000000000001', 'c1807000-0000-0000-0000-0000000000a4', 'contable', true);

INSERT INTO public.licencia (id, empresa_id, numero) VALUES
    ('c1807000-0000-0000-0000-000000000051', 'c1807000-0000-0000-0000-000000000001', 'LIC-A'),
    ('c1807000-0000-0000-0000-000000000052', 'c1807000-0000-0000-0000-000000000001', 'LIC-B');

INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial) VALUES
    ('c1807000-0000-0000-0000-000000000041', 'c1807000-0000-0000-0000-000000000001', 'M-A', 0.20, 1000, 400),
    ('c1807000-0000-0000-0000-000000000042', 'c1807000-0000-0000-0000-000000000001', 'M-B', 0.20, 1000, 400);

-- L1 → tecA, L2 → tecB, L3 → sin operario.
INSERT INTO public.local (id, empresa_id, nombre, operario_id) VALUES
    ('c1807000-0000-0000-0000-000000000031', 'c1807000-0000-0000-0000-000000000001', 'Bar L1', 'c1807000-0000-0000-0000-0000000000a2'),
    ('c1807000-0000-0000-0000-000000000032', 'c1807000-0000-0000-0000-000000000001', 'Bar L2', 'c1807000-0000-0000-0000-0000000000a3'),
    ('c1807000-0000-0000-0000-000000000033', 'c1807000-0000-0000-0000-000000000001', 'Bar L3', NULL);

-- Instalaciones (contador_*_base los rellena el trigger desde la máquina).
INSERT INTO public.instalacion (id, empresa_id, maquina_id, licencia_id, local_id,
                                fecha_inicio, tasa_semanal, porcentaje_local, estado) VALUES
    ('c1807000-0000-0000-0000-000000000061', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000041', 'c1807000-0000-0000-0000-000000000051',
     'c1807000-0000-0000-0000-000000000031', '2026-06-01', 50.00, 50.00, 'activa'),
    ('c1807000-0000-0000-0000-000000000062', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000042', 'c1807000-0000-0000-0000-000000000052',
     'c1807000-0000-0000-0000-000000000032', '2026-06-01', 50.00, 50.00, 'activa');

-- Recaudaciones firmes (bruta=100, tasa_total=0 → neta=100; partes 50/50).
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, recuperado_total, idempotency_key, baseline_origen) VALUES
    ('c1807000-0000-0000-0000-000000000071', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000061', 'c1807000-0000-0000-0000-0000000000a2', now(),
     1000, 400, 1500, 600, 0.20, 100.00, 1, 0.00, 0.00, 100.00, 50.00, 50.00, 50.00,
     '[{"denominacion": 2, "cantidad": 50}]'::jsonb, '[{"denominacion": 2, "cantidad": 25}]'::jsonb,
     0.00, 'idem-p2-recA', 'instalacion_base'),
    ('c1807000-0000-0000-0000-000000000072', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000062', 'c1807000-0000-0000-0000-0000000000a3', now(),
     1000, 400, 1500, 600, 0.20, 100.00, 1, 0.00, 0.00, 100.00, 50.00, 50.00, 50.00,
     '[{"denominacion": 2, "cantidad": 50}]'::jsonb, '[{"denominacion": 2, "cantidad": 25}]'::jsonb,
     0.00, 'idem-p2-recB', 'instalacion_base');

-- cambio_placa + lectura_no_recaudada de L1 (cuelgan de instA).
INSERT INTO public.cambio_placa (id, empresa_id, instalacion_id, fecha, usuario_id) VALUES
    ('c1807000-0000-0000-0000-000000000081', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000061', now(), 'c1807000-0000-0000-0000-0000000000a2');

INSERT INTO public.lectura_no_recaudada (id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_actual, contador_salidas_actual, bruto_estimado, tasa_estimada) VALUES
    ('c1807000-0000-0000-0000-000000000091', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000061', 'c1807000-0000-0000-0000-0000000000a2', now(),
     1100, 450, 10.00, 50.00);

-- Deuda de tolva de L1 + un abono.
INSERT INTO public.credito_local (id, empresa_id, local_id, tipo, instalacion_id, principal, fecha, estado) VALUES
    ('c1807000-0000-0000-0000-0000000000b1', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000031', 'tolva', 'c1807000-0000-0000-0000-000000000061', 100.00, '2026-06-01', 'abierto');
INSERT INTO public.recuperacion (id, empresa_id, local_id, credito_id, origen, importe) VALUES
    ('c1807000-0000-0000-0000-0000000000b2', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000031', 'c1807000-0000-0000-0000-0000000000b1', 'efectivo', 20.00);

-- Averías: avA (snapshot L1), avB (snapshot L2), avNull (en almacén, local_id NULL).
INSERT INTO public.averia (id, empresa_id, maquina_id, local_id, categoria) VALUES
    ('c1807000-0000-0000-0000-0000000000c1', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000041', 'c1807000-0000-0000-0000-000000000031', 'error'),
    ('c1807000-0000-0000-0000-0000000000c2', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000042', 'c1807000-0000-0000-0000-000000000032', 'error'),
    ('c1807000-0000-0000-0000-0000000000c3', 'c1807000-0000-0000-0000-000000000001',
     'c1807000-0000-0000-0000-000000000041', NULL, 'error');

-- A partir de aquí la RLS debe aplicarse: bajamos a rol authenticated.
SET LOCAL ROLE authenticated;

-- --- tecA (técnico de L1): SOLO ve L1 y su cascada ---------------------------
SET LOCAL request.jwt.claims = '{"sub":"c1807000-0000-0000-0000-0000000000a2","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.local),                1::bigint, 'tecA ve 1 local');
SELECT is((SELECT id FROM public.local),
          'c1807000-0000-0000-0000-000000000031'::uuid,                  'tecA: el local que ve es L1');
SELECT is((SELECT count(*) FROM public.instalacion),          1::bigint, 'tecA ve 1 instalacion');
SELECT is((SELECT count(*) FROM public.maquina),              1::bigint, 'tecA ve 1 maquina (la de L1)');
SELECT is((SELECT count(*) FROM public.licencia),             1::bigint, 'tecA ve 1 licencia');
SELECT is((SELECT count(*) FROM public.recaudacion),          1::bigint, 'tecA ve 1 recaudacion');
SELECT is((SELECT count(*) FROM public.cambio_placa),         1::bigint, 'tecA ve 1 cambio_placa');
SELECT is((SELECT count(*) FROM public.lectura_no_recaudada), 1::bigint, 'tecA ve 1 lectura_no_recaudada');
SELECT is((SELECT count(*) FROM public.credito_local),        1::bigint, 'tecA ve 1 credito_local');
SELECT is((SELECT count(*) FROM public.recuperacion),         1::bigint, 'tecA ve 1 recuperacion');
SELECT is((SELECT count(*) FROM public.averia),               1::bigint, 'tecA ve 1 averia (snapshot L1)');
SELECT is((SELECT count(*) FROM public.maquina
            WHERE id = 'c1807000-0000-0000-0000-000000000042'), 0::bigint, 'tecA NO ve la maquina de L2');

-- --- owner (ve-todo): ve los 3 locales y las 3 averías -----------------------
SET LOCAL request.jwt.claims = '{"sub":"c1807000-0000-0000-0000-0000000000a1","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.local),  3::bigint, 'owner ve los 3 locales');
SELECT is((SELECT count(*) FROM public.averia), 3::bigint, 'owner ve las 3 averias (incl. local_id NULL)');
SELECT is((SELECT count(*) FROM public.recaudacion), 2::bigint, 'owner ve las 2 recaudaciones');

-- --- contable (ve-todo financiero) -------------------------------------------
SET LOCAL request.jwt.claims = '{"sub":"c1807000-0000-0000-0000-0000000000a4","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.recaudacion),   2::bigint, 'contable ve las 2 recaudaciones');
SELECT is((SELECT count(*) FROM public.credito_local), 1::bigint, 'contable ve la deuda');

-- --- tecB antes de reasignar: ve L2, NO ve L3 (sin operario) -----------------
SET LOCAL request.jwt.claims = '{"sub":"c1807000-0000-0000-0000-0000000000a3","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.local), 1::bigint, 'tecB ve 1 local (L2)');
SELECT is((SELECT count(*) FROM public.local
            WHERE id = 'c1807000-0000-0000-0000-000000000033'), 0::bigint, 'L3 sin operario: tecB no lo ve');

-- --- Reasignar L1 a tecB (como owner, vía RPC) transfiere visibilidad --------
SET LOCAL request.jwt.claims = '{"sub":"c1807000-0000-0000-0000-0000000000a1","role":"authenticated"}';
SELECT public.actualizar_calendario_local(
    'c1807000-0000-0000-0000-000000000031', NULL, NULL, 'c1807000-0000-0000-0000-0000000000a3');

-- tecA ya NO ve L1.
SET LOCAL request.jwt.claims = '{"sub":"c1807000-0000-0000-0000-0000000000a2","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.local),       0::bigint, 'tras reasignar, tecA no ve ningun local');
SELECT is((SELECT count(*) FROM public.recaudacion), 0::bigint, 'tras reasignar, tecA no ve la recaudacion de L1');

-- tecB ahora ve L1 (2 locales) y su recaudación.
SET LOCAL request.jwt.claims = '{"sub":"c1807000-0000-0000-0000-0000000000a3","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.local), 2::bigint, 'tras reasignar, tecB ve 2 locales (L1+L2)');
SELECT is((SELECT count(*) FROM public.recaudacion
            WHERE instalacion_id = 'c1807000-0000-0000-0000-000000000061'),
          1::bigint, 'tras reasignar, tecB ve la recaudacion de L1');

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;
