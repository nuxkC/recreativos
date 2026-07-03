-- =============================================================================
-- T-277 — Dirección estructurada del local: columnas CCAA/provincia/municipio/
-- calle/CP, firma de crear/actualizar_local (12 args, SIN el antiguo p_direccion
-- de texto libre), coherencia jerárquica CCAA ⊃ provincia ⊃ municipio, y la
-- cadena de display DERIVADA por public.direccion(local). Usa los datos de
-- referencia del INE sembrados por la migración (no depende de seed.sql).
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(27);

-- ---- fixtures: una empresa (para insertar locales directos como owner, que
-- bypassa RLS) y un usuario gestor (para invocar las RPC como authenticated).
INSERT INTO public.empresa (id, nombre, zona_horaria, trial_inicio, trial_fin)
    VALUES ('f3000000-0000-0000-0000-000000000001', 'Test Dirección', 'UTC', now(), now() + interval '30 days');
INSERT INTO auth.users (id) VALUES ('f3000000-0000-0000-0000-0000000000a1');
INSERT INTO public.usuario (id, nombre_completo) VALUES ('f3000000-0000-0000-0000-0000000000a1', 'Gestor Test');
INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo)
    VALUES ('f3000000-0000-0000-0000-000000000001', 'f3000000-0000-0000-0000-0000000000a1', 'gestor', true);

-- ---- estructura: columnas de dirección estructurada, y la de texto libre RETIRADA
SELECT has_column('public', 'local', 'comunidad_autonoma', 'local.comunidad_autonoma existe');
SELECT has_column('public', 'local', 'provincia_codigo',   'local.provincia_codigo existe');
SELECT has_column('public', 'local', 'municipio_codigo',   'local.municipio_codigo existe');
SELECT has_column('public', 'local', 'calle',              'local.calle existe');
SELECT has_column('public', 'local', 'codigo_postal',      'local.codigo_postal existe');
SELECT hasnt_column('public', 'local', 'direccion',        'local.direccion (texto libre) ya no existe');

-- ---- firmas de las RPC (12 args: 1 uuid + 11 text) y helpers
SELECT has_function('public', 'crear_local',
    ARRAY['uuid','text','text','text','text','text','text','text','text','text','text','text'],
    'crear_local tiene la firma de 12 argumentos (sin p_direccion)');
SELECT has_function('public', 'actualizar_local',
    ARRAY['uuid','text','text','text','text','text','text','text','text','text','text','text'],
    'actualizar_local tiene la firma de 12 argumentos (sin p_direccion)');
SELECT has_function('public', '_validar_direccion_local',
    ARRAY['text','text','text'],
    'existe el helper de coherencia _validar_direccion_local');
SELECT has_function('public', 'direccion',
    ARRAY['local'],
    'existe la función derivada de display public.direccion(local)');

-- ---- la firma con p_direccion (13 args) YA NO existe
SELECT hasnt_function('public', 'crear_local',
    ARRAY['uuid','text','text','text','text','text','text','text','text','text','text','text','text','text'],
    'la firma de 13 args (con p_direccion) de crear_local ya no existe');
SELECT hasnt_function('public', 'actualizar_local',
    ARRAY['uuid','text','text','text','text','text','text','text','text','text','text','text','text','text'],
    'la firma de 13 args (con p_direccion) de actualizar_local ya no existe');

-- ---- CHECK de comunidad autónoma (lista de oro)
SELECT throws_ok(
    $$ INSERT INTO public.local (empresa_id, nombre, comunidad_autonoma)
       VALUES ('f3000000-0000-0000-0000-000000000001', 'CCAA mala', 'Inventada') $$,
    '23514', NULL,
    'rechaza una comunidad autónoma fuera de la lista de oro (23514)'
);
SELECT lives_ok(
    $$ INSERT INTO public.local (empresa_id, nombre, comunidad_autonoma)
       VALUES ('f3000000-0000-0000-0000-000000000001', 'CCAA buena', 'Madrid') $$,
    'acepta una comunidad autónoma de la lista de oro'
);

