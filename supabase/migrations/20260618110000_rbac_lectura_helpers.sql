-- =============================================================================
-- Planificación de recaudación — P2.1: helpers de visibilidad (read-RBAC).
--
-- Endurecemos las lecturas para que un TÉCNICO solo lea sus locales asignados;
-- owner/admin/gestor/contable siguen viendo todo. Estos helpers encapsulan el
-- criterio y, al ser SECURITY DEFINER, saltan la RLS: así pueden usarse dentro
-- de las policies SELECT sin recursión (consultan las mismas tablas que filtran).
--
-- "Ve todo" = rol ∈ {owner, admin, gestor, contable} (el contable es solo
-- lectura financiera y necesita verlo todo). El único rol restringido es
-- 'tecnico', que solo ve los locales con operario_id = auth.uid().
-- Spec: docs/superpowers/specs/2026-06-18-planificacion-recaudacion-design.md §5.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.usuario_ve_todo_empresa(p_empresa_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
    SELECT public.usuario_tiene_rol(p_empresa_id, ARRAY['owner', 'admin', 'gestor', 'contable']);
$$;

COMMENT ON FUNCTION public.usuario_ve_todo_empresa(uuid) IS
    'TRUE si el usuario ve TODO de la empresa (no es solo técnico). Solo el rol técnico se restringe a sus locales asignados.';

CREATE OR REPLACE FUNCTION public.usuario_ve_local(p_local_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.local l
         WHERE l.id = p_local_id
           AND (public.usuario_ve_todo_empresa(l.empresa_id) OR l.operario_id = auth.uid())
    );
$$;

COMMENT ON FUNCTION public.usuario_ve_local(uuid) IS
    'TRUE si el usuario puede leer el local: ve-todo de su empresa, o es el operario asignado.';

CREATE OR REPLACE FUNCTION public.usuario_ve_instalacion(p_instalacion_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.instalacion i
          JOIN public.local l ON l.id = i.local_id
         WHERE i.id = p_instalacion_id
           AND (public.usuario_ve_todo_empresa(l.empresa_id) OR l.operario_id = auth.uid())
    );
$$;

COMMENT ON FUNCTION public.usuario_ve_instalacion(uuid) IS
    'TRUE si el usuario puede leer la instalación: ve-todo de su empresa, o es operario del local donde está.';

REVOKE ALL    ON FUNCTION public.usuario_ve_todo_empresa(uuid) FROM PUBLIC;
REVOKE ALL    ON FUNCTION public.usuario_ve_local(uuid)        FROM PUBLIC;
REVOKE ALL    ON FUNCTION public.usuario_ve_instalacion(uuid)  FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.usuario_ve_todo_empresa(uuid) TO authenticated;
GRANT  EXECUTE ON FUNCTION public.usuario_ve_local(uuid)        TO authenticated;
GRANT  EXECUTE ON FUNCTION public.usuario_ve_instalacion(uuid)  TO authenticated;
