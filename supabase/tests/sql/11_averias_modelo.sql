-- =============================================================================
-- T-220 — Tests del modelo de averías (averia + averia_recambio + RPCs).
--
-- Cubre:
--   * crear_averia: alta, estado abierta, autor, y SNAPSHOT instalacion/local
--     derivado de la instalación activa de la máquina.
--   * pone_maquina_fuera_servicio: pone/quita maquina.estado='averiada' vía
--     recalcular_estado_maquina; un fallo leve no la toca.
--   * resolver_averia: cierre (estado/fecha/usuario) y vuelta al estado operativo
--     (instalada si hay instalación activa, si no almacén); rechazo de re-cierre.
--   * crear_recambio / eliminar_recambio.
--   * HISTORIAL por máquina: atraviesa instalaciones (cambio de máquina no
--     reescribe el snapshot de averías pasadas).
--   * Validaciones: categoría inválida (CHECK), cross-tenant, no-miembro.
--
-- Las RPCs son SECURITY DEFINER y validan rol vía auth.uid(); simulamos el JWT
-- con `SET LOCAL request.jwt.claims`. BEGIN..ROLLBACK, sin depender de seed.sql.
-- UUID con namespace `c1220…` (T-220) para no colisionar con datos sembrados.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(28);

-- --- Datos mínimos -----------------------------------------------------------
INSERT INTO auth.users (id) VALUES ('c1220000-0000-0000-0000-0000000000a1');
INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin)
    VALUES ('c1220000-0000-0000-0000-000000000001', 'Test Empresa T-220',
            now(), now() + interval '30 days');
INSERT INTO public.usuario (id, nombre_completo)
    VALUES ('c1220000-0000-0000-0000-0000000000a1', 'Test Owner');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol)
    VALUES ('c1220000-0000-0000-0000-000000000001', 'c1220000-0000-0000-0000-0000000000a1', 'owner');

INSERT INTO public.licencia (id, empresa_id, numero) VALUES
    ('c1220000-0000-0000-0000-000000000010', 'c1220000-0000-0000-0000-000000000001', 'LIC-1'),
    ('c1220000-0000-0000-0000-000000000011', 'c1220000-0000-0000-0000-000000000001', 'LIC-2');
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito,
                            contador_entradas_inicial, contador_salidas_inicial) VALUES
    ('c1220000-0000-0000-0000-000000000020', 'c1220000-0000-0000-0000-000000000001', 'M-1', 0.20, 1000, 400),
    ('c1220000-0000-0000-0000-000000000021', 'c1220000-0000-0000-0000-000000000001', 'M-2', 0.20, 1000, 400);
INSERT INTO public.local (id, empresa_id, nombre)
    VALUES ('c1220000-0000-0000-0000-000000000030', 'c1220000-0000-0000-0000-000000000001', 'Bar Test');

-- Otra empresa con su máquina, para el test cross-tenant.
INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin)
    VALUES ('c1220000-0000-0000-0000-000000000002', 'Otra Empresa T-220',
            now(), now() + interval '30 days');
INSERT INTO public.maquina (id, empresa_id, numero_serie, valor_credito)
    VALUES ('c1220000-0000-0000-0000-000000000022', 'c1220000-0000-0000-0000-000000000002', 'M-3', 0.20);

-- Simula el JWT del owner para que auth.uid() lo reconozca en las RPCs.
SET LOCAL request.jwt.claims = '{"sub":"c1220000-0000-0000-0000-0000000000a1","role":"authenticated"}';

CREATE TEMP TABLE _ids(k text PRIMARY KEY, id uuid) ON COMMIT DROP;

-- M-1 instalada (inst1). crear_instalacion NO toca maquina.estado: sigue 'almacen'.
INSERT INTO _ids(k, id) VALUES
    ('inst1', public.crear_instalacion(
        'c1220000-0000-0000-0000-000000000001',
        'c1220000-0000-0000-0000-000000000020',  -- M-1
        'c1220000-0000-0000-0000-000000000010',  -- LIC-1
        'c1220000-0000-0000-0000-000000000030',  -- local
        '2026-06-10', 12.00, 60.00, NULL, 0, NULL));

