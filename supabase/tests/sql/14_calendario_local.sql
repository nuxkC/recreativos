-- =============================================================================
-- Planificación de recaudación P1 — tests de calendario de local.
--
-- Cubre:
--   * actualizar_calendario_local: alta correcta (cadencia + fecha + operario),
--     limpieza (todo NULL), coherencia cadencia↔fecha, cadencia>0, operario
--     debe ser miembro operativo activo de la empresa, y rol gestor requerido.
--   * CHECKs de tabla (local_calendario_coherente, local_cadencia_positiva) por
--     escritura directa (superusuario del test, salta el REVOKE).
--   * crear_instalacion: con cadencia+fecha fija el calendario del local; con una
--     sola de las dos → 22023.
--
-- Las RPCs son SECURITY DEFINER y validan rol vía auth.uid(); simulamos el JWT
-- con `SET LOCAL request.jwt.claims`. BEGIN..ROLLBACK, sin depender de seed.sql.
-- Namespace de UUID: c1806… (P1, 2026-06-18) para no colisionar con sembrados.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(15);

-- --- Datos mínimos -----------------------------------------------------------
INSERT INTO auth.users (id) VALUES
    ('c1806000-0000-0000-0000-0000000000a1'),   -- owner E1
    ('c1806000-0000-0000-0000-0000000000a2'),   -- tecnico E1 (activo)
    ('c1806000-0000-0000-0000-0000000000a3'),   -- tecnico E2 (otra empresa)
    ('c1806000-0000-0000-0000-0000000000a4');   -- tecnico E1 INACTIVO

INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin) VALUES
    ('c1806000-0000-0000-0000-000000000001', 'Test Empresa P1-A', now(), now() + interval '30 days'),
    ('c1806000-0000-0000-0000-000000000002', 'Test Empresa P1-B', now(), now() + interval '30 days');

INSERT INTO public.usuario (id, nombre_completo) VALUES
    ('c1806000-0000-0000-0000-0000000000a1', 'Owner E1'),
    ('c1806000-0000-0000-0000-0000000000a2', 'Tecnico E1'),
    ('c1806000-0000-0000-0000-0000000000a3', 'Tecnico E2'),
    ('c1806000-0000-0000-0000-0000000000a4', 'Tecnico E1 inactivo');

INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('c1806000-0000-0000-0000-000000000001', 'c1806000-0000-0000-0000-0000000000a1', 'owner',   true),
    ('c1806000-0000-0000-0000-000000000001', 'c1806000-0000-0000-0000-0000000000a2', 'tecnico', true),
    ('c1806000-0000-0000-0000-000000000002', 'c1806000-0000-0000-0000-0000000000a3', 'tecnico', true),
    ('c1806000-0000-0000-0000-000000000001', 'c1806000-0000-0000-0000-0000000000a4', 'tecnico', false);

INSERT INTO public.licencia (id, empresa_id, numero) VALUES
    ('c1806000-0000-0000-0000-000000000010', 'c1806000-0000-0000-0000-000000000001', 'LIC-P1');
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial) VALUES
    ('c1806000-0000-0000-0000-000000000020', 'c1806000-0000-0000-0000-000000000001', 'M-P1', 0.20, 1000, 400);
INSERT INTO public.local (id, empresa_id, nombre) VALUES
    ('c1806000-0000-0000-0000-000000000030', 'c1806000-0000-0000-0000-000000000001', 'Bar P1');

-- Simula el JWT del owner (gestor) para auth.uid() en las RPCs.
SET LOCAL request.jwt.claims = '{"sub":"c1806000-0000-0000-0000-0000000000a1","role":"authenticated"}';

-- --- A. actualizar_calendario_local: alta correcta ---------------------------
SELECT public.actualizar_calendario_local(
    'c1806000-0000-0000-0000-000000000030', 2::smallint, '2026-06-01'::date,
    'c1806000-0000-0000-0000-0000000000a2');

SELECT is(
    (SELECT cadencia_semanas FROM public.local WHERE id = 'c1806000-0000-0000-0000-000000000030'),
    2::smallint, 'actualizar_calendario_local: cadencia_semanas = 2');
SELECT is(
    (SELECT fecha_inicio_recaudacion FROM public.local WHERE id = 'c1806000-0000-0000-0000-000000000030'),
    '2026-06-01'::date, 'actualizar_calendario_local: fecha_inicio_recaudacion fijada');
SELECT is(
    (SELECT operario_id FROM public.local WHERE id = 'c1806000-0000-0000-0000-000000000030'),
    'c1806000-0000-0000-0000-0000000000a2'::uuid, 'actualizar_calendario_local: operario asignado');

-- --- B. limpieza: todo NULL ---------------------------------------------------
SELECT public.actualizar_calendario_local(
    'c1806000-0000-0000-0000-000000000030', NULL, NULL, NULL);

