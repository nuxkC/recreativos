-- =============================================================================
-- T-274 (cierre) — Se retira de la firma de crear/actualizar_licencia el
-- parámetro `p_tipo`, que quedó INERTE cuando se eliminó el "tipo de licencia"
-- (migración 20260702120000): ninguna de las dos funciones lo lee ni lo escribe.
--
-- Es un cambio de firma (breaking): `CREATE OR REPLACE` no puede quitar un
-- parámetro de entrada, así que se hace DROP de la firma vieja (8 args) +
-- CREATE de la nueva (7 args). Al dropear la función se pierden sus grants, por
-- lo que se reemiten REVOKE/GRANT/COMMENT sobre la firma nueva.
--
-- Despliegue coordinado: los clientes (web + Android) dejan de enviar `p_tipo`
-- en el mismo cambio; deben publicarse junto a esta migración (PostgREST casa la
-- función por el conjunto exacto de argumentos, así que un cliente que aún
-- enviara `p_tipo` no encontraría la función de 7 args).
-- =============================================================================

DROP FUNCTION IF EXISTS public.crear_licencia(uuid, text, text, date, date, text, text, text);
DROP FUNCTION IF EXISTS public.actualizar_licencia(uuid, text, text, date, date, text, text, text);

-- -----------------------------------------------------------------------------
-- crear_licencia (7 args, sin p_tipo)
-- -----------------------------------------------------------------------------
CREATE FUNCTION public.crear_licencia(
    p_empresa_id         uuid,
    p_numero             text,
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
        empresa_id, numero, fecha_expedicion, fecha_caducidad,
        comunidad_autonoma, estado, notas
    ) VALUES (
        p_empresa_id, p_numero, p_fecha_expedicion, p_fecha_caducidad,
        p_comunidad_autonoma, p_estado, p_notas
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

-- -----------------------------------------------------------------------------
-- actualizar_licencia (7 args, sin p_tipo)
-- -----------------------------------------------------------------------------
CREATE FUNCTION public.actualizar_licencia(
    p_id                 uuid,
    p_numero             text,
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
        fecha_expedicion   = p_fecha_expedicion,
        fecha_caducidad    = p_fecha_caducidad,
        comunidad_autonoma = p_comunidad_autonoma,
        estado             = p_estado,
        notas              = p_notas
    WHERE id = p_id;
END;
$$;

-- -----------------------------------------------------------------------------
-- Lockdown: solo `authenticated` ejecuta; nunca PUBLIC/anon (patrón guardarraíl 08).
-- -----------------------------------------------------------------------------
COMMENT ON FUNCTION public.crear_licencia(uuid, text, date, date, text, text, text) IS
    'Alta de licencia (SECURITY DEFINER). Solo gestores del tenant.';
COMMENT ON FUNCTION public.actualizar_licencia(uuid, text, date, date, text, text, text) IS
    'Edición de licencia (SECURITY DEFINER). Solo gestores del tenant.';

REVOKE ALL ON FUNCTION public.crear_licencia(uuid, text, date, date, text, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.actualizar_licencia(uuid, text, date, date, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.crear_licencia(uuid, text, date, date, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.actualizar_licencia(uuid, text, date, date, text, text, text) TO authenticated;
