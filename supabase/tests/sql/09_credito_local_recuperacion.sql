-- =============================================================================
-- T-212 — Tests de tolva, préstamos y recuperación (credito_local + recuperacion).
--
-- Cubre:
--   * crear_prestamo: alta correcta, validación de principal.
--   * registrar_recuperacion_efectivo: abono parcial/total, saldo, saldado,
--     rechazo de importe > saldo y de abono sobre deuda no abierta.
--   * v_credito_local_saldo / v_local_saldo: saldo vivo y agregado por local
--     (excluye saldadas/condonadas).
--   * crear_instalacion con tolva: deuda = porcentaje_local × tolva, apuntada a
--     la instalación; y HOOK de traslado (p_tolva_continua_credito_id) que
--     re-apunta la MISMA tolva sin duplicarla.
--   * condonar_credito (admin) deja la deuda fuera del saldo.
--   * permisos: un no-miembro no puede crear deudas.
--
-- Las RPCs son SECURITY DEFINER y validan rol vía auth.uid(); simulamos el JWT
-- con `SET LOCAL request.jwt.claims`. Se envuelve en BEGIN..ROLLBACK y no se
-- depende de seed.sql. Los UUID usan el namespace `c1212…` (T-212) para no
-- colisionar con datos sembrados.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(26);

-- --- Datos mínimos -----------------------------------------------------------
INSERT INTO auth.users (id) VALUES ('c1212000-0000-0000-0000-0000000000a1');
-- estado_suscripcion='trial' (default) exige ventana trial válida (constraint
-- empresa_trial_ventana_check), así que la fijamos explícitamente.
INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin)
    VALUES ('c1212000-0000-0000-0000-000000000001', 'Test Empresa T-212',
            now(), now() + interval '30 days');
INSERT INTO public.usuario (id, nombre_completo)
    VALUES ('c1212000-0000-0000-0000-0000000000a1', 'Test Owner');
-- owner: satisface tanto usuario_es_gestor como usuario_es_admin.
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol)
    VALUES ('c1212000-0000-0000-0000-000000000001', 'c1212000-0000-0000-0000-0000000000a1', 'owner');

INSERT INTO public.licencia (id, empresa_id, numero) VALUES
    ('c1212000-0000-0000-0000-000000000010', 'c1212000-0000-0000-0000-000000000001', 'LIC-1'),
    ('c1212000-0000-0000-0000-000000000011', 'c1212000-0000-0000-0000-000000000001', 'LIC-2');
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial) VALUES
    ('c1212000-0000-0000-0000-000000000020', 'c1212000-0000-0000-0000-000000000001', 'M-1', 0.20, 1000, 400),
    ('c1212000-0000-0000-0000-000000000021', 'c1212000-0000-0000-0000-000000000001', 'M-2', 0.20, 1000, 400);
INSERT INTO public.local (id, empresa_id, nombre)
    VALUES ('c1212000-0000-0000-0000-000000000030', 'c1212000-0000-0000-0000-000000000001', 'Bar Test');

-- Simula el JWT del owner para que auth.uid() lo reconozca en las RPCs.
SET LOCAL request.jwt.claims = '{"sub":"c1212000-0000-0000-0000-0000000000a1","role":"authenticated"}';

-- Guardamos los ids que devuelven las RPCs para encadenar aserciones.
CREATE TEMP TABLE _ids(k text PRIMARY KEY, id uuid) ON COMMIT DROP;

-- --- A. crear_prestamo --------------------------------------------------------
INSERT INTO _ids(k, id) VALUES
    ('p1', public.crear_prestamo('c1212000-0000-0000-0000-000000000001',
                                 'c1212000-0000-0000-0000-000000000030', 100.00, 0, NULL, 'test'));

SELECT is(
    (SELECT tipo FROM public.credito_local WHERE id = (SELECT id FROM _ids WHERE k = 'p1')),
    'prestamo', 'crear_prestamo: tipo prestamo');
SELECT is(
    (SELECT principal FROM public.credito_local WHERE id = (SELECT id FROM _ids WHERE k = 'p1')),
    100.00::numeric, 'crear_prestamo: principal 100');
SELECT is(
    (SELECT estado FROM public.credito_local WHERE id = (SELECT id FROM _ids WHERE k = 'p1')),
    'abierto', 'crear_prestamo: estado abierto');
