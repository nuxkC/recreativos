-- =============================================================================
-- T-224 — Tests de la reposición de tolva en la persistencia de recaudación.
--
-- Cubre persistir_recaudacion() + revertir_recuperaciones_recaudacion() con la
-- reposición de tolva (§5.6):
--   * persistir inserta el movimiento de reposición ATÓMICO con la recaudación;
--     la tolva efectiva sube y el pendiente baja.
--   * revertir (anulación) borra la reposición: la merma vuelve a estar
--     pendiente; la recaudación conserva reposicion_tolva como histórico.
--   * revalidación: una reposición que supera el pendiente vivo se rechaza.
--   * el invariante chk_recaudacion_partes ata el reparto a base_reparto
--     (parte_local + parte_empresa + reposicion_tolva = neto).
--
-- Namespace de UUID `c1224…` (T-224). BEGIN..ROLLBACK, sin depender de seed.sql.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(11);

-- --- Datos mínimos -----------------------------------------------------------
INSERT INTO auth.users (id) VALUES ('c1224000-0000-0000-0000-0000000000a1');
INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin)
    VALUES ('c1224000-0000-0000-0000-000000000001', 'Test Empresa T-224',
            now(), now() + interval '30 days');
INSERT INTO public.usuario (id, nombre_completo)
    VALUES ('c1224000-0000-0000-0000-0000000000a1', 'Test Owner');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol)
    VALUES ('c1224000-0000-0000-0000-000000000001', 'c1224000-0000-0000-0000-0000000000a1', 'owner');
INSERT INTO public.licencia (id, empresa_id, numero) VALUES
    ('c1224000-0000-0000-0000-000000000010', 'c1224000-0000-0000-0000-000000000001', 'LIC-1'),
    ('c1224000-0000-0000-0000-000000000011', 'c1224000-0000-0000-0000-000000000001', 'LIC-2');
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial) VALUES
    ('c1224000-0000-0000-0000-000000000020', 'c1224000-0000-0000-0000-000000000001', 'M-1', 0.20, 1000, 400),
    ('c1224000-0000-0000-0000-000000000021', 'c1224000-0000-0000-0000-000000000001', 'M-2', 0.20, 1000, 400);
INSERT INTO public.local (id, empresa_id, nombre)
    VALUES ('c1224000-0000-0000-0000-000000000030', 'c1224000-0000-0000-0000-000000000001', 'Bar Test');

SET LOCAL request.jwt.claims = '{"sub":"c1224000-0000-0000-0000-0000000000a1","role":"authenticated"}';

CREATE TEMP TABLE _ids(k text PRIMARY KEY, id uuid) ON COMMIT DROP;

-- inst1: M-1 instalada con tolva teórica 100. Avería con merma 50 → pendiente 50.
INSERT INTO _ids(k, id) VALUES
    ('inst1', public.crear_instalacion(
        'c1224000-0000-0000-0000-000000000001',
        'c1224000-0000-0000-0000-000000000020',
        'c1224000-0000-0000-0000-000000000010',
        'c1224000-0000-0000-0000-000000000030',
        '2026-06-01', 0.00, 50.00, NULL, 100.00, NULL));
INSERT INTO _ids(k, id) VALUES
    ('av1', public.crear_averia('c1224000-0000-0000-0000-000000000001',
                                'c1224000-0000-0000-0000-000000000020',
                                'falta_pago', 'premio pagado', false, NULL, true, 50.00));

