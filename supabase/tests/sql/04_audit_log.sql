-- =============================================================================
-- T-202 — Tests de la auditoría (`audit_log`, `registrar_auditoria` y triggers).
--
-- Cubre:
--   * registrar_auditoria inserta un evento con los campos esperados.
--   * Trigger de instalacion: INSERT -> instalacion_creada; cierre -> instalacion_cerrada.
--   * Trigger de recaudacion: INSERT -> recaudacion_creada; conflicto -> conflicto_detectado.
--   * Trigger de recaudacion: anulación -> recaudacion_anulada.
--   * Trigger de cambio_placa: INSERT -> cambio_placa_creado.
--
-- Nota: auth.uid() es NULL en el contexto de test (sin JWT), por lo que el
-- actor_usuario_id queda NULL; verificamos la acción y la entidad, no el actor.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(9);

-- Setup base: empresa, usuario, licencia, maquina, local
INSERT INTO auth.users (id) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin)
    VALUES ('e0040000-0000-0000-0000-000000000001', 'Test Empresa', now(), now() + interval '30 days');
INSERT INTO public.usuario (id, nombre_completo)
    VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Test Tecnico');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol)
    VALUES ('e0040000-0000-0000-0000-000000000001',
            'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'tecnico');
INSERT INTO public.licencia (id, empresa_id, numero)
    VALUES ('e0040000-0000-0000-0000-000000000010',
            'e0040000-0000-0000-0000-000000000001', 'L-T-01');
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito)
    VALUES ('e0040000-0000-0000-0000-000000000020',
            'e0040000-0000-0000-0000-000000000001', 'M-T-01', 0.20);
INSERT INTO public.local (id, empresa_id, nombre)
    VALUES ('e0040000-0000-0000-0000-000000000030',
            'e0040000-0000-0000-0000-000000000001', 'Bar Test');

-- ---------------------------------------------------------------- 1) función directa
SELECT public.registrar_auditoria(
    'e0040000-0000-0000-0000-000000000001',
    'usuario_invitado',
    'empresa_usuario',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '{"rol":"tecnico"}'::jsonb
);

SELECT is(
    (SELECT count(*) FROM public.audit_log
      WHERE accion = 'usuario_invitado'
        AND entidad_tabla = 'empresa_usuario'),
    1::bigint,
    'registrar_auditoria inserta el evento usuario_invitado'
);

SELECT is(
    (SELECT datos->>'rol' FROM public.audit_log WHERE accion = 'usuario_invitado'),
    'tecnico',
    'registrar_auditoria guarda datos jsonb'
);

-- ---------------------------------------------------------------- 2) instalacion creada
INSERT INTO public.instalacion (
    id, empresa_id, maquina_id, licencia_id, local_id, fecha_inicio,
    tasa_semanal, porcentaje_local,
    contador_entradas_base, contador_salidas_base
) VALUES (
    'e0040000-0000-0000-0000-000000000040',
    'e0040000-0000-0000-0000-000000000001',
    'e0040000-0000-0000-0000-000000000020',
    'e0040000-0000-0000-0000-000000000010',
    'e0040000-0000-0000-0000-000000000030',
    '2026-05-01', 10.00, 50.00, 1000, 500
);

SELECT is(
    (SELECT count(*) FROM public.audit_log
      WHERE accion = 'instalacion_creada'
        AND entidad_id = 'e0040000-0000-0000-0000-000000000040'),
    1::bigint,
    'Trigger: alta de instalación -> instalacion_creada'
);

-- ---------------------------------------------------------------- 3) instalacion cerrada
UPDATE public.instalacion
   SET estado = 'cerrada', fecha_fin = '2026-06-01'
 WHERE id = 'e0040000-0000-0000-0000-000000000040';

SELECT is(
    (SELECT count(*) FROM public.audit_log
      WHERE accion = 'instalacion_cerrada'
        AND entidad_id = 'e0040000-0000-0000-0000-000000000040'),
    1::bigint,
    'Trigger: cierre de instalación -> instalacion_cerrada'
);

-- Reabrimos para poder seguir creando recaudaciones (índice único parcial).
UPDATE public.instalacion
   SET estado = 'activa', fecha_fin = NULL
 WHERE id = 'e0040000-0000-0000-0000-000000000040';

