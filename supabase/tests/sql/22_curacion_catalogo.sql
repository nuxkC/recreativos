-- =============================================================================
-- T-275 — Curación del catálogo GLOBAL: renombrar/fusionar fabricante y modelo,
-- restringido a admins de catálogo (usuario.es_admin_catalogo). Verifica el
-- guard de permiso, el reflow del texto denormalizado (maquina.fabricante/
-- maquina.modelo), las colisiones al renombrar y la fusión (repunte de FK +
-- borrado del absorbido + dedup de modelos colisionantes).
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(18);

-- ---- fixtures: una empresa con dos gestores (owner), uno admin de catálogo y
-- otro no; catálogo con 2 fabricantes (A con Alpha/Beta/Shared, B con Shared, un
-- nombre de modelo que colisiona al fusionar A→B) y máquinas que los referencian.
INSERT INTO auth.users (id) VALUES
    ('c2000000-0000-0000-0000-0000000000a1'),   -- admin de catálogo
    ('c2000000-0000-0000-0000-0000000000a2');   -- gestor normal (no admin)
INSERT INTO public.usuario (id, nombre_completo, es_admin_catalogo) VALUES
    ('c2000000-0000-0000-0000-0000000000a1', 'Admin Catalogo', true),
    ('c2000000-0000-0000-0000-0000000000a2', 'Gestor Normal',  false);
INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('c2000000-0000-0000-0000-000000000001', 'Test Curacion', 'UTC', now(), now() + interval '30 days');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('c2000000-0000-0000-0000-000000000001', 'c2000000-0000-0000-0000-0000000000a1', 'owner', true),
    ('c2000000-0000-0000-0000-000000000001', 'c2000000-0000-0000-0000-0000000000a2', 'owner', true);

INSERT INTO public.fabricante (id, nombre) VALUES
    ('c2000000-0000-0000-0000-0000000000f1', 'Fab A'),
    ('c2000000-0000-0000-0000-0000000000f2', 'Fab B');
INSERT INTO public.modelo (id, fabricante_id, nombre) VALUES
    ('c2000000-0000-0000-0000-0000000000d1', 'c2000000-0000-0000-0000-0000000000f1', 'Alpha'),
    ('c2000000-0000-0000-0000-0000000000d2', 'c2000000-0000-0000-0000-0000000000f1', 'Beta'),
    ('c2000000-0000-0000-0000-0000000000d3', 'c2000000-0000-0000-0000-0000000000f1', 'Shared'),
    ('c2000000-0000-0000-0000-0000000000d4', 'c2000000-0000-0000-0000-0000000000f2', 'Shared');
INSERT INTO public.maquina (id, empresa_id, numero_serie, modelo, fabricante, valor_credito, fabricante_id, modelo_id) VALUES
    ('c2000000-0000-0000-0000-0000000000e1', 'c2000000-0000-0000-0000-000000000001', 'MA1', 'Alpha',  'Fab A', 0.20, 'c2000000-0000-0000-0000-0000000000f1', 'c2000000-0000-0000-0000-0000000000d1'),
    ('c2000000-0000-0000-0000-0000000000e2', 'c2000000-0000-0000-0000-000000000001', 'MA2', 'Beta',   'Fab A', 0.20, 'c2000000-0000-0000-0000-0000000000f1', 'c2000000-0000-0000-0000-0000000000d2'),
    ('c2000000-0000-0000-0000-0000000000e3', 'c2000000-0000-0000-0000-000000000001', 'MA3', 'Shared', 'Fab A', 0.20, 'c2000000-0000-0000-0000-0000000000f1', 'c2000000-0000-0000-0000-0000000000d3'),
    ('c2000000-0000-0000-0000-0000000000e4', 'c2000000-0000-0000-0000-000000000001', 'MB1', 'Shared', 'Fab B', 0.20, 'c2000000-0000-0000-0000-0000000000f2', 'c2000000-0000-0000-0000-0000000000d4');

-- ---- actuar como cliente autenticado
SET LOCAL ROLE authenticated;

-- ============================ 1) GUARD: un NO admin no puede curar ============
SET LOCAL request.jwt.claims = '{"sub":"c2000000-0000-0000-0000-0000000000a2","role":"authenticated"}';
SELECT throws_ok(
    $$ SELECT public.renombrar_fabricante('c2000000-0000-0000-0000-0000000000f1'::uuid, 'X') $$,
    '42501',
    NULL,
    'un no-admin no puede renombrar_fabricante (42501)'
);
SELECT throws_ok(
    $$ SELECT public.fusionar_fabricante('c2000000-0000-0000-0000-0000000000f1'::uuid, 'c2000000-0000-0000-0000-0000000000f2'::uuid) $$,
    '42501',
    NULL,
    'un no-admin no puede fusionar_fabricante (42501)'
);

-- ============================ admin de catálogo desde aquí ====================
SET LOCAL request.jwt.claims = '{"sub":"c2000000-0000-0000-0000-0000000000a1","role":"authenticated"}';

