-- =============================================================================
-- Cuadre semanal del técnico — vista v_cuadre_semanal_tecnico.
-- Neto llevado = Σ(desglose_total − desglose_local) por (semana ISO, denominación)
-- del técnico autenticado, solo recaudaciones estado='firme'.
-- Setup como superusuario; aserciones bajan a rol authenticated + jwt (RLS).
-- BEGIN..ROLLBACK, sin seed.sql.
-- =============================================================================
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(6);

-- --- SETUP (superusuario): empresa(UTC), 2 técnicos, instalacion -------------
-- (Reutiliza el patrón de 17_recaudacion_historica.sql para auth.users,
--  empresa, usuario, local, maquina, licencia, instalacion. IDs base 'c0adce00-'.
--  Nota: 'r' no es dígito hex válido en UUID; el namespace 'c0adre00' del brief se
--  materializa como 'c0adce00' (solo 0-9 a-f). Misma intención de namespace.)
INSERT INTO auth.users (id) VALUES
    ('c0adce00-0000-0000-0000-0000000000a2'),   -- tecA
    ('c0adce00-0000-0000-0000-0000000000a3');    -- tecB
INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('c0adce00-0000-0000-0000-000000000001', 'Test Cuadre', 'UTC', now(), now() + interval '30 days');
INSERT INTO public.usuario (id, nombre_completo) VALUES
    ('c0adce00-0000-0000-0000-0000000000a2', 'Tec A'),
    ('c0adce00-0000-0000-0000-0000000000a3', 'Tec B');

INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('c0adce00-0000-0000-0000-000000000001', 'c0adce00-0000-0000-0000-0000000000a2', 'tecnico', true),
    ('c0adce00-0000-0000-0000-000000000001', 'c0adce00-0000-0000-0000-0000000000a3', 'tecnico', true);

-- Local asignado a tecA (operario_id) para que la RLS estricta deje ver sus recaudaciones.
INSERT INTO public.local (id, empresa_id, nombre, operario_id) VALUES
    ('c0adce00-0000-0000-0000-000000000031', 'c0adce00-0000-0000-0000-000000000001', 'Local Cuadre', 'c0adce00-0000-0000-0000-0000000000a2');

INSERT INTO public.licencia (id, empresa_id, numero) VALUES
    ('c0adce00-0000-0000-0000-000000000051', 'c0adce00-0000-0000-0000-000000000001', 'LIC-C');

INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial) VALUES
    ('c0adce00-0000-0000-0000-000000000041', 'c0adce00-0000-0000-0000-000000000001', 'M-C', 0.20, 0, 0);

INSERT INTO public.instalacion (id, empresa_id, maquina_id, licencia_id, local_id,
                                fecha_inicio, tasa_semanal, porcentaje_local, estado) VALUES
    ('c0adce00-0000-0000-0000-0000000000f1', 'c0adce00-0000-0000-0000-000000000001',
     'c0adce00-0000-0000-0000-000000000041', 'c0adce00-0000-0000-0000-000000000051',
     'c0adce00-0000-0000-0000-000000000031', '2026-06-01', 50.00, 50.00, 'activa');

