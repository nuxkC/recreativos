-- =============================================================================
-- T-214 — Tests de la persistencia atómica de recaudación + recuperación de deuda.
--
-- Cubre las RPCs persistir_recaudacion() y revertir_recuperaciones_recaudacion():
--   * persistir inserta la recaudación, el ledger de recuperación y salda el
--     crédito que llega a 0, todo junto.
--   * recuperado_total y la columna generada pagado_local (= parte_local − retenido).
--   * rechazo si una recuperación supera el saldo vivo del crédito.
--   * revertir borra el ledger, reabre el crédito y pone recuperado_total a 0.
--
-- Namespace de UUID `c1214…` (T-214) para no colisionar con el seed. BEGIN..ROLLBACK.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(13);

-- --- Datos mínimos -----------------------------------------------------------
INSERT INTO auth.users (id) VALUES ('c1214000-0000-0000-0000-0000000000a1');
INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin)
    VALUES ('c1214000-0000-0000-0000-000000000001', 'Test Empresa T-214',
            now(), now() + interval '30 days');
INSERT INTO public.usuario (id, nombre_completo)
    VALUES ('c1214000-0000-0000-0000-0000000000a1', 'Test Owner');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol)
    VALUES ('c1214000-0000-0000-0000-000000000001', 'c1214000-0000-0000-0000-0000000000a1', 'owner');
INSERT INTO public.licencia (id, empresa_id, numero)
    VALUES ('c1214000-0000-0000-0000-000000000010', 'c1214000-0000-0000-0000-000000000001', 'LIC-1');
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial)
    VALUES ('c1214000-0000-0000-0000-000000000020', 'c1214000-0000-0000-0000-000000000001', 'M-1', 0.20, 1000, 400);
INSERT INTO public.local (id, empresa_id, nombre)
    VALUES ('c1214000-0000-0000-0000-000000000030', 'c1214000-0000-0000-0000-000000000001', 'Bar Test');

SET LOCAL request.jwt.claims = '{"sub":"c1214000-0000-0000-0000-0000000000a1","role":"authenticated"}';

CREATE TEMP TABLE _ids(k text PRIMARY KEY, id uuid) ON COMMIT DROP;

-- Instalación (sin tolva) + un préstamo de 90 como deuda a recuperar.
INSERT INTO _ids(k, id) VALUES
    ('inst', public.crear_instalacion(
        'c1214000-0000-0000-0000-000000000001',
        'c1214000-0000-0000-0000-000000000020',
        'c1214000-0000-0000-0000-000000000010',
        'c1214000-0000-0000-0000-000000000030',
        '2026-06-01', 10.00, 50.00, NULL, 0, NULL));
INSERT INTO _ids(k, id) VALUES
    ('p1', public.crear_prestamo('c1214000-0000-0000-0000-000000000001',
                                 'c1214000-0000-0000-0000-000000000030', 90.00, 0, NULL, 'préstamo de prueba'));

-- --- persistir_recaudacion ---------------------------------------------------
-- bruto 200, tasa 20 → neto 180, %50 → parte_local 90, parte_empresa 90.
-- recuperado_total 90 (todo a la deuda de 90) → pagado_local 0, deuda saldada.
SELECT public.persistir_recaudacion(
    jsonb_build_object(
        'id',                         'c1214000-0000-0000-0000-0000000000d1',
        'empresa_id',                 'c1214000-0000-0000-0000-000000000001',
        'instalacion_id',             (SELECT id FROM _ids WHERE k = 'inst'),
        'tecnico_id',                 'c1214000-0000-0000-0000-0000000000a1',
        'fecha',                      '2026-06-12T10:00:00+02:00',
        'contador_entradas_anterior', 1000,
        'contador_salidas_anterior',  400,
        'contador_entradas_actual',   2000,
        'contador_salidas_actual',    420,
        'valor_credito_aplicado',     0.20,
        'recaudacion_bruta',          200.00,
        'semanas_aplicadas',          2,
        'tasa_semanal_aplicada',      10.00,
        'tasa_total_aplicada',        20.00,
        'recaudacion_neta',           180.00,
        'porcentaje_local_aplicado',  50.00,
        'parte_local',                90.00,
        'parte_empresa',              90.00,
        'recuperado_total',           90.00,
        'desglose_total',             '[{"denominacion":50,"cantidad":4}]'::jsonb,
        'desglose_local',             '[]'::jsonb,
        'idempotency_key',            'c1214000-0000-0000-0000-0000000000e1',
        'baseline_origen',            'instalacion_base'
    ),
    jsonb_build_array(
        jsonb_build_object('credito_id', (SELECT id FROM _ids WHERE k = 'p1'), 'importe', 90.00)
    ),
    'c1214000-0000-0000-0000-0000000000a1'
);

SELECT is(
    (SELECT estado FROM public.recaudacion WHERE id = 'c1214000-0000-0000-0000-0000000000d1'),
    'firme', 'persistir: recaudacion insertada (firme)');
