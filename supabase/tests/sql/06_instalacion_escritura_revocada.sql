-- =============================================================================
-- Guardarraíl: `authenticated` solo puede LEER `instalacion`. Toda escritura
-- pasa por función (RPC SECURITY DEFINER / Edge service_role). La BBDD debe
-- rechazar INSERT/UPDATE/DELETE directos del cliente.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;
SELECT plan(4);

SELECT ok(
    has_table_privilege('authenticated', 'public.instalacion', 'SELECT'),
    'authenticated PUEDE leer instalacion'
);
SELECT ok(
    NOT has_table_privilege('authenticated', 'public.instalacion', 'INSERT'),
    'authenticated NO puede INSERT directo en instalacion'
);
SELECT ok(
    NOT has_table_privilege('authenticated', 'public.instalacion', 'UPDATE'),
    'authenticated NO puede UPDATE directo en instalacion'
);
SELECT ok(
    NOT has_table_privilege('authenticated', 'public.instalacion', 'DELETE'),
    'authenticated NO puede DELETE directo en instalacion'
);

SELECT * FROM finish();
ROLLBACK;