SELECT ok(
    (SELECT instalacion_id IS NULL FROM public.credito_local WHERE id = (SELECT id FROM _ids WHERE k = 'p1')),
    'crear_prestamo: prestamo no cuelga de instalacion (instalacion_id NULL)');

-- --- B. saldo inicial = principal --------------------------------------------
SELECT is(
    (SELECT saldo FROM public.v_credito_local_saldo WHERE credito_id = (SELECT id FROM _ids WHERE k = 'p1')),
    100.00::numeric, 'v_credito_local_saldo: saldo inicial = principal');

-- --- C. abono parcial --------------------------------------------------------
INSERT INTO _ids(k, id) VALUES
    ('r1', public.registrar_recuperacion_efectivo((SELECT id FROM _ids WHERE k = 'p1'), 30.00, 'parcial'));
SELECT is(
    (SELECT saldo FROM public.v_credito_local_saldo WHERE credito_id = (SELECT id FROM _ids WHERE k = 'p1')),
    70.00::numeric, 'abono parcial: saldo 70');
SELECT is(
    (SELECT estado FROM public.credito_local WHERE id = (SELECT id FROM _ids WHERE k = 'p1')),
    'abierto', 'abono parcial: la deuda sigue abierta');

-- --- D. abono que supera el saldo: rechazo -----------------------------------
SELECT throws_ok(
    format($$ SELECT public.registrar_recuperacion_efectivo(%L, 1000.00) $$,
           (SELECT id FROM _ids WHERE k = 'p1')),
    '23514', NULL, 'abono > saldo lanza check_violation');

-- --- E. abono del resto: saldado ---------------------------------------------
INSERT INTO _ids(k, id) VALUES
    ('r2', public.registrar_recuperacion_efectivo((SELECT id FROM _ids WHERE k = 'p1'), 70.00));
SELECT is(
    (SELECT saldo FROM public.v_credito_local_saldo WHERE credito_id = (SELECT id FROM _ids WHERE k = 'p1')),
    0.00::numeric, 'abono total: saldo 0');
SELECT is(
    (SELECT estado FROM public.credito_local WHERE id = (SELECT id FROM _ids WHERE k = 'p1')),
    'saldado', 'abono total: estado saldado');

-- --- F. abono sobre deuda no abierta: rechazo --------------------------------
SELECT throws_ok(
    format($$ SELECT public.registrar_recuperacion_efectivo(%L, 5.00) $$,
           (SELECT id FROM _ids WHERE k = 'p1')),
    '22023', NULL, 'abono sobre deuda saldada lanza error');

-- --- G. crear_prestamo con principal no positivo: rechazo --------------------
SELECT throws_ok(
    $$ SELECT public.crear_prestamo('c1212000-0000-0000-0000-000000000001',
                                    'c1212000-0000-0000-0000-000000000030', 0) $$,
    '22023', NULL, 'crear_prestamo con principal 0 lanza error');

-- --- G2. crear_prestamo sin concepto: rechazo (T-216) ------------------------
SELECT throws_ok(
    $$ SELECT public.crear_prestamo('c1212000-0000-0000-0000-000000000001',
                                    'c1212000-0000-0000-0000-000000000030', 25.00, 0, NULL, '   ') $$,
    '22023', NULL, 'crear_prestamo sin concepto (notas en blanco) lanza error');

-- --- H. crear_instalacion con tolva ------------------------------------------
-- 60% de 200 = 120.00 de deuda de tolva, apuntada a la instalación.
INSERT INTO _ids(k, id) VALUES
    ('inst1', public.crear_instalacion(
        'c1212000-0000-0000-0000-000000000001',
        'c1212000-0000-0000-0000-000000000020',  -- M-1
        'c1212000-0000-0000-0000-000000000010',  -- LIC-1
        'c1212000-0000-0000-0000-000000000030',  -- local
        '2026-06-10', 12.00, 60.00, NULL, 200.00, NULL));

SELECT is(
    (SELECT principal FROM public.credito_local
      WHERE local_id = 'c1212000-0000-0000-0000-000000000030' AND tipo = 'tolva'),
    120.00::numeric, 'tolva: principal = 60% x 200 = 120');
SELECT is(
    (SELECT instalacion_id FROM public.credito_local
      WHERE local_id = 'c1212000-0000-0000-0000-000000000030' AND tipo = 'tolva'),
    (SELECT id FROM _ids WHERE k = 'inst1'),
    'tolva: la deuda apunta a la instalacion creada');