SELECT is(
    (SELECT recuperado_total FROM public.recaudacion WHERE id = 'c1214000-0000-0000-0000-0000000000d1'),
    90.00::numeric, 'persistir: recuperado_total = 90');
SELECT is(
    (SELECT pagado_local FROM public.recaudacion WHERE id = 'c1214000-0000-0000-0000-0000000000d1'),
    0.00::numeric, 'persistir: pagado_local generado = 0');
SELECT is(
    (SELECT count(*) FROM public.recuperacion WHERE recaudacion_id = 'c1214000-0000-0000-0000-0000000000d1'),
    1::bigint, 'persistir: 1 fila de recuperacion en el ledger');
SELECT is(
    (SELECT origen FROM public.recuperacion WHERE recaudacion_id = 'c1214000-0000-0000-0000-0000000000d1'),
    'recaudacion', 'persistir: recuperacion con origen recaudacion');
SELECT is(
    (SELECT saldo FROM public.v_credito_local_saldo WHERE credito_id = (SELECT id FROM _ids WHERE k = 'p1')),
    0.00::numeric, 'persistir: la deuda queda a saldo 0');
SELECT is(
    (SELECT estado FROM public.credito_local WHERE id = (SELECT id FROM _ids WHERE k = 'p1')),
    'saldado', 'persistir: la deuda queda saldada');

-- --- rechazo: recuperación que supera el saldo -------------------------------
INSERT INTO _ids(k, id) VALUES
    ('p2', public.crear_prestamo('c1214000-0000-0000-0000-000000000001',
                                 'c1214000-0000-0000-0000-000000000030', 30.00, 0, NULL, 'préstamo de prueba'));
SELECT throws_ok(
    format($$ SELECT public.persistir_recaudacion(
        jsonb_build_object(
            'id', 'c1214000-0000-0000-0000-0000000000d2',
            'empresa_id', 'c1214000-0000-0000-0000-000000000001',
            'instalacion_id', %L,
            'tecnico_id', 'c1214000-0000-0000-0000-0000000000a1',
            'fecha', '2026-06-12T11:00:00+02:00',
            'contador_entradas_anterior', 2000, 'contador_salidas_anterior', 420,
            'contador_entradas_actual', 2100, 'contador_salidas_actual', 420,
            'valor_credito_aplicado', 0.20,
            'recaudacion_bruta', 20.00, 'semanas_aplicadas', 0,
            'tasa_semanal_aplicada', 0.00, 'tasa_total_aplicada', 0.00,
            'recaudacion_neta', 20.00, 'porcentaje_local_aplicado', 50.00,
            'parte_local', 10.00, 'parte_empresa', 10.00, 'recuperado_total', 10.00,
            'desglose_total', '[{"denominacion":20,"cantidad":1}]'::jsonb, 'desglose_local', '[]'::jsonb,
            'idempotency_key', 'c1214000-0000-0000-0000-0000000000e2',
            'baseline_origen', 'instalacion_base',
            'baseline_id', 'c1214000-0000-0000-0000-0000000000b2'
        ),
        jsonb_build_array(jsonb_build_object('credito_id', %L, 'importe', 1000.00)),
        'c1214000-0000-0000-0000-0000000000a1') $$,
        (SELECT id FROM _ids WHERE k = 'inst'),
        (SELECT id FROM _ids WHERE k = 'p2')),
    '23514', NULL, 'persistir: una recuperacion mayor que el saldo se rechaza');

-- --- revertir_recuperaciones_recaudacion (anulación) -------------------------
SELECT public.revertir_recuperaciones_recaudacion('c1214000-0000-0000-0000-0000000000d1');

SELECT is(
    (SELECT count(*) FROM public.recuperacion WHERE recaudacion_id = 'c1214000-0000-0000-0000-0000000000d1'),
    0::bigint, 'revertir: el ledger de la recaudacion queda vacio');
SELECT is(
    (SELECT estado FROM public.credito_local WHERE id = (SELECT id FROM _ids WHERE k = 'p1')),
    'abierto', 'revertir: la deuda se reabre');
SELECT is(
    (SELECT saldo FROM public.v_credito_local_saldo WHERE credito_id = (SELECT id FROM _ids WHERE k = 'p1')),
    90.00::numeric, 'revertir: el saldo vuelve a 90');
SELECT is(
    (SELECT recuperado_total FROM public.recaudacion WHERE id = 'c1214000-0000-0000-0000-0000000000d1'),
    90.00::numeric, 'revertir: la recaudacion anulada conserva su recuperado_total (historico)');
SELECT is(
    (SELECT pagado_local FROM public.recaudacion WHERE id = 'c1214000-0000-0000-0000-0000000000d1'),
    0.00::numeric, 'revertir: pagado_local se conserva (no se reescribe la fila anulada)');

SELECT * FROM finish();
ROLLBACK;
