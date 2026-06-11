-- =============================================================================
-- Capa de escritura SECURITY DEFINER para gestión de empresa: ajustes
-- (empresa), equipo (empresa_usuario) y alertas (alerta).
--
-- Invariante del repo: los clientes solo LEEN; toda escritura pasa por función.
-- Estas RPCs validan rol + tenant internamente (puentean RLS por ser DEFINER).
-- El REVOKE de la escritura directa va en 20260611170000, junto al rewire de
-- los clientes web/android.
--
-- auth.uid() sigue devolviendo el usuario del JWT dentro de SECURITY DEFINER
-- (DEFINER cambia el privilegio/owner, no las claims), así que los helpers
-- usuario_es_admin / usuario_tiene_rol / usuario_pertenece_a_empresa evalúan al
-- LLAMANTE correctamente.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- empresa: ajustes (owner/admin). No toca columnas de suscripción/trial
-- (las protege su propio trigger); solo los datos editables del formulario.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.actualizar_ajustes_empresa(
    p_empresa_id      uuid,
    p_nombre          text,
    p_cif             text,
    p_direccion       text,
    p_telefono        text,
    p_email           text,
    p_zona_horaria    text,
    p_ticket_cabecera text,
    p_ticket_pie      text
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
BEGIN
    IF NOT public.usuario_es_admin(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para editar la empresa'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.empresa SET
        nombre          = p_nombre,
        cif             = p_cif,
        direccion       = p_direccion,
        telefono        = p_telefono,
        email           = p_email,
        zona_horaria    = p_zona_horaria,
        ticket_cabecera = p_ticket_cabecera,
        ticket_pie      = p_ticket_pie
    WHERE id = p_empresa_id;
END;
$$;

COMMENT ON FUNCTION public.actualizar_ajustes_empresa(uuid, text, text, text, text, text, text, text, text) IS
    'Edita los ajustes editables de la empresa. Valida rol admin + tenant.';

-- -----------------------------------------------------------------------------
-- empresa_usuario: cambio de rol y de estado (activo) de un miembro.
-- Reglas (espejo de web/src/lib/equipo/actions.ts, como gate server-side):
--   * solo owner/admin.
--   * nadie se edita a sí mismo.
--   * asignar 'owner' o tocar a un 'owner' requiere ser owner.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.cambiar_rol_miembro(
    p_empresa_id uuid,
    p_usuario_id uuid,
    p_rol        text
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_rol_actual text;
    v_soy_owner  boolean := public.usuario_tiene_rol(p_empresa_id, ARRAY['owner']);
BEGIN
    IF NOT public.usuario_es_admin(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar el equipo'
            USING ERRCODE = '42501';
    END IF;
    IF auth.uid() = p_usuario_id THEN
        RAISE EXCEPTION 'no puedes cambiar tu propio rol'
            USING ERRCODE = '42501';
    END IF;
    IF p_rol = 'owner' AND NOT v_soy_owner THEN
        RAISE EXCEPTION 'solo el owner puede asignar el rol owner'
            USING ERRCODE = '42501';
    END IF;

    SELECT rol INTO v_rol_actual
      FROM public.empresa_usuario
     WHERE empresa_id = p_empresa_id AND usuario_id = p_usuario_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'miembro no encontrado en la empresa'
            USING ERRCODE = 'no_data_found';
    END IF;
    IF v_rol_actual = 'owner' AND NOT v_soy_owner THEN
        RAISE EXCEPTION 'solo el owner puede editar a un owner'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.empresa_usuario SET rol = p_rol
     WHERE empresa_id = p_empresa_id AND usuario_id = p_usuario_id;
END;
$$;

COMMENT ON FUNCTION public.cambiar_rol_miembro(uuid, uuid, text) IS
    'Cambia el rol de un miembro. Valida admin + tenant + reglas de owner/self.';

CREATE OR REPLACE FUNCTION public.cambiar_estado_miembro(
    p_empresa_id uuid,
    p_usuario_id uuid,
    p_activo     boolean
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_rol_actual text;
    v_soy_owner  boolean := public.usuario_tiene_rol(p_empresa_id, ARRAY['owner']);
BEGIN
    IF NOT public.usuario_es_admin(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar el equipo'
            USING ERRCODE = '42501';
    END IF;
    IF auth.uid() = p_usuario_id THEN
        RAISE EXCEPTION 'no puedes cambiar tu propio estado'
            USING ERRCODE = '42501';
    END IF;

    SELECT rol INTO v_rol_actual
      FROM public.empresa_usuario
     WHERE empresa_id = p_empresa_id AND usuario_id = p_usuario_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'miembro no encontrado en la empresa'
            USING ERRCODE = 'no_data_found';
    END IF;
    IF v_rol_actual = 'owner' AND NOT v_soy_owner THEN
        RAISE EXCEPTION 'solo el owner puede editar a un owner'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.empresa_usuario SET activo = p_activo
     WHERE empresa_id = p_empresa_id AND usuario_id = p_usuario_id;
END;
$$;

COMMENT ON FUNCTION public.cambiar_estado_miembro(uuid, uuid, boolean) IS
    'Activa/desactiva un miembro. Valida admin + tenant + reglas de owner/self.';

-- -----------------------------------------------------------------------------
-- alerta: marcar como leída (un miembro cualquiera de la empresa). El INSERT
-- y el resto de columnas los maneja el backend (service_role / triggers).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.marcar_alerta_leida(p_alerta_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.alerta WHERE id = p_alerta_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'alerta no encontrada: %', p_alerta_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_pertenece_a_empresa(v_empresa_id) THEN
        RAISE EXCEPTION 'sin acceso a esta alerta'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.alerta SET leida = true WHERE id = p_alerta_id;
END;
$$;

COMMENT ON FUNCTION public.marcar_alerta_leida(uuid) IS
    'Marca una alerta como leída. Valida pertenencia a la empresa de la alerta.';

CREATE OR REPLACE FUNCTION public.marcar_alertas_leidas_empresa(p_empresa_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
BEGIN
    IF NOT public.usuario_pertenece_a_empresa(p_empresa_id) THEN
        RAISE EXCEPTION 'sin acceso a las alertas de esta empresa'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.alerta SET leida = true
     WHERE empresa_id = p_empresa_id AND leida = false;
END;
$$;

COMMENT ON FUNCTION public.marcar_alertas_leidas_empresa(uuid) IS
    'Marca todas las alertas pendientes de la empresa como leídas. Valida pertenencia.';

-- -----------------------------------------------------------------------------
-- Permisos: solo `authenticated` ejecuta las RPCs de escritura.
-- -----------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.actualizar_ajustes_empresa(uuid, text, text, text, text, text, text, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.cambiar_rol_miembro(uuid, uuid, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.cambiar_estado_miembro(uuid, uuid, boolean) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.marcar_alerta_leida(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.marcar_alertas_leidas_empresa(uuid) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.actualizar_ajustes_empresa(uuid, text, text, text, text, text, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.cambiar_rol_miembro(uuid, uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.cambiar_estado_miembro(uuid, uuid, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.marcar_alerta_leida(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.marcar_alertas_leidas_empresa(uuid) TO authenticated;