-- Helper local para insertar una recaudación firme coherente con los constraints.
-- desglose_total/local deben cumplir: sumar_desglose(total)=recaudacion_bruta,
-- sumar_desglose(local)=parte_local, neta=bruta-tasa_total, partes suman neta.
-- Recaudación 1 (tecA, semana del 2026-06-22 lunes, UTC):
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen, baseline_id, estado
) VALUES (
    'c0adce00-0000-0000-0000-0000000000e1',
    'c0adce00-0000-0000-0000-000000000001',
    'c0adce00-0000-0000-0000-0000000000f1',
    'c0adce00-0000-0000-0000-0000000000a2',
    '2026-06-23 10:00:00+00',
    0, 0, 100, 0,
    1.00, 20.00, 0, 0, 0.00, 20.00,
    50.00, 10.00, 10.00,
    '[{"denominacion":2,"cantidad":10}]'::jsonb,
    '[{"denominacion":2,"cantidad":5}]'::jsonb,
    'c0adre-idem-1', 'instalacion_base', 'c0adce00-0000-0000-0000-0000000000b1', 'firme'
);
-- Recaudación 2 (tecA, MISMA semana): total 1×50€ + 0; local 0 -> neto carried 50€,1×50.
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen, baseline_id, estado
) VALUES (
    'c0adce00-0000-0000-0000-0000000000e2',
    'c0adce00-0000-0000-0000-000000000001',
    'c0adce00-0000-0000-0000-0000000000f1',
    'c0adce00-0000-0000-0000-0000000000a2',
    '2026-06-24 10:00:00+00',
    0, 0, 100, 0,
    1.00, 50.00, 0, 0, 0.00, 50.00,
    0.00, 0.00, 50.00,
    '[{"denominacion":50,"cantidad":1}]'::jsonb,
    '[]'::jsonb,
    'c0adre-idem-2', 'instalacion_base', 'c0adce00-0000-0000-0000-0000000000b2', 'firme'
);
-- Recaudación 3 (tecA, ANULADA, misma semana): no debe contar.
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen,
    estado, motivo_anulacion, anulada_por, anulada_en
) VALUES (
    'c0adce00-0000-0000-0000-0000000000e3',
    'c0adce00-0000-0000-0000-000000000001',
    'c0adce00-0000-0000-0000-0000000000f1',
    'c0adce00-0000-0000-0000-0000000000a2',
    '2026-06-25 10:00:00+00',
    0, 0, 100, 0,
    1.00, 20.00, 0, 0, 0.00, 20.00,
    50.00, 10.00, 10.00,
    '[{"denominacion":2,"cantidad":10}]'::jsonb,
    '[{"denominacion":2,"cantidad":5}]'::jsonb,
    'c0adre-idem-3', 'instalacion_base',
    'anulada', 'error de prueba', 'c0adce00-0000-0000-0000-0000000000a2', now()
);

-- --- ASERCIONES como rol authenticated + JWT de tecA -------------------------
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claims" TO '{"sub":"c0adce00-0000-0000-0000-0000000000a2","role":"authenticated"}';

-- 1) Neto de 2€ esa semana = 10(total r1) − 5(local r1) = 5
SELECT is(
    (SELECT cantidad_neta FROM public.v_cuadre_semanal_tecnico
      WHERE semana_inicio = DATE '2026-06-22' AND denominacion = 2),
    5::bigint, 'neto 2€ = 5 piezas');

-- 2) Neto de 50€ esa semana = 1
SELECT is(
    (SELECT cantidad_neta FROM public.v_cuadre_semanal_tecnico
      WHERE semana_inicio = DATE '2026-06-22' AND denominacion = 50),
    1::bigint, 'neto 50€ = 1 pieza');

-- 3) Importe total llevado esa semana = 5×2 + 1×50 = 60.00
SELECT is(
    (SELECT SUM(importe_neto) FROM public.v_cuadre_semanal_tecnico
      WHERE semana_inicio = DATE '2026-06-22'),
    60.00::numeric, 'total llevado = 60,00 €');

-- 4) num_recaudaciones = 2 (la anulada no cuenta)
SELECT is(
    (SELECT DISTINCT num_recaudaciones FROM public.v_cuadre_semanal_tecnico
      WHERE semana_inicio = DATE '2026-06-22'),
    2::bigint, 'num_recaudaciones = 2 (excluye anulada)');

-- 5) La recaudación anulada no añade denominaciones de más
SELECT is(
    (SELECT COUNT(*) FROM public.v_cuadre_semanal_tecnico
      WHERE semana_inicio = DATE '2026-06-22'),
    2::bigint, 'solo 2 denominaciones netas (2€ y 50€)');

-- 6) tecB no ve la caja de tecA (aislamiento)
SET LOCAL "request.jwt.claims" TO '{"sub":"c0adce00-0000-0000-0000-0000000000a3","role":"authenticated"}';
SELECT is(
    (SELECT COUNT(*) FROM public.v_cuadre_semanal_tecnico),
    0::bigint, 'tecB no ve la caja de tecA');

SELECT * FROM finish();
ROLLBACK;