-- ---- CHECK de código postal (exactamente 5 dígitos)
SELECT throws_ok(
    $$ INSERT INTO public.local (empresa_id, nombre, codigo_postal)
       VALUES ('f3000000-0000-0000-0000-000000000001', 'CP malo', '1234') $$,
    '23514', NULL,
    'rechaza un código postal que no son 5 dígitos (23514)'
);
SELECT lives_ok(
    $$ INSERT INTO public.local (empresa_id, nombre, codigo_postal)
       VALUES ('f3000000-0000-0000-0000-000000000001', 'CP bueno', '28001') $$,
    'acepta un código postal de 5 dígitos'
);
SELECT lives_ok(
    $$ INSERT INTO public.local (empresa_id, nombre, codigo_postal)
       VALUES ('f3000000-0000-0000-0000-000000000001', 'CP nulo', NULL) $$,
    'acepta código postal NULL (transición)'
);

-- ---- coherencia jerárquica (helper _validar_direccion_local). Códigos INE:
-- Barcelona = municipio 08019 en provincia 08 (Cataluña); Madrid = 28079 en 28.
SELECT lives_ok(
    $$ SELECT public._validar_direccion_local('Cataluña', '08', '08019') $$,
    'acepta Barcelona (08019) en provincia 08 de Cataluña'
);
SELECT lives_ok(
    $$ SELECT public._validar_direccion_local('Madrid', '28', '28079') $$,
    'acepta Madrid (28079) en provincia 28 de Madrid'
);
SELECT lives_ok(
    $$ SELECT public._validar_direccion_local(NULL, NULL, NULL) $$,
    'acepta dirección sin estructurar (todo NULL)'
);
SELECT throws_ok(
    $$ SELECT public._validar_direccion_local('Madrid', '08', '08019') $$,
    '23514', NULL,
    'rechaza provincia 08 (Cataluña) declarada como Madrid'
);
SELECT throws_ok(
    $$ SELECT public._validar_direccion_local('Cataluña', '08', '28079') $$,
    '23514', NULL,
    'rechaza municipio 28079 (prov 28) dentro de la provincia 08'
);
SELECT throws_ok(
    $$ SELECT public._validar_direccion_local(NULL, '08', NULL) $$,
    '23514', NULL,
    'rechaza provincia sin comunidad autónoma'
);
SELECT throws_ok(
    $$ SELECT public._validar_direccion_local('Cataluña', NULL, '08019') $$,
    '23514', NULL,
    'rechaza municipio sin provincia'
);

-- ---- comportamiento de crear_local vía la RPC real, como gestor authenticated.
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claims = '{"sub":"f3000000-0000-0000-0000-0000000000a1","role":"authenticated"}';

-- Una llamada de 12 args con dirección coherente funciona.
SELECT lives_ok(
    $$ SELECT public.crear_local('f3000000-0000-0000-0000-000000000001', 'Local nuevo',
                                 NULL, NULL, NULL, NULL, NULL,
                                 'Cataluña', '08', '08019', 'Rambla 1', '08002') $$,
    'crear_local acepta 12 args con dirección coherente'
);
-- La RPC rechaza dirección incoherente (validador vía RPC, no solo directo).
SELECT throws_ok(
    $$ SELECT public.crear_local('f3000000-0000-0000-0000-000000000001', 'Local malo',
                                 NULL, NULL, NULL, NULL, NULL,
                                 'Madrid', '08', '08019', NULL, NULL) $$,
    '23514', NULL,
    'crear_local rechaza dirección incoherente (Madrid + provincia 08)'
);
-- La cadena de display se DERIVA de los campos estructurados (municipio+provincia).
SELECT is(
    (SELECT l.direccion FROM public.local l
      WHERE l.nombre = 'Local nuevo'
        AND l.empresa_id = 'f3000000-0000-0000-0000-000000000001'),
    'Rambla 1, 08002 Barcelona, Barcelona',
    'public.direccion(local) compone la dirección derivada desde la estructura'
);
RESET ROLE;

SELECT * FROM finish();
ROLLBACK;