-- --- persistir_recaudacion con reposición de tolva ---------------------------
-- neto 100, reposicion 50 → base_reparto 50, %50 → parte_local 25, parte_empresa 25.
-- desglose_local cuadra con pagado_local (= parte_local − recuperado_total 0 = 25).
SELECT public.persistir_recaudacion(
    jsonb_build_object(
        'id',                         'c1224000-0000-0000-0000-0000000000d1',
        'empresa_id',                 'c1224000-0000-0000-0000-000000000001',
        'instalacion_id',             (SELECT id FROM _ids WHERE k = 'inst1'),
        'tecnico_id',                 'c1224000-0000-0000-0000-0000000000a1',
        'fecha',                      '2026-06-12T10:00:00+02:00',
        'contador_entradas_anterior', 1000,
        'contador_salidas_anterior',  400,
        'contador_entradas_actual',   1500,
        'contador_salidas_actual',    400,
        'valor_credito_aplicado',     0.20,
        'recaudacion_bruta',          100.00,
        'semanas_aplicadas',          0,
        'tasa_semanal_aplicada',      0.00,
        'tasa_total_aplicada',        0.00,
        'recaudacion_neta',           100.00,
        'porcentaje_local_aplicado',  50.00,
        'parte_local',                25.00,
        'parte_empresa',              25.00,
        'reposicion_tolva',           50.00,
        'desglose_total',             '[{"denominacion":50,"cantidad":2}]'::jsonb,
        'desglose_local',             '[{"denominacion":5,"cantidad":5}]'::jsonb,
        'idempotency_key',            'c1224000-0000-0000-0000-0000000000e1',
        'baseline_origen',            'instalacion_base'
    ),
    '[]'::jsonb,
    'c1224000-0000-0000-0000-0000000000a1'
);

SELECT is(
    (SELECT estado FROM public.recaudacion WHERE id = 'c1224000-0000-0000-0000-0000000000d1'),
    'firme', 'persistir: recaudacion insertada (firme)');
SELECT is(
    (SELECT reposicion_tolva FROM public.recaudacion WHERE id = 'c1224000-0000-0000-0000-0000000000d1'),
    50.00::numeric, 'persistir: reposicion_tolva = 50 en la recaudacion');
SELECT is(
    (SELECT count(*) FROM public.tolva_movimiento
      WHERE recaudacion_id = 'c1224000-0000-0000-0000-0000000000d1' AND tipo = 'reposicion'),
    1::bigint, 'persistir: 1 movimiento de reposicion en el ledger');
SELECT is(
    (SELECT importe FROM public.tolva_movimiento
      WHERE recaudacion_id = 'c1224000-0000-0000-0000-0000000000d1'),
    50.00::numeric, 'persistir: la reposicion repone 50');
SELECT is(
    (SELECT pendiente FROM public.v_instalacion_tolva WHERE instalacion_id = (SELECT id FROM _ids WHERE k = 'inst1')),
    0.00::numeric, 'persistir: la merma queda saldada (pendiente 0)');
SELECT is(
    (SELECT efectiva FROM public.v_instalacion_tolva WHERE instalacion_id = (SELECT id FROM _ids WHERE k = 'inst1')),
    100.00::numeric, 'persistir: la tolva efectiva vuelve a la teorica (100)');

-- --- revertir (anulación): la reposición se deshace -------------------------
SELECT public.revertir_recuperaciones_recaudacion('c1224000-0000-0000-0000-0000000000d1');

SELECT is(
    (SELECT count(*) FROM public.tolva_movimiento
      WHERE recaudacion_id = 'c1224000-0000-0000-0000-0000000000d1' AND tipo = 'reposicion'),
    0::bigint, 'revertir: el movimiento de reposicion se borra');
SELECT is(
    (SELECT pendiente FROM public.v_instalacion_tolva WHERE instalacion_id = (SELECT id FROM _ids WHERE k = 'inst1')),
    50.00::numeric, 'revertir: la merma vuelve a estar pendiente (50)');
SELECT is(
    (SELECT reposicion_tolva FROM public.recaudacion WHERE id = 'c1224000-0000-0000-0000-0000000000d1'),
    50.00::numeric, 'revertir: la recaudacion anulada conserva reposicion_tolva (historico)');

