-- =============================================================================
-- T-18 — Tests de `obtener_baseline`.
--
-- Verifica los 4 caminos:
--   * Sin recaudación ni cambio_placa -> instalacion_base
--   * Solo recaudación previa -> recaudacion_anterior
--   * Solo cambio_placa previo -> cambio_placa
--   * Ambos: gana el más reciente; en empate gana cambio_placa
--   * Recaudaciones anuladas se IGNORAN
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(8);

-- Setup: empresa, usuario, licencia, maquina, local, instalación
INSERT INTO auth.users (id) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
INSERT INTO public.empresa (id, nombre)
    VALUES ('e0000000-0000-0000-0000-000000000001', 'Test Empresa');
INSERT INTO public.usuario (id, nombre_completo)
    VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Test Tecnico');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol)
    VALUES ('e0000000-0000-0000-0000-000000000001',
            'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'tecnico');
INSERT INTO public.licencia (id, empresa_id, numero)
    VALUES ('e0000000-0000-0000-0000-000000000010',
            'e0000000-0000-0000-0000-000000000001', 'L-T-01');
-- La base de la instalación se DERIVA de la máquina (trigger
-- trg_set_contador_base_instalacion). Para que la base sea 1000/500 sin
-- historial previo, la máquina arranca con esos contadores iniciales.
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial)
    VALUES ('e0000000-0000-0000-0000-000000000020',
            'e0000000-0000-0000-0000-000000000001', 'M-T-01', 0.20,
            1000, 500);
INSERT INTO public.local (id, empresa_id, nombre)
    VALUES ('e0000000-0000-0000-0000-000000000030',
            'e0000000-0000-0000-0000-000000000001', 'Bar Test');
INSERT INTO public.instalacion (
    id, empresa_id, maquina_id, licencia_id, local_id, fecha_inicio,
    tasa_semanal, porcentaje_local,
    contador_entradas_base, contador_salidas_base
) VALUES (
    'e0000000-0000-0000-0000-000000000040',
    'e0000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000020',
    'e0000000-0000-0000-0000-000000000010',
    'e0000000-0000-0000-0000-000000000030',
    '2026-05-01',
    10.00, 50.00,
    1000, 500
);

-- 1) Sin recaudación ni cambio_placa: usa la base de la instalación
SELECT is(
    (public.obtener_baseline('e0000000-0000-0000-0000-000000000040', '2026-05-15 10:00+02')).origen,
    'instalacion_base',
    'Sin nada previo: origen = instalacion_base'
);

SELECT is(
    (public.obtener_baseline('e0000000-0000-0000-0000-000000000040', '2026-05-15 10:00+02')).entradas,
    1000::bigint,
    'Sin nada previo: entradas = base'
);

-- 2) Con una recaudación firme: gana esa
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen
) VALUES (
    'e0000000-0000-0000-0000-000000000050',
    'e0000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000040',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '2026-05-10 10:00+02',
    1000, 500, 1500, 700,
    0.20, 60.00, 1, 10.00, 10.00, 50.00, 50.00, 25.00, 25.00,
    '[{"denominacion":50,"cantidad":1},{"denominacion":10,"cantidad":1}]'::jsonb,
    '[{"denominacion":20,"cantidad":1},{"denominacion":5,"cantidad":1}]'::jsonb,
    'idem-1', 'instalacion_base'
);

SELECT is(
    (public.obtener_baseline('e0000000-0000-0000-0000-000000000040', '2026-05-15 10:00+02')).origen,
    'recaudacion_anterior',
    'Con recaudación firme previa: origen = recaudacion_anterior'
);

SELECT is(
    (public.obtener_baseline('e0000000-0000-0000-0000-000000000040', '2026-05-15 10:00+02')).entradas,
    1500::bigint,
    'Con recaudación firme previa: entradas = contador_actual de la recaudación'
);

-- 3) Cambio de placa más reciente que la recaudación: gana cambio_placa
INSERT INTO public.cambio_placa (
    id, empresa_id, instalacion_id, fecha, usuario_id,
    contador_entradas_nuevo, contador_salidas_nuevo
) VALUES (
    'e0000000-0000-0000-0000-000000000060',
    'e0000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000040',
    '2026-05-12 10:00+02',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    0, 0
);

SELECT is(
    (public.obtener_baseline('e0000000-0000-0000-0000-000000000040', '2026-05-15 10:00+02')).origen,
    'cambio_placa',
    'Cambio de placa más reciente que recaudación: origen = cambio_placa'
);

SELECT is(
    (public.obtener_baseline('e0000000-0000-0000-0000-000000000040', '2026-05-15 10:00+02')).entradas,
    0::bigint,
    'Cambio de placa: entradas = contador_entradas_nuevo'
);

-- 4) Recaudación posterior al cambio_placa: gana de nuevo recaudación
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen
) VALUES (
    'e0000000-0000-0000-0000-000000000070',
    'e0000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000040',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '2026-05-13 10:00+02',
    0, 0, 200, 100,
    0.20, 20.00, 1, 10.00, 10.00, 10.00, 50.00, 5.00, 5.00,
    '[{"denominacion":20,"cantidad":1}]'::jsonb,
    '[{"denominacion":5,"cantidad":1}]'::jsonb,
    'idem-2', 'cambio_placa'
);

SELECT is(
    (public.obtener_baseline('e0000000-0000-0000-0000-000000000040', '2026-05-15 10:00+02')).origen,
    'recaudacion_anterior',
    'Recaudación tras cambio_placa: vuelve a ganar recaudacion_anterior'
);

-- 5) Anulamos la última recaudación: vuelve a ganar el cambio_placa
UPDATE public.recaudacion
   SET estado = 'anulada',
       motivo_anulacion = 'Test anulación',
       anulada_por = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
       anulada_en = now()
 WHERE id = 'e0000000-0000-0000-0000-000000000070';

SELECT is(
    (public.obtener_baseline('e0000000-0000-0000-0000-000000000040', '2026-05-15 10:00+02')).origen,
    'cambio_placa',
    'Recaudación anulada se ignora: vuelve cambio_placa como baseline'
);

SELECT * FROM finish();
ROLLBACK;
