-- =============================================================================
-- T-200 — Función transaccional `registrar_empresa_con_owner`.
--
-- Crea, EN UNA SOLA TRANSACCIÓN (atómica), todo lo necesario para que una
-- cuenta de Auth recién creada quede operativa como dueña de una empresa nueva
-- en periodo de prueba:
--
--   1. empresa (estado_suscripcion='trial', trial_inicio=now, trial_fin=now+N días)
--   2. perfil usuario (upsert: respeta el nombre si ya existía)
--   3. membresía empresa_usuario con rol 'owner'
--
-- Atomicidad / rollback (ver design.md §6):
--   * Toda la función corre en una transacción implícita: si cualquier INSERT
--     falla, NADA se persiste → no quedan empresas huérfanas ni membresías
--     a medias.
--   * La creación del usuario de Auth ocurre FUERA de esta transacción (es una
--     API separada de Supabase Auth). La Edge Function `registrar-empresa` se
--     encarga del rollback de ese paso: si esta función falla tras haber
--     creado un usuario de Auth nuevo, la Edge Function lo elimina (best-effort).
--
-- Seguridad:
--   * SECURITY DEFINER + search_path fijo: la función inserta saltándose RLS,
--     pero su EXECUTE está restringido a service_role (REVOKE a PUBLIC). Solo
--     la Edge Function (service_role) puede invocarla; nunca el cliente.
--   * Valida nombres no vacíos y trial_dias > 0 server-side.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.registrar_empresa_con_owner(
    p_usuario_id      uuid,
    p_nombre_empresa  text,
    p_nombre_completo text,
    p_trial_dias      integer DEFAULT 14
)
RETURNS TABLE (
    empresa_id         uuid,
    estado_suscripcion text,
    trial_inicio       timestamptz,
    trial_fin          timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id      uuid;
    v_inicio          timestamptz := now();
    v_fin             timestamptz;
    v_nombre_empresa  text := btrim(coalesce(p_nombre_empresa, ''));
    v_nombre_completo text := btrim(coalesce(p_nombre_completo, ''));
BEGIN
    IF v_nombre_empresa = '' THEN
        RAISE EXCEPTION 'nombre_empresa_vacio' USING ERRCODE = '23514';
    END IF;
    IF v_nombre_completo = '' THEN
        RAISE EXCEPTION 'nombre_completo_vacio' USING ERRCODE = '23514';
    END IF;
    IF p_trial_dias IS NULL OR p_trial_dias <= 0 THEN
        RAISE EXCEPTION 'trial_dias_invalido' USING ERRCODE = '22023';
    END IF;

    v_fin := v_inicio + make_interval(days => p_trial_dias);

    INSERT INTO public.empresa (nombre, estado_suscripcion, trial_inicio, trial_fin)
    VALUES (v_nombre_empresa, 'trial', v_inicio, v_fin)
    RETURNING id INTO v_empresa_id;

    -- Perfil: si el usuario ya existía (p. ej. registró con sesión previa) no
    -- pisamos su nombre salvo que estuviera vacío.
    INSERT INTO public.usuario (id, nombre_completo)
    VALUES (p_usuario_id, v_nombre_completo)
    ON CONFLICT (id) DO UPDATE
        SET nombre_completo = coalesce(
            nullif(btrim(public.usuario.nombre_completo), ''),
            excluded.nombre_completo
        );

    INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo)
    VALUES (v_empresa_id, p_usuario_id, 'owner', true);

    RETURN QUERY
        SELECT v_empresa_id, 'trial'::text, v_inicio, v_fin;
END;
$$;

COMMENT ON FUNCTION public.registrar_empresa_con_owner(uuid, text, text, integer) IS
    'T-200: crea empresa (trial) + perfil usuario + membresía owner de forma atómica. Solo service_role.';

-- Solo la Edge Function (service_role) puede ejecutarla. El cliente jamás.
REVOKE ALL ON FUNCTION public.registrar_empresa_con_owner(uuid, text, text, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.registrar_empresa_con_owner(uuid, text, text, integer) FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.registrar_empresa_con_owner(uuid, text, text, integer) TO service_role;