-- --- revalidación: una reposición que supera el pendiente vivo se rechaza -----
-- inst2 (M-2) sin merma: pendiente 0. Una recaudación que diga reposicion 10 se
-- rechaza dentro de la transacción (no se cuela una reposición sin merma).
INSERT INTO _ids(k, id) VALUES
    ('inst2', public.crear_instalacion(
        'c1224000-0000-0000-0000-000000000001',
        'c1224000-0000-0000-0000-000000000021',
        'c1224000-0000-0000-0000-000000000011',
        'c1224000-0000-0000-0000-000000000030',
        '2026-06-01', 0.00, 50.00, NULL, 0.00, NULL));
SELECT throws_ok(
    format($$ SELECT public.persistir_recaudacion(
        jsonb_build_object(
            'id', 'c1224000-0000-0000-0000-0000000000d2',
            'empresa_id', 'c1224000-0000-0000-0000-000000000001',
            'instalacion_id', %L,
            'tecnico_id', 'c1224000-0000-0000-0000-0000000000a1',
            'fecha', '2026-06-12T11:00:00+02:00',
            'contador_entradas_anterior', 1000, 'contador_salidas_anterior', 400,
            'contador_entradas_actual', 1500, 'contador_salidas_actual', 400,
            'valor_credito_aplicado', 0.20,
            'recaudacion_bruta', 100.00, 'semanas_aplicadas', 0,
            'tasa_semanal_aplicada', 0.00, 'tasa_total_aplicada', 0.00,
            'recaudacion_neta', 100.00, 'porcentaje_local_aplicado', 50.00,
            'parte_local', 45.00, 'parte_empresa', 45.00, 'reposicion_tolva', 10.00,
            'desglose_total', '[{"denominacion":50,"cantidad":2}]'::jsonb,
            'desglose_local', '[{"denominacion":5,"cantidad":9}]'::jsonb,
            'idempotency_key', 'c1224000-0000-0000-0000-0000000000e2',
            'baseline_origen', 'instalacion_base'
        ), '[]'::jsonb, 'c1224000-0000-0000-0000-0000000000a1') $$,
        (SELECT id FROM _ids WHERE k = 'inst2')),
    '23514', NULL, 'persistir: reposicion mayor que el pendiente vivo se rechaza');

-- --- invariante: parte_local + parte_empresa + reposicion_tolva = neto --------
SELECT throws_ok(
    format($$ SELECT public.persistir_recaudacion(
        jsonb_build_object(
            'id', 'c1224000-0000-0000-0000-0000000000d3',
            'empresa_id', 'c1224000-0000-0000-0000-000000000001',
            'instalacion_id', %L,
            'tecnico_id', 'c1224000-0000-0000-0000-0000000000a1',
            'fecha', '2026-06-12T12:00:00+02:00',
            'contador_entradas_anterior', 1000, 'contador_salidas_anterior', 400,
            'contador_entradas_actual', 1500, 'contador_salidas_actual', 400,
            'valor_credito_aplicado', 0.20,
            'recaudacion_bruta', 100.00, 'semanas_aplicadas', 0,
            'tasa_semanal_aplicada', 0.00, 'tasa_total_aplicada', 0.00,
            'recaudacion_neta', 100.00, 'porcentaje_local_aplicado', 50.00,
            'parte_local', 25.00, 'parte_empresa', 25.00, 'reposicion_tolva', 40.00,
            'desglose_total', '[{"denominacion":50,"cantidad":2}]'::jsonb,
            'desglose_local', '[{"denominacion":5,"cantidad":5}]'::jsonb,
            'idempotency_key', 'c1224000-0000-0000-0000-0000000000e3',
            'baseline_origen', 'instalacion_base'
        ), '[]'::jsonb, 'c1224000-0000-0000-0000-0000000000a1') $$,
        (SELECT id FROM _ids WHERE k = 'inst2')),
    '23514', NULL, 'chk_recaudacion_partes: parte_local+parte_empresa+reposicion != neto se rechaza');

SELECT * FROM finish();
ROLLBACK;
