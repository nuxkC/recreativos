-- =============================================================================
-- Tests del contador base DERIVADO de la máquina
-- (trigger trg_set_contador_base_instalacion + obtener_contador_actual_maquina).
--
-- Verifica que la base de una instalación se HEREDA de la máquina y NUNCA se
-- teclea: una base inferior a la última lectura recaudada (el "fallo garrafal"
-- al mover la máquina a otro local) es imposible por construcción.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(7);

-- Setup: empresa, usuario, licencia, máquina (con contadores iniciales), locales.
INSERT INTO auth.users (id) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin)
    VALUES ('e0050000-0000-0000-0000-000000000001', 'Test Empresa', now(), now() + interval '30 days');
INSERT INTO public.usuario (id, nombre_completo)
    VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Test Gestor');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol)
    VALUES ('e0050000-0000-0000-0000-000000000001',
            'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'gestor');
INSERT INTO public.licencia (id, empresa_id, numero)
    VALUES ('e0050000-0000-0000-0000-000000000010',
            'e0050000-0000-0000-0000-000000000001', 'L-T-01');
-- Máquina con contadores iniciales 1000/400.
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial)
    VALUES ('e0050000-0000-0000-0000-000000000020',
            'e0050000-0000-0000-0000-000000000001', 'M-T-01', 0.20, 1000, 400);
INSERT INTO public.local (id, empresa_id, nombre)
    VALUES ('e0050000-0000-0000-0000-000000000030',
            'e0050000-0000-0000-0000-000000000001', 'Bar A');
INSERT INTO public.local (id, empresa_id, nombre)
    VALUES ('e0050000-0000-0000-0000-000000000031',
            'e0050000-0000-0000-0000-000000000001', 'Bar B');

-- -----------------------------------------------------------------------------
-- 1ª instalación (Bar A): la máquina no tiene historial. Se INSERTA una base
-- BASURA a propósito para probar que el trigger la sobrescribe con el inicial.
-- -----------------------------------------------------------------------------
INSERT INTO public.instalacion (
    id, empresa_id, maquina_id, licencia_id, local_id, fecha_inicio,
    tasa_semanal, porcentaje_local,
    contador_entradas_base, contador_salidas_base
) VALUES (
    'e0050000-0000-0000-0000-000000000040',
    'e0050000-0000-0000-0000-000000000001',
    'e0050000-0000-0000-0000-000000000020',
    'e0050000-0000-0000-0000-000000000010',
    'e0050000-0000-0000-0000-000000000030',
    '2026-05-01', 10.00, 50.00,
    999999, 888888   -- basura: el trigger debe ignorarla
);

SELECT is(
    (SELECT contador_entradas_base FROM public.instalacion
      WHERE id = 'e0050000-0000-0000-0000-000000000040'),
    1000::bigint,
    'Máquina sin historial: base entradas = contador inicial (base manual ignorada)'
);
SELECT is(
    (SELECT contador_salidas_base FROM public.instalacion
      WHERE id = 'e0050000-0000-0000-0000-000000000040'),
    400::bigint,
    'Máquina sin historial: base salidas = contador inicial'
);
SELECT is(
    (public.obtener_contador_actual_maquina(
        'e0050000-0000-0000-0000-000000000020', '2026-05-05 10:00+02')).origen,
    'maquina_inicial',
    'Sin historial: origen = maquina_inicial'
);

-- -----------------------------------------------------------------------------
-- Recaudación firme: la máquina llega a 2500/900. Luego se cierra la instalación
-- (la máquina se mueve a otro local).
-- -----------------------------------------------------------------------------
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen
) VALUES (
    'e0050000-0000-0000-0000-000000000050',
    'e0050000-0000-0000-0000-000000000001',
    'e0050000-0000-0000-0000-000000000040',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '2026-05-10 10:00+02',
    1000, 400, 2500, 900,
    0.20, 60.00, 1, 10.00, 10.00, 50.00, 50.00, 25.00, 25.00,
    '[{"denominacion":50,"cantidad":1},{"denominacion":10,"cantidad":1}]'::jsonb,
    '[{"denominacion":20,"cantidad":1},{"denominacion":5,"cantidad":1}]'::jsonb,
    'idem-1', 'instalacion_base'
);

