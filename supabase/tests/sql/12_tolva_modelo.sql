-- =============================================================================
-- T-223 — Tests del modelo de tolva por avería (tolva_movimiento + tolva
-- efectiva + crear_averia con tolva + saldar_tolva_pendiente).
--
-- Cubre:
--   * crear_averia con afecta_tolva: fija columnas e inserta la MERMA atómica.
--   * v_instalacion_tolva: teórica / merma / repuesto / efectiva / pendiente.
--   * Rechazos: tolva sin instalación; importe ≤ 0.
--   * saldar_tolva_pendiente (admin): repone lo pendiente; vuelve a la teórica;
--     re-saldar sin pendiente y saldar como no-admin se rechazan.
--   * Constraints: chk_averia_tolva_inst y chk_tolva_mov_origen.
--
-- Las RPCs son SECURITY DEFINER y validan rol vía auth.uid(); simulamos el JWT
-- con `SET LOCAL request.jwt.claims`. BEGIN..ROLLBACK, sin depender de seed.sql.
-- UUID con namespace `c1223…` (T-223) para no colisionar con datos sembrados.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(21);

-- --- Datos mínimos -----------------------------------------------------------
INSERT INTO auth.users (id) VALUES
    ('c1223000-0000-0000-0000-0000000000a1'),   -- owner (admin)
    ('c1223000-0000-0000-0000-0000000000a2');    -- tecnico (operativo, no admin)
INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin)
    VALUES ('c1223000-0000-0000-0000-000000000001', 'Test Empresa T-223',
            now(), now() + interval '30 days');
INSERT INTO public.usuario (id, nombre_completo) VALUES
    ('c1223000-0000-0000-0000-0000000000a1', 'Test Owner'),
    ('c1223000-0000-0000-0000-0000000000a2', 'Test Tecnico');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol) VALUES
    ('c1223000-0000-0000-0000-000000000001', 'c1223000-0000-0000-0000-0000000000a1', 'owner'),
    ('c1223000-0000-0000-0000-000000000001', 'c1223000-0000-0000-0000-0000000000a2', 'tecnico');

INSERT INTO public.licencia (id, empresa_id, numero) VALUES
    ('c1223000-0000-0000-0000-000000000010', 'c1223000-0000-0000-0000-000000000001', 'LIC-1');
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial) VALUES
    ('c1223000-0000-0000-0000-000000000020', 'c1223000-0000-0000-0000-000000000001', 'M-1', 0.20, 1000, 400),
    ('c1223000-0000-0000-0000-000000000021', 'c1223000-0000-0000-0000-000000000001', 'M-2', 0.20, 1000, 400);
INSERT INTO public.local (id, empresa_id, nombre)
    VALUES ('c1223000-0000-0000-0000-000000000030', 'c1223000-0000-0000-0000-000000000001', 'Bar Test');

-- Simula el JWT del owner para que auth.uid() lo reconozca en las RPCs.
SET LOCAL request.jwt.claims = '{"sub":"c1223000-0000-0000-0000-0000000000a1","role":"authenticated"}';

CREATE TEMP TABLE _ids(k text PRIMARY KEY, id uuid) ON COMMIT DROP;

-- M-1 instalada con tolva TEÓRICA de 100,00 €.
INSERT INTO _ids(k, id) VALUES
    ('inst1', public.crear_instalacion(
        'c1223000-0000-0000-0000-000000000001',
        'c1223000-0000-0000-0000-000000000020',  -- M-1
        'c1223000-0000-0000-0000-000000000010',  -- LIC-1
        'c1223000-0000-0000-0000-000000000030',  -- local
        '2026-06-10', 12.00, 60.00, NULL, 100.00, NULL));

-- --- A. crear_averia con tolva: columnas + merma atómica ----------------------
INSERT INTO _ids(k, id) VALUES
    ('av1', public.crear_averia('c1223000-0000-0000-0000-000000000001',
                                'c1223000-0000-0000-0000-000000000020',  -- M-1
                                'falta_pago', 'premio no contabilizado', false, NULL,
                                true, 15.00));

SELECT is((SELECT afecta_tolva FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'av1')),
          true, 'crear_averia tolva: afecta_tolva = true');
SELECT is((SELECT importe_tolva FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'av1')),
          15.00::numeric, 'crear_averia tolva: importe_tolva = 15.00');
SELECT is((SELECT count(*) FROM public.tolva_movimiento
            WHERE averia_id = (SELECT id FROM _ids WHERE k = 'av1') AND tipo = 'merma'),
          1::bigint, 'crear_averia tolva: inserta 1 merma');
SELECT is((SELECT importe FROM public.tolva_movimiento
            WHERE averia_id = (SELECT id FROM _ids WHERE k = 'av1')),
          15.00::numeric, 'crear_averia tolva: merma importe = 15.00');

SELECT is((SELECT merma FROM public.v_instalacion_tolva
            WHERE instalacion_id = (SELECT id FROM _ids WHERE k = 'inst1')),
          15.00::numeric, 'v_instalacion_tolva: merma = 15');
SELECT is((SELECT efectiva FROM public.v_instalacion_tolva
            WHERE instalacion_id = (SELECT id FROM _ids WHERE k = 'inst1')),
          85.00::numeric, 'v_instalacion_tolva: efectiva = teórica − merma = 85');
