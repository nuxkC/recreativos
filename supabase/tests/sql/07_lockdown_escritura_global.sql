-- =============================================================================
-- Guardarraíl GLOBAL del invariante de escritura.
--
-- "Toda acción de escribir en la BBDD pasa por una función; no puede hacerse
--  directamente. La BBDD debe rechazarlo. Los clientes solo pueden leer."
--
-- Verifica, para TODAS las tablas de dominio, que los roles cliente
-- (`authenticated` y `anon`) NO tienen INSERT/UPDATE/DELETE directo, y que
-- `authenticated` sí conserva SELECT. La escritura real ocurre vía RPC
-- SECURITY DEFINER o Edge Function service_role (que puentean estos privilegios).
--
-- Si alguien añade una tabla nueva y le concede escritura a authenticated (o se
-- olvida de revocarla), este test falla. Mantener la lista al día.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

CREATE TEMP TABLE _tablas_dominio(t text) ON COMMIT DROP;
INSERT INTO _tablas_dominio(t) VALUES
    ('alerta'),
    ('audit_log'),
    ('averia'),
    ('averia_recambio'),
    ('cambio_placa'),
    ('credito_local'),
    ('device_token'),
    ('empresa'),
    ('empresa_usuario'),
    ('instalacion'),
    ('lectura_no_recaudada'),
    ('licencia'),
    ('local'),
    ('maquina'),
    ('recaudacion'),
    ('recaudacion_lock'),
    ('recuperacion'),
    ('resumen_mensual_envio'),
    ('usuario');

-- 19 tablas × (1 SELECT authenticated + 3 no-write authenticated + 3 no-write anon) = 133
SELECT plan(133);

-- authenticated CONSERVA lectura.
SELECT ok(
    has_table_privilege('authenticated', 'public.' || t, 'SELECT'),
    'authenticated PUEDE SELECT en ' || t
) FROM _tablas_dominio;

-- authenticated NO escribe directamente.
SELECT ok(
    NOT has_table_privilege('authenticated', 'public.' || t, 'INSERT'),
    'authenticated NO puede INSERT directo en ' || t
) FROM _tablas_dominio;
SELECT ok(
    NOT has_table_privilege('authenticated', 'public.' || t, 'UPDATE'),
    'authenticated NO puede UPDATE directo en ' || t
) FROM _tablas_dominio;
SELECT ok(
    NOT has_table_privilege('authenticated', 'public.' || t, 'DELETE'),
    'authenticated NO puede DELETE directo en ' || t
) FROM _tablas_dominio;

-- anon NO escribe directamente.
SELECT ok(
    NOT has_table_privilege('anon', 'public.' || t, 'INSERT'),
    'anon NO puede INSERT directo en ' || t
) FROM _tablas_dominio;
SELECT ok(
    NOT has_table_privilege('anon', 'public.' || t, 'UPDATE'),
    'anon NO puede UPDATE directo en ' || t
) FROM _tablas_dominio;
SELECT ok(
    NOT has_table_privilege('anon', 'public.' || t, 'DELETE'),
    'anon NO puede DELETE directo en ' || t
) FROM _tablas_dominio;

SELECT * FROM finish();
ROLLBACK;
