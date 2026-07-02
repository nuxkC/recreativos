-- =============================================================================
-- T-277 — Referencia geográfica GLOBAL: provincia (52) y municipio (8132).
-- Tablas de solo lectura sembradas del INE. Verifica estructura, recuento,
-- integridad referencial municipio→provincia, coherencia de comunidad_autonoma
-- con la lista de oro (19 CCAA), y el patrón de acceso del catálogo global:
-- authenticated puede LEER pero NO escribir, anon no puede ni leer.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(13);

-- ---- fixtures: un usuario autenticado en una empresa (patrón del catálogo)
INSERT INTO auth.users (id) VALUES
    ('f2000000-0000-0000-0000-0000000000a1');
INSERT INTO public.usuario (id, nombre_completo) VALUES
    ('f2000000-0000-0000-0000-0000000000a1', 'Autenticado');
INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('f2000000-0000-0000-0000-000000000001', 'Test Geo', 'UTC', now(), now() + interval '30 days');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo) VALUES
    ('f2000000-0000-0000-0000-000000000001', 'f2000000-0000-0000-0000-0000000000a1', 'owner', true);

-- ---- estructura
SELECT has_table('public', 'provincia', 'existe la tabla provincia');
SELECT has_table('public', 'municipio', 'existe la tabla municipio');

-- ---- recuento sembrado (INE, 1 enero 2025)
SELECT is(
    (SELECT count(*) FROM public.provincia),
    52::bigint,
    'hay 52 provincias sembradas'
);
SELECT is(
    (SELECT count(*) FROM public.municipio),
    8132::bigint,
    'hay 8132 municipios sembrados'
);

-- ---- integridad referencial: ningún municipio apunta a una provincia inexistente
SELECT is(
    (SELECT count(*)
       FROM public.municipio m
       LEFT JOIN public.provincia p ON p.codigo = m.provincia_codigo
      WHERE p.codigo IS NULL),
    0::bigint,
    'ningún municipio queda huérfano de provincia'
);

-- ---- coherencia con la lista de oro de CCAA (idéntica al CHECK de licencia)
SELECT is(
    (SELECT count(DISTINCT comunidad_autonoma) FROM public.provincia),
    19::bigint,
    'las provincias abarcan exactamente 19 comunidades autónomas'
);
SELECT is(
    (SELECT count(*) FROM public.provincia
      WHERE comunidad_autonoma NOT IN (
          'Andalucía', 'Aragón', 'Asturias', 'Islas Baleares', 'Canarias',
          'Cantabria', 'Castilla-La Mancha', 'Castilla y León', 'Cataluña',
          'Comunidad Valenciana', 'Extremadura', 'Galicia', 'Madrid', 'Murcia',
          'Navarra', 'País Vasco', 'La Rioja', 'Ceuta', 'Melilla'
      )),
    0::bigint,
    'toda comunidad_autonoma pertenece a la lista de oro'
);

-- ---- actuar como cliente autenticado
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claims = '{"sub":"f2000000-0000-0000-0000-0000000000a1","role":"authenticated"}';

-- authenticated PUEDE leer (RLS FOR SELECT USING(true))
SELECT is(
    (SELECT count(*) FROM public.provincia),
    52::bigint,
    'authenticated puede leer provincia'
);
SELECT is(
    (SELECT count(*) FROM public.municipio),
    8132::bigint,
    'authenticated puede leer municipio'
);

-- authenticated NO puede escribir directamente (solo se concedió SELECT)
SELECT throws_ok(
    $$ INSERT INTO public.provincia (codigo, nombre, comunidad_autonoma)
       VALUES ('99', 'Prohibida', 'Madrid') $$,
    '42501',
    NULL,
    'authenticated NO puede INSERT directo en provincia (42501)'
);
SELECT throws_ok(
    $$ INSERT INTO public.municipio (codigo, nombre, provincia_codigo)
       VALUES ('99999', 'Prohibido', '28') $$,
    '42501',
    NULL,
    'authenticated NO puede INSERT directo en municipio (42501)'
);

-- anon NO puede leer (permiso revocado)
RESET ROLE;
SET LOCAL ROLE anon;
SELECT throws_ok(
    $$ SELECT 1 FROM public.provincia $$,
    '42501',
    NULL,
    'anon NO puede leer provincia (permiso denegado)'
);
SELECT throws_ok(
    $$ SELECT 1 FROM public.municipio $$,
    '42501',
    NULL,
    'anon NO puede leer municipio (permiso denegado)'
);

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;
