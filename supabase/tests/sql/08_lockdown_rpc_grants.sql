-- =============================================================================
-- Guardarraíl: las RPCs de escritura (SECURITY DEFINER) son ejecutables por
-- `authenticated` y NO por `anon`. Son la única vía de escritura de los
-- clientes; el anon key (que viaja en la app) no debe poder invocarlas.
-- =============================================================================

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap;

CREATE TEMP TABLE _fns(sig text) ON COMMIT DROP;
INSERT INTO _fns(sig) VALUES
    -- inventario
    ('crear_licencia(uuid, text, text, date, date, text, text, text)'),
    ('actualizar_licencia(uuid, text, text, date, date, text, text, text)'),
    ('eliminar_licencia(uuid)'),
    ('crear_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text)'),
    ('actualizar_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text)'),
    ('eliminar_maquina(uuid)'),
    ('crear_local(uuid, text, text, text, text, text, text, text)'),
    ('actualizar_local(uuid, text, text, text, text, text, text, text)'),
    ('eliminar_local(uuid)'),
    -- gestión de empresa
    ('actualizar_ajustes_empresa(uuid, text, text, text, text, text, text, text, text)'),
    ('cambiar_rol_miembro(uuid, uuid, text)'),
    ('cambiar_estado_miembro(uuid, uuid, boolean)'),
    ('marcar_alerta_leida(uuid)'),
    ('marcar_alertas_leidas_empresa(uuid)'),
    -- instalación
    ('crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text)'),
    ('actualizar_instalacion(uuid, date, numeric, numeric, text)'),
    ('eliminar_instalacion(uuid)');

-- 17 funciones × (authenticated EXECUTE + anon NO EXECUTE) = 34
SELECT plan(34);

SELECT ok(
    has_function_privilege('authenticated', 'public.' || sig, 'EXECUTE'),
    'authenticated PUEDE ejecutar ' || sig
) FROM _fns;

SELECT ok(
    NOT has_function_privilege('anon', 'public.' || sig, 'EXECUTE'),
    'anon NO puede ejecutar ' || sig
) FROM _fns;

SELECT * FROM finish();
ROLLBACK;