SELECT is(
    (SELECT tolva FROM public.instalacion WHERE id = (SELECT id FROM _ids WHERE k = 'inst1')),
    200.00::numeric, 'instalacion.tolva = 200 (informativo)');

-- --- I. v_local_saldo agrega solo deudas abiertas ----------------------------
-- p1 está saldado → no cuenta. Solo la tolva (120) está abierta.
SELECT is(
    (SELECT saldo_total FROM public.v_local_saldo WHERE local_id = 'c1212000-0000-0000-0000-000000000030'),
    120.00::numeric, 'v_local_saldo: saldo_total 120 (prestamo saldado excluido)');
SELECT is(
    (SELECT saldo_tolva FROM public.v_local_saldo WHERE local_id = 'c1212000-0000-0000-0000-000000000030'),
    120.00::numeric, 'v_local_saldo: saldo_tolva 120');
SELECT is(
    (SELECT num_deudas_abiertas FROM public.v_local_saldo WHERE local_id = 'c1212000-0000-0000-0000-000000000030'),
    1::bigint, 'v_local_saldo: 1 deuda abierta');

-- --- J. traslado de tolva (cambio de máquina, misma tolva) -------------------
UPDATE public.instalacion SET estado = 'cerrada', fecha_fin = '2026-06-12'
    WHERE id = (SELECT id FROM _ids WHERE k = 'inst1');
INSERT INTO _ids(k, id) VALUES
    ('tolvacred', (SELECT id FROM public.credito_local
                    WHERE local_id = 'c1212000-0000-0000-0000-000000000030' AND tipo = 'tolva'));
-- Nueva instalación en otra máquina, indicando que la tolva CONTINÚA.
INSERT INTO _ids(k, id) VALUES
    ('inst2', public.crear_instalacion(
        'c1212000-0000-0000-0000-000000000001',
        'c1212000-0000-0000-0000-000000000021',  -- M-2
        'c1212000-0000-0000-0000-000000000011',  -- LIC-2
        'c1212000-0000-0000-0000-000000000030',  -- mismo local
        '2026-06-12', 12.00, 60.00, NULL, 200.00,
        (SELECT id FROM _ids WHERE k = 'tolvacred')));

SELECT is(
    (SELECT instalacion_id FROM public.credito_local WHERE id = (SELECT id FROM _ids WHERE k = 'tolvacred')),
    (SELECT id FROM _ids WHERE k = 'inst2'),
    'traslado: la tolva se re-apunta a la nueva instalacion');
SELECT is(
    (SELECT count(*) FROM public.credito_local
      WHERE local_id = 'c1212000-0000-0000-0000-000000000030' AND tipo = 'tolva'),
    1::bigint, 'traslado: sigue habiendo 1 sola tolva (no se duplica)');

-- --- K. condonar deja la deuda fuera del saldo -------------------------------
INSERT INTO _ids(k, id) VALUES
    ('p2', public.crear_prestamo('c1212000-0000-0000-0000-000000000001',
                                 'c1212000-0000-0000-0000-000000000030', 50.00, 0, NULL, 'préstamo de prueba'));
SELECT is(
    (SELECT saldo_prestamo FROM public.v_local_saldo WHERE local_id = 'c1212000-0000-0000-0000-000000000030'),
    50.00::numeric, 'antes de condonar: saldo_prestamo 50');
SELECT lives_ok(
    format($$ SELECT public.condonar_credito(%L, 'perdon') $$, (SELECT id FROM _ids WHERE k = 'p2')),
    'condonar_credito ejecuta (rol admin)');
SELECT is(
    (SELECT estado FROM public.credito_local WHERE id = (SELECT id FROM _ids WHERE k = 'p2')),
    'condonado', 'condonar: estado condonado');
SELECT is(
    (SELECT saldo_prestamo FROM public.v_local_saldo WHERE local_id = 'c1212000-0000-0000-0000-000000000030'),
    0.00::numeric, 'tras condonar: saldo_prestamo 0 (excluido del agregado)');

-- --- L. permisos: un no-miembro no puede crear deudas ------------------------
SET LOCAL request.jwt.claims = '{"sub":"c1212000-0000-0000-0000-0000000000b2","role":"authenticated"}';
SELECT throws_ok(
    $$ SELECT public.crear_prestamo('c1212000-0000-0000-0000-000000000001',
                                    'c1212000-0000-0000-0000-000000000030', 10) $$,
    '42501', NULL, 'un no-miembro no puede crear_prestamo');

SELECT * FROM finish();
ROLLBACK;