SELECT ok(
    (SELECT cadencia_semanas IS NULL FROM public.local WHERE id = 'c1806000-0000-0000-0000-000000000030'),
    'actualizar_calendario_local: limpia cadencia (NULL)');
SELECT ok(
    (SELECT operario_id IS NULL FROM public.local WHERE id = 'c1806000-0000-0000-0000-000000000030'),
    'actualizar_calendario_local: limpia operario (NULL)');

-- --- C. coherencia: cadencia sin fecha → 22023 -------------------------------
SELECT throws_ok(
    $$ SELECT public.actualizar_calendario_local(
           'c1806000-0000-0000-0000-000000000030', 2::smallint, NULL, NULL) $$,
    '22023', NULL, 'cadencia sin fecha: rechazada (22023)');

-- --- D. cadencia <= 0 → 22023 -------------------------------------------------
SELECT throws_ok(
    $$ SELECT public.actualizar_calendario_local(
           'c1806000-0000-0000-0000-000000000030', 0::smallint, '2026-06-01'::date, NULL) $$,
    '22023', NULL, 'cadencia 0: rechazada (22023)');

-- --- E. operario de otra empresa → 22023 -------------------------------------
SELECT throws_ok(
    $$ SELECT public.actualizar_calendario_local(
           'c1806000-0000-0000-0000-000000000030', NULL, NULL,
           'c1806000-0000-0000-0000-0000000000a3') $$,
    '22023', NULL, 'operario de otra empresa: rechazado (22023)');

-- --- F. operario inactivo → 22023 --------------------------------------------
SELECT throws_ok(
    $$ SELECT public.actualizar_calendario_local(
           'c1806000-0000-0000-0000-000000000030', NULL, NULL,
           'c1806000-0000-0000-0000-0000000000a4') $$,
    '22023', NULL, 'operario inactivo: rechazado (22023)');

-- --- G. no-gestor (técnico) → 42501 ------------------------------------------
SET LOCAL request.jwt.claims = '{"sub":"c1806000-0000-0000-0000-0000000000a2","role":"authenticated"}';
SELECT throws_ok(
    $$ SELECT public.actualizar_calendario_local(
           'c1806000-0000-0000-0000-000000000030', 2::smallint, '2026-06-01'::date, NULL) $$,
    '42501', NULL, 'técnico no puede planificar: rechazado (42501)');
SET LOCAL request.jwt.claims = '{"sub":"c1806000-0000-0000-0000-0000000000a1","role":"authenticated"}';

-- --- H. CHECKs de tabla por escritura directa (superusuario del test) --------
-- El local quedó todo-NULL tras B.
SELECT throws_ok(
    $$ UPDATE public.local SET cadencia_semanas = 1
        WHERE id = 'c1806000-0000-0000-0000-000000000030' $$,
    '23514', NULL, 'CHECK coherente: cadencia sin fecha rechazada por la tabla');
SELECT throws_ok(
    $$ UPDATE public.local SET cadencia_semanas = 0, fecha_inicio_recaudacion = '2026-06-01'
        WHERE id = 'c1806000-0000-0000-0000-000000000030' $$,
    '23514', NULL, 'CHECK positiva: cadencia 0 rechazada por la tabla');

-- --- I. crear_instalacion fija el calendario del local -----------------------
SELECT public.crear_instalacion(
    'c1806000-0000-0000-0000-000000000001',   -- empresa
    'c1806000-0000-0000-0000-000000000020',   -- maquina
    'c1806000-0000-0000-0000-000000000010',   -- licencia
    'c1806000-0000-0000-0000-000000000030',   -- local
    '2026-06-10'::date, 100::numeric, 50::numeric,
    NULL, 0::numeric, NULL,
    4::smallint, '2026-06-10'::date);

SELECT is(
    (SELECT cadencia_semanas FROM public.local WHERE id = 'c1806000-0000-0000-0000-000000000030'),
    4::smallint, 'crear_instalacion: fija cadencia del local');
SELECT is(
    (SELECT fecha_inicio_recaudacion FROM public.local WHERE id = 'c1806000-0000-0000-0000-000000000030'),
    '2026-06-10'::date, 'crear_instalacion: fija fecha de inicio del local');

-- --- J. crear_instalacion con calendario incoherente → 22023 -----------------
SELECT throws_ok(
    $$ SELECT public.crear_instalacion(
           'c1806000-0000-0000-0000-000000000001',
           'c1806000-0000-0000-0000-000000000020',
           'c1806000-0000-0000-0000-000000000010',
           'c1806000-0000-0000-0000-000000000030',
           '2026-06-10'::date, 100::numeric, 50::numeric,
           NULL, 0::numeric, NULL,
           4::smallint, NULL) $$,
    '22023', NULL, 'crear_instalacion: cadencia sin fecha rechazada (22023)');

SELECT * FROM finish();
ROLLBACK;
