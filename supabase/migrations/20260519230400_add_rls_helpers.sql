-- =============================================================================
-- T-15a — Helpers para Row Level Security.
--
-- Centralizamos la lógica de "¿este usuario tiene acceso a esta empresa?"
-- en funciones reutilizables. Marcadas como SECURITY DEFINER + STABLE
-- para que las policies puedan llamarlas sin recursión RLS.
--
-- IMPORTANTE: las funciones SECURITY DEFINER deben fijar `search_path` para
-- evitar shadowing de tablas, una buena práctica de seguridad.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.empresas_del_usuario_actual()
RETURNS TABLE (empresa_id uuid, rol text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
    SELECT empresa_id, rol
      FROM public.empresa_usuario
     WHERE usuario_id = auth.uid()
       AND activo = true;
$$;

COMMENT ON FUNCTION public.empresas_del_usuario_actual() IS
    'Empresas activas del usuario autenticado y su rol en cada una. Usada por las policies RLS.';

CREATE OR REPLACE FUNCTION public.usuario_pertenece_a_empresa(p_empresa_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.empresa_usuario
         WHERE usuario_id = auth.uid()
           AND empresa_id = p_empresa_id
           AND activo = true
    );
$$;

COMMENT ON FUNCTION public.usuario_pertenece_a_empresa(uuid) IS
    'TRUE si el usuario autenticado pertenece (activo) a la empresa indicada.';

CREATE OR REPLACE FUNCTION public.usuario_tiene_rol(p_empresa_id uuid, p_roles text[])
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.empresa_usuario
         WHERE usuario_id = auth.uid()
           AND empresa_id = p_empresa_id
           AND activo = true
           AND rol = ANY (p_roles)
    );
$$;

COMMENT ON FUNCTION public.usuario_tiene_rol(uuid, text[]) IS
    'TRUE si el usuario autenticado tiene alguno de los roles indicados en la empresa indicada.';

-- Atajo: el dueño/admin de una empresa.
CREATE OR REPLACE FUNCTION public.usuario_es_admin(p_empresa_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
    SELECT public.usuario_tiene_rol(p_empresa_id, ARRAY['owner', 'admin']);
$$;

COMMENT ON FUNCTION public.usuario_es_admin(uuid) IS
    'TRUE si el usuario es owner o admin de la empresa.';

CREATE OR REPLACE FUNCTION public.usuario_es_gestor(p_empresa_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
    SELECT public.usuario_tiene_rol(p_empresa_id, ARRAY['owner', 'admin', 'gestor']);
$$;

COMMENT ON FUNCTION public.usuario_es_gestor(uuid) IS
    'TRUE si el usuario puede gestionar inventario en la empresa (owner/admin/gestor).';

CREATE OR REPLACE FUNCTION public.usuario_es_operativo(p_empresa_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
    SELECT public.usuario_tiene_rol(p_empresa_id, ARRAY['owner', 'admin', 'gestor', 'tecnico']);
$$;

COMMENT ON FUNCTION public.usuario_es_operativo(uuid) IS
    'TRUE si el usuario puede ejecutar acciones operativas (recaudar, registrar cambio_placa) en la empresa.';