UPDATE public.instalacion
   SET estado = 'cerrada', fecha_fin = '2026-05-12'
 WHERE id = 'e0050000-0000-0000-0000-000000000040';

-- -----------------------------------------------------------------------------
-- 2ª instalación de la MISMA máquina en OTRO local (Bar B). La base NO puede ser
-- menor que la última lectura recaudada: hereda 2500/900.
-- -----------------------------------------------------------------------------
INSERT INTO public.instalacion (
    id, empresa_id, maquina_id, licencia_id, local_id, fecha_inicio,
    tasa_semanal, porcentaje_local,
    contador_entradas_base, contador_salidas_base
) VALUES (
    'e0050000-0000-0000-0000-000000000041',
    'e0050000-0000-0000-0000-000000000001',
    'e0050000-0000-0000-0000-000000000020',
    'e0050000-0000-0000-0000-000000000010',
    'e0050000-0000-0000-0000-000000000031',
    '2026-05-20', 12.00, 60.00,
    0, 0   -- intento de base 0 (el "fallo garrafal"): el trigger lo corrige
);

SELECT is(
    (SELECT contador_entradas_base FROM public.instalacion
      WHERE id = 'e0050000-0000-0000-0000-000000000041'),
    2500::bigint,
    'Reinstalación en otro local: base entradas hereda la última lectura recaudada'
);
SELECT is(
    (SELECT contador_salidas_base FROM public.instalacion
      WHERE id = 'e0050000-0000-0000-0000-000000000041'),
    900::bigint,
    'Reinstalación en otro local: base salidas hereda la última lectura recaudada'
);

-- -----------------------------------------------------------------------------
-- Cambio de placa (resetea la máquina a 5/2) y nueva instalación: la base
-- hereda el contador de la placa nueva, no la recaudación anterior.
-- -----------------------------------------------------------------------------
INSERT INTO public.cambio_placa (
    id, empresa_id, instalacion_id, fecha, usuario_id,
    contador_entradas_nuevo, contador_salidas_nuevo
) VALUES (
    'e0050000-0000-0000-0000-000000000060',
    'e0050000-0000-0000-0000-000000000001',
    'e0050000-0000-0000-0000-000000000041',
    '2026-06-01 09:00+02',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    5, 2
);

UPDATE public.instalacion
   SET estado = 'cerrada', fecha_fin = '2026-06-05'
 WHERE id = 'e0050000-0000-0000-0000-000000000041';

INSERT INTO public.instalacion (
    id, empresa_id, maquina_id, licencia_id, local_id, fecha_inicio,
    tasa_semanal, porcentaje_local,
    contador_entradas_base, contador_salidas_base
) VALUES (
    'e0050000-0000-0000-0000-000000000042',
    'e0050000-0000-0000-0000-000000000001',
    'e0050000-0000-0000-0000-000000000020',
    'e0050000-0000-0000-0000-000000000010',
    'e0050000-0000-0000-0000-000000000030',
    '2026-06-10', 12.00, 60.00,
    0, 0
);

SELECT is(
    (SELECT contador_entradas_base FROM public.instalacion
      WHERE id = 'e0050000-0000-0000-0000-000000000042'),
    5::bigint,
    'Reinstalación tras cambio de placa: base entradas hereda la placa nueva'
);
SELECT is(
    (public.obtener_contador_actual_maquina(
        'e0050000-0000-0000-0000-000000000020', '2026-06-10 10:00+02')).origen,
    'cambio_placa',
    'Cambio de placa posterior a la recaudación: origen = cambio_placa'
);

SELECT * FROM finish();
ROLLBACK;
