-- =============================================================================
-- Planificación de recaudación — P1.2: RPC actualizar_calendario_local.
--
-- Fija, desde la ficha de local, el calendario (cadencia + fecha de inicio) y el
-- operario asignado. SECURITY DEFINER con validación de rol (patrón del repo:
-- los clientes nunca escriben `local` directamente, solo vía esta función).
--
-- Validaciones:
--   * El local existe y pertenece a una empresa (tenant) donde quien llama es
--     gestor (owner/admin/gestor). Cross-tenant queda bloqueado por el chequeo
--     de rol: usuario_es_gestor de otra empresa es false.
--   * Coherencia cadencia ↔ fecha (ambas o ninguna) y cadencia > 0: mismos
--     invariantes que los CHECK de la tabla, pero con error legible.
--   * El operario, si se asigna, es miembro operativo ACTIVO de la MISMA empresa.
-- Spec: docs/superpowers/specs/2026-06-18-planificacion-recaudacion-design.md §4/§5.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.actualizar_calendario_local(
    p_local_id                 uuid,
    p_cadencia_semanas         smallint,
    p_fecha_inicio_recaudacion date,
    p_operario_id              uuid
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.local WHERE id = p_local_id;
    IF v_empresa_id IS NULL THEN
        RAISE EXCEPTION 'local no encontrado: %', p_local_id USING ERRCODE = '42704';
    END IF;

    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para planificar la recaudacion de este local'
            USING ERRCODE = '42501';
    END IF;

    -- Coherencia: cadencia y fecha van juntas (mismo invariante que el CHECK
    -- local_calendario_coherente, pero con mensaje legible en vez de violación).
    IF (p_cadencia_semanas IS NULL) <> (p_fecha_inicio_recaudacion IS NULL) THEN
        RAISE EXCEPTION 'cadencia_semanas y fecha_inicio_recaudacion deben fijarse juntas o ninguna'
            USING ERRCODE = '22023';
    END IF;
    IF p_cadencia_semanas IS NOT NULL AND p_cadencia_semanas <= 0 THEN
        RAISE EXCEPTION 'cadencia_semanas debe ser > 0: %', p_cadencia_semanas
            USING ERRCODE = '22023';
    END IF;

    -- El operario, si se asigna, debe ser miembro operativo activo de la empresa
    -- del local. Roles operativos = owner/admin/gestor/tecnico (los que recaudan
    -- o gestionan); un contable (solo lectura) no lleva locales.
    IF p_operario_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.empresa_usuario
             WHERE usuario_id = p_operario_id
               AND empresa_id = v_empresa_id
               AND activo = true
               AND rol = ANY (ARRAY['owner', 'admin', 'gestor', 'tecnico'])
        ) THEN
            RAISE EXCEPTION 'el operario % no es miembro operativo activo de la empresa', p_operario_id
                USING ERRCODE = '22023';
        END IF;
    END IF;

    UPDATE public.local SET
        cadencia_semanas         = p_cadencia_semanas,
        fecha_inicio_recaudacion = p_fecha_inicio_recaudacion,
        operario_id              = p_operario_id,
        updated_at               = now()
    WHERE id = p_local_id;
END;
$$;

COMMENT ON FUNCTION public.actualizar_calendario_local(uuid, smallint, date, uuid) IS
    'Fija el calendario de recaudación de un local (cadencia + fecha de inicio) y su operario asignado. Valida rol gestor + tenant + operario operativo activo. Planificación P1.';

REVOKE ALL    ON FUNCTION public.actualizar_calendario_local(uuid, smallint, date, uuid) FROM PUBLIC, anon;
GRANT  EXECUTE ON FUNCTION public.actualizar_calendario_local(uuid, smallint, date, uuid) TO authenticated;
