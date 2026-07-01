-- =============================================================================
-- T-274 — La licencia pierde el "tipo" y su CCAA queda restringida a la lista
-- cerrada de las 19 comunidades autónomas.
--
-- Cubre:
--   * La columna `tipo` se eliminó de licencia.
--   * crear_licencia CONSERVA su firma exacta (p_tipo inerte, no-breaking).
--   * INSERT con una CCAA válida ('Cataluña') pasa.
--   * INSERT con CCAA NULL pasa (permitido).
--   * INSERT con una CCAA fuera de la lista es rechazado por el CHECK (23514).
--
-- BEGIN..ROLLBACK, sin depender de seed.sql. UUID con namespace `c1274…`.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

SELECT plan(5);

-- --- Datos mínimos -----------------------------------------------------------
INSERT INTO public.empresa (id, nombre, trial_inicio, trial_fin)
    VALUES ('c1274000-0000-0000-0000-000000000001', 'Test Empresa T-274',
            now(), now() + interval '30 days');

-- 1. La columna `tipo` ya no existe.
SELECT hasnt_column('public', 'licencia', 'tipo',
    'licencia ya no tiene la columna tipo');

-- 2. crear_licencia conserva su firma exacta (p_tipo inerte, no-breaking).
SELECT has_function(
    'public', 'crear_licencia',
    ARRAY['uuid', 'text', 'text', 'date', 'date', 'text', 'text', 'text'],
    'crear_licencia conserva su firma con p_tipo inerte'
);

-- 3. CCAA válida de la lista cerrada.
SELECT lives_ok($$
    INSERT INTO public.licencia (empresa_id, numero, comunidad_autonoma)
    VALUES ('c1274000-0000-0000-0000-000000000001', 'LIC-CAT', 'Cataluña')
$$, 'INSERT con CCAA válida (Cataluña) pasa el CHECK');

-- 4. CCAA NULL permitida.
SELECT lives_ok($$
    INSERT INTO public.licencia (empresa_id, numero, comunidad_autonoma)
    VALUES ('c1274000-0000-0000-0000-000000000001', 'LIC-NULL', NULL)
$$, 'INSERT con CCAA NULL pasa (permitido)');

-- 5. CCAA fuera de la lista → rechazada por el CHECK (23514 check_violation).
SELECT throws_ok($$
    INSERT INTO public.licencia (empresa_id, numero, comunidad_autonoma)
    VALUES ('c1274000-0000-0000-0000-000000000001', 'LIC-BAD', 'Comunidad Inventada')
$$, '23514', NULL,
    'INSERT con CCAA inventada es rechazado por el CHECK');

SELECT * FROM finish();
ROLLBACK;
