-- =============================================================================
-- Planificación P3a — vista de agenda v_agenda_operario (estado "¿toca?").
--
-- Verifica el cálculo de estado (sin_planificar/al_dia/toca_hoy/atrasado), la
-- fecha programada vigente, y que la RLS estricta (P2) fluye por la vista
-- (security_invoker): el técnico solo ve sus locales.
--
-- Setup como superusuario; las aserciones bajan a rol authenticated + jwt para
-- que la RLS aplique. Empresa con zona_horaria='UTC' → "hoy" determinista =
-- (now() AT TIME ZONE 'UTC')::date. Las fechas de inicio se fijan relativas a él.
-- Namespace de UUID: c1808…. BEGIN..ROLLBACK, sin depender de seed.sql.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(12);

-- --- SETUP (superusuario) ----------------------------------------------------
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

-- Locales de tecA (toca_hoy / atrasado / futuro / atendido / lectura / sin plan)
-- y uno de owner (RLS). Fechas relativas al "hoy" en UTC.
INSERT INTO public.local (id, empresa_id, nombre, operario_id, cadencia_semanas, fecha_inicio_recaudacion) VALUES
    ('c1808000-0000-0000-0000-000000000031', 'c1808000-0000-0000-0000-000000000001', 'L_hoy',      'c1808000-0000-0000-0000-0000000000a2', 1, (now() AT TIME ZONE 'UTC')::date),
    ('c1808000-0000-0000-0000-000000000032', 'c1808000-0000-0000-0000-000000000001', 'L_atras',    'c1808000-0000-0000-0000-0000000000a2', 1, (now() AT TIME ZONE 'UTC')::date - 10),
    ('c1808000-0000-0000-0000-000000000033', 'c1808000-0000-0000-0000-000000000001', 'L_futuro',   'c1808000-0000-0000-0000-0000000000a2', 1, (now() AT TIME ZONE 'UTC')::date + 7),
    ('c1808000-0000-0000-0000-000000000034', 'c1808000-0000-0000-0000-000000000001', 'L_atendido', 'c1808000-0000-0000-0000-0000000000a2', 1, (now() AT TIME ZONE 'UTC')::date),
    ('c1808000-0000-0000-0000-000000000035', 'c1808000-0000-0000-0000-000000000001', 'L_lectura',  'c1808000-0000-0000-0000-0000000000a2', 1, (now() AT TIME ZONE 'UTC')::date),
    ('c1808000-0000-0000-0000-000000000036', 'c1808000-0000-0000-0000-000000000001', 'L_sinplan',  'c1808000-0000-0000-0000-0000000000a2', NULL, NULL),
    ('c1808000-0000-0000-0000-000000000037', 'c1808000-0000-0000-0000-000000000001', 'L_otro',     'c1808000-0000-0000-0000-0000000000a1', 1, (now() AT TIME ZONE 'UTC')::date + 7);

-- Solo L_atendido y L_lectura necesitan instalación (para colgar la visita).
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

-- L_atendido: recaudación firme HOY (dentro de [S=hoy, hoy]).
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

-- A partir de aquí, RLS: rol authenticated.
SET LOCAL ROLE authenticated;

-- --- tecA: estados por local --------------------------------------------------
SET LOCAL request.jwt.claims = '{"sub":"c1808000-0000-0000-0000-0000000000a2","role":"authenticated"}';
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000031'),
          'toca_hoy',       'L_hoy: toca hoy');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000032'),
          'atrasado',       'L_atras: atrasado');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000033'),
          'al_dia',         'L_futuro: al dia (aun no empieza)');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000034'),
          'al_dia',         'L_atendido: al dia (recaudado en ciclo)');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000035'),
          'al_dia',         'L_lectura: al dia (lectura en ciclo)');
SELECT is((SELECT estado FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000036'),
          'sin_planificar', 'L_sinplan: sin planificar');
SELECT is((SELECT fecha_programada_vigente FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000032'),
          (now() AT TIME ZONE 'UTC')::date - 3, 'L_atras: fecha programada vigente = hoy-3');

-- RLS: tecA NO ve el local de owner, y ve exactamente sus 6.
SELECT is((SELECT count(*) FROM public.v_agenda_operario WHERE local_id = 'c1808000-0000-0000-0000-000000000037'),
          0::bigint, 'tecA no ve L_otro en la agenda (RLS)');
SELECT is((SELECT count(*) FROM public.v_agenda_operario), 6::bigint, 'tecA ve sus 6 locales');

-- --- owner (ve-todo): ve los 7 y los recuentos de estado --------------------
SET LOCAL request.jwt.claims = '{"sub":"c1808000-0000-0000-0000-0000000000a1","role":"authenticated"}';
SELECT is((SELECT count(*) FROM public.v_agenda_operario), 7::bigint, 'owner ve los 7 locales');
SELECT is((SELECT count(*) FROM public.v_agenda_operario WHERE estado = 'toca_hoy'), 1::bigint, 'owner: 1 toca_hoy (L_hoy)');
SELECT is((SELECT count(*) FROM public.v_agenda_operario WHERE estado = 'atrasado'), 1::bigint, 'owner: 1 atrasado (L_atras)');

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;