-- ---- 2) renombrar_fabricante: cambia el nombre canónico Y reescribe el texto
-- denormalizado maquina.fabricante de TODAS las máquinas del fabricante.
SELECT public.renombrar_fabricante('c2000000-0000-0000-0000-0000000000f1'::uuid, 'Fab A Editado');
SELECT is(
    (SELECT nombre FROM public.fabricante WHERE id = 'c2000000-0000-0000-0000-0000000000f1'::uuid),
    'Fab A Editado',
    'renombrar_fabricante cambia el nombre canónico'
);
SELECT results_eq(
    $$ SELECT DISTINCT fabricante FROM public.maquina WHERE fabricante_id = 'c2000000-0000-0000-0000-0000000000f1'::uuid $$,
    $$ VALUES ('Fab A Editado'::text) $$,
    'renombrar_fabricante reescribe maquina.fabricante (reflow denormalizado)'
);

-- ---- 3) renombrar_fabricante colisión: renombrar A al normalizado de B → 23505.
SELECT throws_ok(
    $$ SELECT public.renombrar_fabricante('c2000000-0000-0000-0000-0000000000f1'::uuid, 'Fab B') $$,
    '23505',
    NULL,
    'renombrar_fabricante a un nombre ya existente exige fusionar (23505)'
);

-- ---- 4) renombrar_modelo: reescribe maquina.modelo; colisión dentro del mismo
-- fabricante → 23505.
SELECT public.renombrar_modelo('c2000000-0000-0000-0000-0000000000d1'::uuid, 'Alpha Editado');
SELECT is(
    (SELECT modelo FROM public.maquina WHERE numero_serie = 'MA1'),
    'Alpha Editado',
    'renombrar_modelo reescribe maquina.modelo (reflow denormalizado)'
);
SELECT throws_ok(
    $$ SELECT public.renombrar_modelo('c2000000-0000-0000-0000-0000000000d2'::uuid, 'Alpha Editado') $$,
    '23505',
    NULL,
    'renombrar_modelo a otro modelo del mismo fabricante exige fusionar (23505)'
);

-- ---- 5) fusionar_modelo: exige mismo fabricante (cross-fabricante → 22023);
-- las máquinas del origen quedan con modelo_id/modelo del destino y el origen
-- desaparece.
SELECT throws_ok(
    $$ SELECT public.fusionar_modelo('c2000000-0000-0000-0000-0000000000d3'::uuid, 'c2000000-0000-0000-0000-0000000000d4'::uuid) $$,
    '22023',
    NULL,
    'fusionar_modelo entre fabricantes distintos falla (22023)'
);
SELECT public.fusionar_modelo('c2000000-0000-0000-0000-0000000000d2'::uuid, 'c2000000-0000-0000-0000-0000000000d1'::uuid);
SELECT is(
    (SELECT modelo_id FROM public.maquina WHERE numero_serie = 'MA2'),
    'c2000000-0000-0000-0000-0000000000d1'::uuid,
    'fusionar_modelo repunta el modelo_id de la máquina al destino'
);
SELECT is(
    (SELECT modelo FROM public.maquina WHERE numero_serie = 'MA2'),
    'Alpha Editado',
    'fusionar_modelo reescribe el texto denormalizado al nombre del destino'
);
SELECT is_empty(
    $$ SELECT 1 FROM public.modelo WHERE id = 'c2000000-0000-0000-0000-0000000000d2'::uuid $$,
    'fusionar_modelo borra el modelo origen absorbido'
);

-- ---- 6) fusionar_fabricante: repunta FK+texto de las máquinas del origen,
-- mueve los modelos no colisionantes al destino, fusiona (repunta+borra) los que
-- colisionan y borra el fabricante absorbido.
SELECT public.fusionar_fabricante('c2000000-0000-0000-0000-0000000000f1'::uuid, 'c2000000-0000-0000-0000-0000000000f2'::uuid);
SELECT is(
    (SELECT fabricante_id FROM public.maquina WHERE numero_serie = 'MA1'),
    'c2000000-0000-0000-0000-0000000000f2'::uuid,
    'fusionar_fabricante repunta el fabricante_id de la máquina al destino'
);
SELECT is(
    (SELECT fabricante FROM public.maquina WHERE numero_serie = 'MA1'),
    'Fab B',
    'fusionar_fabricante reescribe maquina.fabricante al nombre del destino'
);
SELECT is(
    (SELECT fabricante_id FROM public.modelo WHERE id = 'c2000000-0000-0000-0000-0000000000d1'::uuid),
    'c2000000-0000-0000-0000-0000000000f2'::uuid,
    'un modelo sin colisión pasa a colgar del fabricante destino'
);
SELECT is(
    (SELECT modelo_id FROM public.maquina WHERE numero_serie = 'MA3'),
    'c2000000-0000-0000-0000-0000000000d4'::uuid,
    'un modelo colisionante se fusiona: la máquina repunta al modelo del destino'
);
SELECT is(
    (SELECT modelo FROM public.maquina WHERE numero_serie = 'MA3'),
    'Shared',
    'la máquina del modelo fusionado toma el texto del modelo destino'
);
SELECT is_empty(
    $$ SELECT 1 FROM public.modelo WHERE id = 'c2000000-0000-0000-0000-0000000000d3'::uuid $$,
    'el modelo colisionante del origen se borra tras fusionarse'
);
SELECT is_empty(
    $$ SELECT 1 FROM public.fabricante WHERE id = 'c2000000-0000-0000-0000-0000000000f1'::uuid $$,
    'fusionar_fabricante borra el fabricante origen absorbido'
);

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;
