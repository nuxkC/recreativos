-- =============================================================================
-- Planificación — agenda v_agenda_operario + funciones de estado (semana/día
-- objetivo). Verifica:
--   (A) DETERMINISTA (fechas fijas, independiente del día en que corre el test):
--       fecha_objetivo_agenda() y estado_agenda() — incluida la REGRESIÓN del
--       bug "pendiente vs atrasado" (F=miércoles, cada 2 semanas, hoy=lunes de
--       la 2ª semana → 'pendiente', NO 'atrasado').
--   (B) Vista + RLS (security_invoker, P2): el técnico solo ve sus locales. Los
--       estados día-dependientes (pendiente/atrasado) se cubren en (A); en la
--       vista solo se asertan estados día-independientes (toca_hoy con F=hoy,
--       al_dia, sin_planificar) para no depender del weekday del test.
--
-- Empresa zona_horaria='UTC' → "hoy" determinista. Namespace UUID: c1808….
-- BEGIN..ROLLBACK, sin depender de seed.sql.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(20);

-- =====================  (A) FUNCIONES — DETERMINISTA  ========================
-- 2026-06-15 lunes · 2026-06-17 miércoles · 2026-06-22 lunes · 2026-06-24 miérc.
-- 2026-06-29 lunes · 2026-07-01 miércoles · 2026-07-08 miércoles.

-- fecha_objetivo_agenda: día objetivo del ciclo vigente.
SELECT is(public.fecha_objetivo_agenda('2026-06-17', 2::smallint, '2026-06-29'),
          '2026-07-01'::date,
          'objetivo: F=mié, cada 2 sem, hoy=lunes w2 → miércoles de w2');
SELECT is(public.fecha_objetivo_agenda('2026-06-17', 1::smallint, '2026-06-24'),
          '2026-06-24'::date,
          'objetivo: semanal, hoy=mismo weekday semana siguiente → hoy');
SELECT is(public.fecha_objetivo_agenda('2026-06-17', 2::smallint, '2026-07-08'),
          '2026-07-01'::date,
          'objetivo: bisemanal en semana OFF (w3) → sigue el objetivo de w2 (arrastre)');
SELECT is(public.fecha_objetivo_agenda('2026-06-17', 1::smallint, '2026-06-10'),
          '2026-06-17'::date,
          'objetivo: hoy < F → F (sin semanas negativas)');

-- estado_agenda: un test por estado.
SELECT is(public.estado_agenda(NULL, NULL, '2026-06-22', NULL, false),
          'sin_planificar', 'estado: cadencia NULL → sin_planificar');
SELECT is(public.estado_agenda(1::smallint, '2026-06-17', '2026-06-10', '2026-06-17', false),
          'al_dia', 'estado: hoy < F → al_dia (aún no empieza)');
SELECT is(public.estado_agenda(2::smallint, '2026-06-17', '2026-07-02', '2026-07-01', true),
          'al_dia', 'estado: atendido en el ciclo → al_dia');
SELECT is(public.estado_agenda(2::smallint, '2026-06-17', '2026-06-29', '2026-07-01', false),
          'pendiente', 'estado: REGRESIÓN — hoy=lunes w2, toca el miércoles → pendiente (NO atrasado)');
SELECT is(public.estado_agenda(2::smallint, '2026-06-17', '2026-07-01', '2026-07-01', false),
          'toca_hoy', 'estado: hoy = día objetivo → toca_hoy');
SELECT is(public.estado_agenda(2::smallint, '2026-06-17', '2026-07-02', '2026-07-01', false),
          'atrasado', 'estado: pasó el día objetivo sin atender → atrasado');

-- =====================  (B) VISTA + RLS  =====================================
INSERT INTO auth.users (id) VALUES
    ('c1808000-0000-0000-0000-0000000000a1'),   -- owner (ve-todo)
    ('c1808000-0000-0000-0000-0000000000a2');   -- tecA (técnico)

INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('c1808000-0000-0000-0000-000000000001', 'Test Empresa P3a', 'UTC', now(), now() + interval '30 days');

INSERT INTO public.usuario (id, nombre_completo) VALUES
    ('c1808000-0000-0000-0000-0000000000a1', 'Owner'),
    ('c1808000-0000-0000-0000-0000000000a2', 'Tecnico A');

INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('c1808000-0000-0000-0000-000000000001', 'c1808000-0000-0000-0000-0000000000a1', 'owner',   true),
    ('c1808000-0000-0000-0000-000000000001', 'c1808000-0000-0000-0000-0000000000a2', 'tecnico', true);