SELECT is((SELECT pendiente FROM public.v_instalacion_tolva
            WHERE instalacion_id = (SELECT id FROM _ids WHERE k = 'inst1')),
          15.00::numeric, 'v_instalacion_tolva: pendiente = 15');

-- --- B. crear_averia sin tolva: ni merma ni importe ---------------------------
INSERT INTO _ids(k, id) VALUES
    ('av2', public.crear_averia('c1223000-0000-0000-0000-000000000001',
                                'c1223000-0000-0000-0000-000000000020',
                                'error', NULL, false, NULL, false, 0));
SELECT is((SELECT count(*) FROM public.tolva_movimiento
            WHERE averia_id = (SELECT id FROM _ids WHERE k = 'av2')),
          0::bigint, 'avería sin tolva: no genera movimiento');
SELECT is((SELECT importe_tolva FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'av2')),
          0::numeric, 'avería sin tolva: importe_tolva = 0');

-- --- C. tolva en máquina NO instalada: rechazo --------------------------------
SELECT throws_ok(
    $$ SELECT public.crear_averia('c1223000-0000-0000-0000-000000000001',
                                  'c1223000-0000-0000-0000-000000000021',  -- M-2 en almacén
                                  'falta_pago', NULL, false, NULL, true, 10.00) $$,
    '22023', NULL, 'tolva en máquina no instalada lanza error');

-- --- D. tolva con importe 0: rechazo ------------------------------------------
SELECT throws_ok(
    $$ SELECT public.crear_averia('c1223000-0000-0000-0000-000000000001',
                                  'c1223000-0000-0000-0000-000000000020',
                                  'falta_pago', NULL, false, NULL, true, 0) $$,
    '22023', NULL, 'tolva con importe 0 lanza error');

-- --- E. saldar_tolva_pendiente (admin): repone lo pendiente -------------------
SELECT lives_ok(
    format($$ SELECT public.saldar_tolva_pendiente(%L, 'baja sin recaudación futura') $$,
           (SELECT id FROM _ids WHERE k = 'inst1')),
    'saldar_tolva_pendiente ejecuta (admin)');
SELECT is((SELECT pendiente FROM public.v_instalacion_tolva
            WHERE instalacion_id = (SELECT id FROM _ids WHERE k = 'inst1')),
          0::numeric, 'tras saldar: pendiente = 0');
SELECT is((SELECT efectiva FROM public.v_instalacion_tolva
            WHERE instalacion_id = (SELECT id FROM _ids WHERE k = 'inst1')),
          100.00::numeric, 'tras saldar: efectiva vuelve a la teórica (100)');
SELECT is((SELECT repuesto FROM public.v_instalacion_tolva
            WHERE instalacion_id = (SELECT id FROM _ids WHERE k = 'inst1')),
          15.00::numeric, 'tras saldar: repuesto = 15');
SELECT is((SELECT count(*) FROM public.tolva_movimiento
            WHERE instalacion_id = (SELECT id FROM _ids WHERE k = 'inst1') AND tipo = 'reposicion'),
          1::bigint, 'tras saldar: 1 reposición en el ledger');

-- --- F. re-saldar sin pendiente: rechazo --------------------------------------
SELECT throws_ok(
    format($$ SELECT public.saldar_tolva_pendiente(%L) $$, (SELECT id FROM _ids WHERE k = 'inst1')),
    '22023', NULL, 'saldar sin merma pendiente lanza error');

-- --- G. constraint: afecta_tolva exige instalación (snapshot) -----------------
SELECT throws_ok(
    $$ INSERT INTO public.averia (empresa_id, maquina_id, categoria, estado, afecta_tolva, importe_tolva, instalacion_id)
       VALUES ('c1223000-0000-0000-0000-000000000001','c1223000-0000-0000-0000-000000000020',
               'otro','abierta', true, 10.00, NULL) $$,
    '23514', NULL, 'chk_averia_tolva_inst: afecta_tolva sin instalación lanza check_violation');

-- --- H. constraint: coherencia del ledger (merma↔avería, reposición↔no-avería) -
SELECT throws_ok(
    format($$ INSERT INTO public.tolva_movimiento (empresa_id, instalacion_id, tipo, importe, averia_id)
              VALUES ('c1223000-0000-0000-0000-000000000001', %L, 'merma', 5.00, NULL) $$,
           (SELECT id FROM _ids WHERE k = 'inst1')),
    '23514', NULL, 'chk_tolva_mov_origen: merma sin avería lanza check_violation');
SELECT throws_ok(
    format($$ INSERT INTO public.tolva_movimiento (empresa_id, instalacion_id, tipo, importe, averia_id)
              VALUES ('c1223000-0000-0000-0000-000000000001', %L, 'reposicion', 5.00, %L) $$,
           (SELECT id FROM _ids WHERE k = 'inst1'), (SELECT id FROM _ids WHERE k = 'av1')),
    '23514', NULL, 'chk_tolva_mov_origen: reposición con avería lanza check_violation');

-- --- I. permisos: un técnico (no admin) no puede saldar -----------------------
SET LOCAL request.jwt.claims = '{"sub":"c1223000-0000-0000-0000-0000000000a2","role":"authenticated"}';
SELECT throws_ok(
    format($$ SELECT public.saldar_tolva_pendiente(%L) $$, (SELECT id FROM _ids WHERE k = 'inst1')),
    '42501', NULL, 'un técnico no puede saldar la tolva (requiere admin)');

SELECT * FROM finish();
ROLLBACK;