-- ---------------------------------------------------------------- 4) recaudacion creada (sin conflicto)
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen,
    conflicto
) VALUES (
    'e0040000-0000-0000-0000-000000000050',
    'e0040000-0000-0000-0000-000000000001',
    'e0040000-0000-0000-0000-000000000040',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '2026-05-10 10:00+02',
    1000, 500, 1500, 700,
    0.20, 60.00, 1, 10.00, 10.00, 50.00, 50.00, 25.00, 25.00,
    '[{"denominacion":50,"cantidad":1},{"denominacion":10,"cantidad":1}]'::jsonb,
    '[{"denominacion":20,"cantidad":1},{"denominacion":5,"cantidad":1}]'::jsonb,
    'idem-audit-1', 'instalacion_base', false
);

SELECT is(
    (SELECT count(*) FROM public.audit_log
      WHERE accion = 'recaudacion_creada'
        AND entidad_id = 'e0040000-0000-0000-0000-000000000050'),
    1::bigint,
    'Trigger: alta de recaudación -> recaudacion_creada'
);

SELECT is(
    (SELECT count(*) FROM public.audit_log
      WHERE accion = 'conflicto_detectado'
        AND entidad_id = 'e0040000-0000-0000-0000-000000000050'),
    0::bigint,
    'Sin conflicto: no se registra conflicto_detectado'
);

-- ---------------------------------------------------------------- 5) recaudacion con conflicto
INSERT INTO public.recaudacion (
    id, empresa_id, instalacion_id, tecnico_id, fecha,
    contador_entradas_anterior, contador_salidas_anterior,
    contador_entradas_actual, contador_salidas_actual,
    valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
    tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
    porcentaje_local_aplicado, parte_local, parte_empresa,
    desglose_total, desglose_local, idempotency_key, baseline_origen, baseline_id,
    conflicto, bruto_recalculado, neto_recalculado,
    parte_local_recalculada, parte_empresa_recalculada
) VALUES (
    'e0040000-0000-0000-0000-000000000051',
    'e0040000-0000-0000-0000-000000000001',
    'e0040000-0000-0000-0000-000000000040',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '2026-05-17 10:00+02',
    1500, 700, 2000, 900,
    0.20, 60.00, 1, 10.00, 10.00, 50.00, 50.00, 25.00, 25.00,
    '[{"denominacion":50,"cantidad":1},{"denominacion":10,"cantidad":1}]'::jsonb,
    '[{"denominacion":20,"cantidad":1},{"denominacion":5,"cantidad":1}]'::jsonb,
    'idem-audit-2', 'recaudacion_anterior', 'e0040000-0000-0000-0000-000000000050', true,
    60.00, 50.00, 25.00, 25.00
);

SELECT is(
    (SELECT count(*) FROM public.audit_log
      WHERE accion = 'conflicto_detectado'
        AND entidad_id = 'e0040000-0000-0000-0000-000000000051'),
    1::bigint,
    'Trigger: recaudación con conflicto -> conflicto_detectado'
);

-- ---------------------------------------------------------------- 6) anulación
UPDATE public.recaudacion
   SET estado = 'anulada',
       motivo_anulacion = 'Test anulación auditoría',
       anulada_por = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
       anulada_en = now()
 WHERE id = 'e0040000-0000-0000-0000-000000000050';

SELECT is(
    (SELECT count(*) FROM public.audit_log
      WHERE accion = 'recaudacion_anulada'
        AND entidad_id = 'e0040000-0000-0000-0000-000000000050'),
    1::bigint,
    'Trigger: anulación de recaudación -> recaudacion_anulada'
);

-- ---------------------------------------------------------------- 7) cambio de placa
INSERT INTO public.cambio_placa (
    id, empresa_id, instalacion_id, fecha, usuario_id,
    contador_entradas_nuevo, contador_salidas_nuevo
) VALUES (
    'e0040000-0000-0000-0000-000000000060',
    'e0040000-0000-0000-0000-000000000001',
    'e0040000-0000-0000-0000-000000000040',
    '2026-05-20 10:00+02',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    0, 0
);

SELECT is(
    (SELECT count(*) FROM public.audit_log
      WHERE accion = 'cambio_placa_creado'
        AND entidad_id = 'e0040000-0000-0000-0000-000000000060'),
    1::bigint,
    'Trigger: alta de cambio de placa -> cambio_placa_creado'
);

SELECT * FROM finish();
ROLLBACK;