-- Fixtures día-independientes, relativas al "hoy" en UTC. tecA: 6 locales.
INSERT INTO public.local (id, empresa_id, nombre, operario_id, cadencia_semanas, fecha_inicio_recaudacion) VALUES
    ('c1808000-0000-0000-0000-000000000031', 'c1808000-0000-0000-0000-000000000001', 'L_hoy',      'c1808000-0000-0000-0000-0000000000a2', 1,    (now() AT TIME ZONE 'UTC')::date),
    ('c1808000-0000-0000-0000-000000000033', 'c1808000-0000-0000-0000-000000000001', 'L_futuro',   'c1808000-0000-0000-0000-0000000000a2', 1,    (now() AT TIME ZONE 'UTC')::date + 14),
    ('c1808000-0000-0000-0000-000000000034', 'c1808000-0000-0000-0000-000000000001', 'L_atendido', 'c1808000-0000-0000-0000-0000000000a2', 1,    (now() AT TIME ZONE 'UTC')::date),
    ('c1808000-0000-0000-0000-000000000035', 'c1808000-0000-0000-0000-000000000001', 'L_lectura',  'c1808000-0000-0000-0000-0000000000a2', 1,    (now() AT TIME ZONE 'UTC')::date),
    ('c1808000-0000-0000-0000-000000000036', 'c1808000-0000-0000-0000-000000000001', 'L_sinplan',  'c1808000-0000-0000-0000-0000000000a2', NULL, NULL),
    ('c1808000-0000-0000-0000-000000000038', 'c1808000-0000-0000-0000-000000000001', 'L_bisem',    'c1808000-0000-0000-0000-0000000000a2', 2,    (now() AT TIME ZONE 'UTC')::date),
    -- owner (RLS): no visible para tecA.
    ('c1808000-0000-0000-0000-000000000037', 'c1808000-0000-0000-0000-000000000001', 'L_otro',     'c1808000-0000-0000-0000-0000000000a1', 1,    (now() AT TIME ZONE 'UTC')::date + 7);

-- Solo L_atendido y L_lectura cuelgan visita.
INSERT INTO public.licencia (id, empresa_id, numero) VALUES
    ('c1808000-0000-0000-0000-000000000051', 'c1808000-0000-0000-0000-000000000001', 'LIC-AT'),
    ('c1808000-0000-0000-0000-000000000052', 'c1808000-0000-0000-0000-000000000001', 'LIC-LE');
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial) VALUES
    ('c1808000-0000-0000-0000-000000000041', 'c1808000-0000-0000-0000-000000000001', 'M-AT', 0.20, 1000, 400),
    ('c1808000-0000-0000-0000-000000000042', 'c1808000-0000-0000-0000-000000000001', 'M-LE', 0.20, 1000, 400);
INSERT INTO public.instalacion (id, empresa_id, maquina_id, licencia_id, local_id,
                                fecha_inicio, tasa_semanal, porcentaje_local, estado) VALUES
    ('c1808000-0000-0000-0000-000000000061', 'c1808000-0000-0000-0000-000000000001',
     'c1808000-0000-0000-0000-000000000041', 'c1808000-0000-0000-0000-000000000051',
     'c1808000-0000-0000-0000-000000000034', '2026-06-01', 50.00, 50.00, 'activa'),
    ('c1808000-0000-0000-0000-000000000062', 'c1808000-0000-0000-0000-000000000001',
     'c1808000-0000-0000-0000-000000000042', 'c1808000-0000-0000-0000-000000000052',
     'c1808000-0000-0000-0000-000000000035', '2026-06-01', 50.00, 50.00, 'activa');

-- L_atendido: recaudación firme HOY (dentro de la ventana del ciclo).
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, recuperado_total, idempotency_key, baseline_origen) VALUES
    ('c1808000-0000-0000-0000-000000000071', 'c1808000-0000-0000-0000-000000000001',
     'c1808000-0000-0000-0000-000000000061', 'c1808000-0000-0000-0000-0000000000a2', now(),
     1000, 400, 1500, 600, 0.20, 100.00, 1, 0.00, 0.00, 100.00, 50.00, 50.00, 50.00,
     '[{"denominacion": 2, "cantidad": 50}]'::jsonb, '[{"denominacion": 2, "cantidad": 25}]'::jsonb,
     0.00, 'idem-p3a-recAt', 'instalacion_base');

-- L_lectura: lectura_no_recaudada HOY.
INSERT INTO public.lectura_no_recaudada (id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_actual, contador_salidas_actual, bruto_estimado, tasa_estimada) VALUES
    ('c1808000-0000-0000-0000-000000000081', 'c1808000-0000-0000-0000-000000000001',
     'c1808000-0000-0000-0000-000000000062', 'c1808000-0000-0000-0000-0000000000a2', now(),
     1100, 450, 10.00, 50.00);

SET LOCAL ROLE authenticated;

-- --- tecA: estados día-independientes -----------------------------------------
SET LOCAL request.jwt.claims = '{"sub":"c1808000-0000-0000-0000-0000000000a2","role":"authenticated"}';
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000031'),
          'toca_hoy',       'vista L_hoy: F=hoy → toca_hoy');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000033'),
          'al_dia',         'vista L_futuro: F=hoy+14 → al_dia (no empieza)');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000034'),
          'al_dia',         'vista L_atendido: recaudado hoy → al_dia');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000035'),
          'al_dia',         'vista L_lectura: lectura hoy → al_dia');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000036'),
          'sin_planificar', 'vista L_sinplan: sin calendario → sin_planificar');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000038'),
          'toca_hoy',       'vista L_bisem: F=hoy, cada 2 sem → objetivo=hoy → toca_hoy');
SELECT is((SELECT fecha_programada_vigente FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000038'),
          (now() AT TIME ZONE 'UTC')::date, 'vista L_bisem: fecha_programada_vigente = hoy (día objetivo)');

-- RLS: tecA NO ve el local de owner, y ve exactamente sus 6.
SELECT is((SELECT count(*) FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000037'),
          0::bigint, 'tecA no ve L_otro en la agenda (RLS)');
SELECT is((SELECT count(*) FROM public.v_agenda_operario), 6::bigint, 'tecA ve sus 6 locales');

-- owner ve los 7.
SET LOCAL request.jwt.claims = '{"sub":"c1808000-0000-0000-0000-0000000000a1","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.v_agenda_operario), 7::bigint, 'owner ve los 7 locales');

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;