-- --- A. crear_averia: alta + snapshot derivado --------------------------------
INSERT INTO _ids(k, id) VALUES
    ('a1', public.crear_averia('c1220000-0000-0000-0000-000000000001',
                               'c1220000-0000-0000-0000-000000000020',
                               'atasco_billete', 'No traga billetes de 20', false, 'leve'));

SELECT is((SELECT estado FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a1')),
          'abierta', 'crear_averia: estado abierta');
SELECT is((SELECT categoria FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a1')),
          'atasco_billete', 'crear_averia: categoría');
SELECT is((SELECT reportada_por FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a1')),
          'c1220000-0000-0000-0000-0000000000a1'::uuid, 'crear_averia: reportada_por = autor');
SELECT is((SELECT instalacion_id FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a1')),
          (SELECT id FROM _ids WHERE k = 'inst1'), 'crear_averia: snapshot instalacion_id derivado');
SELECT is((SELECT local_id FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a1')),
          'c1220000-0000-0000-0000-000000000030'::uuid, 'crear_averia: snapshot local_id derivado');
SELECT is((SELECT estado FROM public.maquina WHERE id = 'c1220000-0000-0000-0000-000000000020'),
          'almacen', 'fallo leve (fuera_servicio=false) no cambia el estado de la máquina');

-- --- B. fuera de servicio pone la máquina 'averiada' --------------------------
INSERT INTO _ids(k, id) VALUES
    ('a2', public.crear_averia('c1220000-0000-0000-0000-000000000001',
                               'c1220000-0000-0000-0000-000000000020',
                               'no_enciende', NULL, true, NULL));
SELECT is((SELECT estado FROM public.maquina WHERE id = 'c1220000-0000-0000-0000-000000000020'),
          'averiada', 'fuera_servicio pone la máquina averiada');

-- --- C. resolver vuelve al estado operativo (instalada) -----------------------
SELECT lives_ok(
    format($$ SELECT public.resolver_averia(%L, 'cambiada la fuente') $$, (SELECT id FROM _ids WHERE k = 'a2')),
    'resolver_averia ejecuta');
SELECT is((SELECT estado FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a2')),
          'resuelta', 'resolver: estado resuelta');
SELECT ok((SELECT fecha_resolucion IS NOT NULL FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a2')),
          'resolver: fecha_resolucion fijada');
SELECT is((SELECT estado FROM public.maquina WHERE id = 'c1220000-0000-0000-0000-000000000020'),
          'instalada', 'resolver: máquina vuelve a instalada (tiene instalación activa)');

-- --- D. re-resolver una avería ya resuelta: rechazo ---------------------------
SELECT throws_ok(
    format($$ SELECT public.resolver_averia(%L) $$, (SELECT id FROM _ids WHERE k = 'a2')),
    '22023', NULL, 'resolver una avería ya resuelta lanza error');

-- --- E. recambios -------------------------------------------------------------
INSERT INTO _ids(k, id) VALUES
    ('r1', public.crear_recambio((SELECT id FROM _ids WHERE k = 'a1'), 'Aceptador de billetes', 1, 45.50, NULL));
SELECT is((SELECT pieza FROM public.averia_recambio WHERE id = (SELECT id FROM _ids WHERE k = 'r1')),
          'Aceptador de billetes', 'crear_recambio: pieza');
SELECT is((SELECT coste FROM public.averia_recambio WHERE id = (SELECT id FROM _ids WHERE k = 'r1')),
          45.50::numeric, 'crear_recambio: coste');
SELECT is((SELECT count(*) FROM public.averia_recambio WHERE averia_id = (SELECT id FROM _ids WHERE k = 'a1')),
          1::bigint, 'crear_recambio: 1 recambio en la avería');
SELECT lives_ok(
    format($$ SELECT public.eliminar_recambio(%L) $$, (SELECT id FROM _ids WHERE k = 'r1')),
    'eliminar_recambio ejecuta');
SELECT is((SELECT count(*) FROM public.averia_recambio WHERE averia_id = (SELECT id FROM _ids WHERE k = 'a1')),
          0::bigint, 'eliminar_recambio: 0 recambios tras borrar');

-- --- F. categoría inválida: CHECK violation -----------------------------------
SELECT throws_ok(
    $$ SELECT public.crear_averia('c1220000-0000-0000-0000-000000000001',
                                  'c1220000-0000-0000-0000-000000000020', 'categoria_invalida') $$,
    '23514', NULL, 'categoría fuera del catálogo lanza check_violation');

-- --- G. HISTORIAL por máquina: atraviesa instalaciones ------------------------
-- Cerramos inst1 y movemos M-1 a una nueva instalación (inst2, otra licencia).
UPDATE public.instalacion SET estado = 'cerrada', fecha_fin = '2026-06-12'
    WHERE id = (SELECT id FROM _ids WHERE k = 'inst1');
INSERT INTO _ids(k, id) VALUES
    ('inst2', public.crear_instalacion(
        'c1220000-0000-0000-0000-000000000001',
        'c1220000-0000-0000-0000-000000000020',  -- M-1
        'c1220000-0000-0000-0000-000000000011',  -- LIC-2
        'c1220000-0000-0000-0000-000000000030',  -- mismo local
        '2026-06-12', 12.00, 60.00, NULL, 0, NULL));
INSERT INTO _ids(k, id) VALUES
    ('a3', public.crear_averia('c1220000-0000-0000-0000-000000000001',
                               'c1220000-0000-0000-0000-000000000020', 'error', NULL, false, NULL));

SELECT is((SELECT count(*) FROM public.averia WHERE maquina_id = 'c1220000-0000-0000-0000-000000000020'),
          3::bigint, 'historial: 3 averías de la máquina atravesando 2 instalaciones');
SELECT is((SELECT instalacion_id FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a1')),
          (SELECT id FROM _ids WHERE k = 'inst1'),
          'historial: el snapshot de la avería antigua NO se re-apunta (sigue en inst1)');
SELECT is((SELECT instalacion_id FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a3')),
          (SELECT id FROM _ids WHERE k = 'inst2'),
          'historial: la avería nueva apunta a la instalación actual (inst2)');

-- --- H. máquina en almacén (sin instalación): snapshot NULL + averiada→almacen -
INSERT INTO _ids(k, id) VALUES
    ('a4', public.crear_averia('c1220000-0000-0000-0000-000000000001',
                               'c1220000-0000-0000-0000-000000000021',  -- M-2, en almacén
                               'atasco_moneda', NULL, true, NULL));
SELECT ok((SELECT instalacion_id IS NULL FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a4')),
          'almacén: snapshot instalacion_id NULL');
SELECT ok((SELECT local_id IS NULL FROM public.averia WHERE id = (SELECT id FROM _ids WHERE k = 'a4')),
          'almacén: snapshot local_id NULL');
SELECT is((SELECT estado FROM public.maquina WHERE id = 'c1220000-0000-0000-0000-000000000021'),
          'averiada', 'almacén: fuera_servicio pone averiada');
SELECT lives_ok(
    format($$ SELECT public.resolver_averia(%L) $$, (SELECT id FROM _ids WHERE k = 'a4')),
    'resolver avería de máquina en almacén ejecuta');
SELECT is((SELECT estado FROM public.maquina WHERE id = 'c1220000-0000-0000-0000-000000000021'),
          'almacen', 'almacén: al resolver vuelve a almacen (sin instalación activa)');

-- --- I. cross-tenant: máquina de otra empresa --------------------------------
SELECT throws_ok(
    $$ SELECT public.crear_averia('c1220000-0000-0000-0000-000000000001',
                                  'c1220000-0000-0000-0000-000000000022', 'otro') $$,
    '23503', NULL, 'crear_averia con máquina de otra empresa lanza foreign_key_violation');

-- --- J. permisos: un no-miembro no puede crear averías ------------------------
SET LOCAL request.jwt.claims = '{"sub":"c1220000-0000-0000-0000-0000000000b2","role":"authenticated"}';
SELECT throws_ok(
    $$ SELECT public.crear_averia('c1220000-0000-0000-0000-000000000001',
                                  'c1220000-0000-0000-0000-000000000020', 'otro') $$,
    '42501', NULL, 'un no-miembro no puede crear_averia');

SELECT * FROM finish();
ROLLBACK;
