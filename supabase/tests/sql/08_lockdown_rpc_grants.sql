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
    ('crear_licencia(uuid, text, date, date, text, text, text)'),
    ('actualizar_licencia(uuid, text, date, date, text, text, text)'),
    ('eliminar_licencia(uuid)'),
    ('crear_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text)'),
    ('actualizar_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text)'),
    ('eliminar_maquina(uuid)'),
    -- crear/actualizar_local ganan dirección estructurada (T-277): +5 args text
    ('crear_local(uuid, text, text, text, text, text, text, text, text, text, text, text, text)'),
    ('actualizar_local(uuid, text, text, text, text, text, text, text, text, text, text, text, text)'),
    ('eliminar_local(uuid)'),
    -- gestión de empresa (actualizar_ajustes_empresa lleva redondeo T-211 + % recuperación T-212)
    ('actualizar_ajustes_empresa(uuid, text, text, text, text, text, text, text, text, smallint, smallint)'),
    ('cambiar_rol_miembro(uuid, uuid, text)'),
    ('cambiar_estado_miembro(uuid, uuid, boolean)'),
    ('marcar_alerta_leida(uuid)'),
    ('marcar_alertas_leidas_empresa(uuid)'),
    -- instalación (crear_instalacion lleva tolva + hook de traslado T-212;
    -- gana cadencia + fecha de inicio de recaudación en Planificación P1)
    ('crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text, numeric, uuid, smallint, date)'),
    ('actualizar_instalacion(uuid, date, numeric, numeric, text)'),
    ('eliminar_instalacion(uuid)'),
    -- calendario de recaudación por local (Planificación P1)
    ('actualizar_calendario_local(uuid, smallint, date, uuid)'),
    -- tolva / préstamos / recuperación (T-212)
    ('crear_prestamo(uuid, uuid, numeric, numeric, date, text)'),
    ('registrar_recuperacion_efectivo(uuid, numeric, text)'),
    ('condonar_credito(uuid, text)'),
    ('set_porcentaje_recuperacion_local(uuid, smallint)'),
    -- averías (T-220; crear_averia gana firma de tolva en T-223)
    ('crear_averia(uuid, uuid, text, text, boolean, text, boolean, numeric)'),
    ('actualizar_averia(uuid, text, text, boolean, text)'),
    ('resolver_averia(uuid, text)'),
    ('crear_recambio(uuid, text, integer, numeric, text)'),
    ('eliminar_recambio(uuid)'),
    -- tolva por avería (T-223)
    ('saldar_tolva_pendiente(uuid, text)'),
    -- catálogo global (T-268)
    ('crear_fabricante(text)'),
    ('crear_modelo(uuid, text)'),
    -- curación del catálogo (T-275)
    ('renombrar_fabricante(uuid, text)'),
    ('renombrar_modelo(uuid, text)'),
    ('fusionar_fabricante(uuid, uuid)'),
    ('fusionar_modelo(uuid, uuid)');

-- 34 funciones × (authenticated EXECUTE + anon NO EXECUTE) = 68
SELECT plan(68);

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
