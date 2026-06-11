-- =============================================================================
-- Capa de escritura SECURITY DEFINER para el inventario (licencia, maquina,
-- local).
--
-- Invariante del repo: los clientes (`authenticated`/`anon`) NUNCA escriben las
-- tablas directamente; solo SELECT. Toda escritura pasa por una función. Aquí
-- creamos las RPCs `crear/actualizar/eliminar_<tabla>` (SECURITY DEFINER, dueño
-- postgres → puentean RLS y grants de tabla) que validan rol (gestor) + tenant
-- internamente. El REVOKE de la escritura directa va en una migración posterior
-- (20260611150000), junto al rewire de los clientes web/android.
--
-- Patrón idéntico al de instalación (20260611120000):
--   * crear_*  -> valida usuario_es_gestor(p_empresa_id), INSERT, RETURNS id.
--   * actualizar_*/eliminar_* -> derivan empresa_id de la fila, validan gestor
--     sobre ESA empresa (aísla tenants), UPDATE/DELETE.
--   * Errores nativos (23505 duplicado, 23503 FK en uso) se propagan tal cual
--     para que el cliente los siga distinguiendo.
--   * GRANT EXECUTE a authenticated; REVOKE de PUBLIC/anon.
--
-- `updated_at` lo refresca el trigger de 20260519230000; no se toca aquí.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- licencia
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.crear_licencia(
    p_empresa_id         uuid,
    p_numero             text,
    p_tipo               text,
    p_fecha_expedicion   date,
    p_fecha_caducidad    date,
    p_comunidad_autonoma text,
    p_estado             text,
    p_notas              text DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id uuid;
BEGIN
    IF NOT public.usuario_es_gestor(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar licencias'
            USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.licencia (
        empresa_id, numero, tipo, fecha_expedicion, fecha_caducidad,
        comunidad_autonoma, estado, notas
    ) VALUES (
        p_empresa_id, p_numero, p_tipo, p_fecha_expedicion, p_fecha_caducidad,
        p_comunidad_autonoma, p_estado, p_notas
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_licencia(uuid, text, text, date, date, text, text, text) IS
    'Alta de licencia. Valida rol gestor + tenant. Devuelve el id.';

CREATE OR REPLACE FUNCTION public.actualizar_licencia(
    p_id                 uuid,
    p_numero             text,
    p_tipo               text,
    p_fecha_expedicion   date,
    p_fecha_caducidad    date,
    p_comunidad_autonoma text,
    p_estado             text,
    p_notas              text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.licencia WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'licencia no encontrada: %', p_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar licencias'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.licencia SET
        numero             = p_numero,
        tipo               = p_tipo,
        fecha_expedicion   = p_fecha_expedicion,
        fecha_caducidad    = p_fecha_caducidad,
        comunidad_autonoma = p_comunidad_autonoma,
        estado             = p_estado,
        notas              = p_notas
    WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.actualizar_licencia(uuid, text, text, date, date, text, text, text) IS
    'Edición de licencia. Valida rol gestor + tenant.';

CREATE OR REPLACE FUNCTION public.eliminar_licencia(p_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.licencia WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'licencia no encontrada: %', p_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar licencias'
            USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.licencia WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.eliminar_licencia(uuid) IS
    'Borra una licencia. Valida rol gestor + tenant. La FK propaga 23503 si está en uso.';

-- -----------------------------------------------------------------------------
-- maquina
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.crear_maquina(
    p_empresa_id                uuid,
    p_numero_serie              text,
    p_modelo                    text,
    p_fabricante                text,
    p_valor_credito             numeric,
    p_contador_entradas_inicial bigint,
    p_contador_salidas_inicial  bigint,
    p_estado                    text,
    p_notas                     text DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id uuid;
BEGIN
    IF NOT public.usuario_es_gestor(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar maquinas'
            USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.maquina (
        empresa_id, numero_serie, modelo, fabricante, valor_credito,
        contador_entradas_inicial, contador_salidas_inicial, estado, notas
    ) VALUES (
        p_empresa_id, p_numero_serie, p_modelo, p_fabricante, p_valor_credito,
        p_contador_entradas_inicial, p_contador_salidas_inicial, p_estado, p_notas
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text) IS
    'Alta de maquina. Valida rol gestor + tenant. Devuelve el id.';

CREATE OR REPLACE FUNCTION public.actualizar_maquina(
    p_id                        uuid,
    p_numero_serie              text,
    p_modelo                    text,
    p_fabricante                text,
    p_valor_credito             numeric,
    p_contador_entradas_inicial bigint,
    p_contador_salidas_inicial  bigint,
    p_estado                    text,
    p_notas                     text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.maquina WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'maquina no encontrada: %', p_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar maquinas'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.maquina SET
        numero_serie              = p_numero_serie,
        modelo                    = p_modelo,
        fabricante                = p_fabricante,
        valor_credito             = p_valor_credito,
        contador_entradas_inicial = p_contador_entradas_inicial,
        contador_salidas_inicial  = p_contador_salidas_inicial,
        estado                    = p_estado,
        notas                     = p_notas
    WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.actualizar_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text) IS
    'Edición de maquina. Valida rol gestor + tenant.';

CREATE OR REPLACE FUNCTION public.eliminar_maquina(p_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.maquina WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'maquina no encontrada: %', p_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar maquinas'
            USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.maquina WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.eliminar_maquina(uuid) IS
    'Borra una maquina. Valida rol gestor + tenant. La FK propaga 23503 si está en uso.';

-- -----------------------------------------------------------------------------
-- local
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.crear_local(
    p_empresa_id     uuid,
    p_nombre         text,
    p_direccion      text,
    p_cif_o_nif      text,
    p_titular_nombre text,
    p_telefono       text,
    p_email          text,
    p_notas          text DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id uuid;
BEGIN
    IF NOT public.usuario_es_gestor(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar locales'
            USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.local (
        empresa_id, nombre, direccion, cif_o_nif, titular_nombre,
        telefono, email, notas
    ) VALUES (
        p_empresa_id, p_nombre, p_direccion, p_cif_o_nif, p_titular_nombre,
        p_telefono, p_email, p_notas
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_local(uuid, text, text, text, text, text, text, text) IS
    'Alta de local. Valida rol gestor + tenant. Devuelve el id.';

CREATE OR REPLACE FUNCTION public.actualizar_local(
    p_id             uuid,
    p_nombre         text,
    p_direccion      text,
    p_cif_o_nif      text,
    p_titular_nombre text,
    p_telefono       text,
    p_email          text,
    p_notas          text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.local WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'local no encontrado: %', p_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar locales'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.local SET
        nombre         = p_nombre,
        direccion      = p_direccion,
        cif_o_nif      = p_cif_o_nif,
        titular_nombre = p_titular_nombre,
        telefono       = p_telefono,
        email          = p_email,
        notas          = p_notas
    WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.actualizar_local(uuid, text, text, text, text, text, text, text) IS
    'Edición de local. Valida rol gestor + tenant.';

CREATE OR REPLACE FUNCTION public.eliminar_local(p_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.local WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'local no encontrado: %', p_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar locales'
            USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.local WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.eliminar_local(uuid) IS
    'Borra un local. Valida rol gestor + tenant. La FK propaga 23503 si está en uso.';

-- -----------------------------------------------------------------------------
-- Permisos: solo `authenticated` ejecuta las RPCs de escritura.
-- -----------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.crear_licencia(uuid, text, text, date, date, text, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.actualizar_licencia(uuid, text, text, date, date, text, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.eliminar_licencia(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.crear_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.actualizar_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.eliminar_maquina(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.crear_local(uuid, text, text, text, text, text, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.actualizar_local(uuid, text, text, text, text, text, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.eliminar_local(uuid) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.crear_licencia(uuid, text, text, date, date, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.actualizar_licencia(uuid, text, text, date, date, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.eliminar_licencia(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.crear_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.actualizar_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.eliminar_maquina(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.crear_local(uuid, text, text, text, text, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.actualizar_local(uuid, text, text, text, text, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.eliminar_local(uuid) TO authenticated;
