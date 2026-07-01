-- =============================================================================
-- T-268 — Catálogo GLOBAL fabricante/modelo: alta idempotente, dedup por nombre
-- normalizado, cascada modelo⊂fabricante, y guard de permiso (solo gestor).
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(6);

-- ---- fixtures: un gestor (owner) y un no-gestor (tecnico) en una empresa
INSERT INTO auth.users (id) VALUES
    ('f1000000-0000-0000-0000-0000000000a1'),   -- gestor
    ('f1000000-0000-0000-0000-0000000000a2');   -- tecnico (no gestor)
INSERT INTO public.usuario (id, nombre_completo) VALUES
    ('f1000000-0000-0000-0000-0000000000a1', 'Gestor'),
    ('f1000000-0000-0000-0000-0000000000a2', 'Tecnico');
INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('f1000000-0000-0000-0000-000000000001', 'Test Catalogo', 'UTC', now(), now() + interval '30 days');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('f1000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-0000000000a1', 'owner',   true),
    ('f1000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-0000000000a2', 'tecnico', true);

-- ---- estructura
SELECT has_table('public', 'fabricante', 'existe la tabla fabricante');
SELECT has_table('public', 'modelo', 'existe la tabla modelo');

-- ---- actuar como cliente autenticado
SET LOCAL ROLE authenticated;

-- guard: un no-gestor NO puede dar de alta
SET LOCAL request.jwt.claims = '{"sub":"f1000000-0000-0000-0000-0000000000a2","role":"authenticated"}';
SELECT throws_ok(
    $$ SELECT public.crear_fabricante('Prohibido') $$,
    '42501',
    NULL,
    'un no-gestor no puede crear fabricante (42501)'
);

-- gestor: alta idempotente + dedup por nombre normalizado
SET LOCAL request.jwt.claims = '{"sub":"f1000000-0000-0000-0000-0000000000a1","role":"authenticated"}';
SELECT is(
    public.crear_fabricante('Cirsa'),
    public.crear_fabricante('CIRSA'),
    'crear_fabricante es idempotente y deduplica por nombre normalizado'
);
SELECT is(
    public.crear_modelo(
        (SELECT id FROM public.fabricante WHERE nombre_normalizado = 'cirsa'),
        'Super Bar'
    ),
    public.crear_modelo(
        (SELECT id FROM public.fabricante WHERE nombre_normalizado = 'cirsa'),
        'super bar'
    ),
    'crear_modelo deduplica por (fabricante, nombre normalizado)'
);
SELECT throws_ok(
    $$ SELECT public.crear_modelo('00000000-0000-0000-0000-000000000000'::uuid, 'X') $$,
    '23503',
    NULL,
    'crear_modelo con fabricante inexistente falla (23503)'
);

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;
