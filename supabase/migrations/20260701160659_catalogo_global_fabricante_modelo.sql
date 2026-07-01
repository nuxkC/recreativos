-- =============================================================================
-- Catálogo GLOBAL de fabricante y modelo de máquina.
--
-- PRIMER catálogo del repo SIN empresa_id: fabricante/modelo son los mismos
-- para todas las empresas (como las comunidades autónomas). Rompe a conciencia
-- el invariante "toda tabla lleva empresa_id" de CLAUDE.md.
--
-- Lectura: abierta a cualquier usuario autenticado (RLS SELECT USING(true) +
-- GRANT SELECT TO authenticated; NO a anon).
-- Escritura: solo vía RPC SECURITY DEFINER (alta idempotente); INSERT/UPDATE/
-- DELETE directos revocados. La curación (renombrar/fusionar) llega en B2,
-- cuando la máquina ya referencia el catálogo.
-- =============================================================================

-- ---------------------------------------------------------------- tablas

CREATE TABLE IF NOT EXISTS public.fabricante (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre              text        NOT NULL CHECK (btrim(nombre) <> ''),
    nombre_normalizado  text        GENERATED ALWAYS AS (lower(btrim(nombre))) STORED,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid        DEFAULT auth.uid(),
    UNIQUE (nombre_normalizado)
);

COMMENT ON TABLE public.fabricante IS
    'Catálogo GLOBAL de fabricantes de máquina (sin empresa_id). Alta vía RPC crear_fabricante.';

CREATE TABLE IF NOT EXISTS public.modelo (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    fabricante_id       uuid        NOT NULL REFERENCES public.fabricante(id) ON DELETE RESTRICT,
    nombre              text        NOT NULL CHECK (btrim(nombre) <> ''),
    nombre_normalizado  text        GENERATED ALWAYS AS (lower(btrim(nombre))) STORED,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid        DEFAULT auth.uid(),
    UNIQUE (fabricante_id, nombre_normalizado)
);

COMMENT ON TABLE public.modelo IS
    'Catálogo GLOBAL de modelos, colgando de fabricante (cascada). Alta vía RPC crear_modelo.';

CREATE INDEX IF NOT EXISTS modelo_fabricante_id_idx ON public.modelo (fabricante_id);

-- ---------------------------------------------------------------- RLS: lectura global, escritura solo por RPC

ALTER TABLE public.fabricante ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.modelo     ENABLE ROW LEVEL SECURITY;

CREATE POLICY fabricante_select ON public.fabricante
    FOR SELECT USING (true);
CREATE POLICY modelo_select ON public.modelo
    FOR SELECT USING (true);

GRANT SELECT ON public.fabricante, public.modelo TO authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.fabricante FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.modelo     FROM authenticated, anon;

-- ---------------------------------------------------------------- helper: gestor en ALGUNA empresa

CREATE OR REPLACE FUNCTION public.usuario_es_gestor_en_alguna_empresa()
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
           AND activo = true
           AND rol = ANY (ARRAY['owner', 'admin', 'gestor'])
    );
$$;

COMMENT ON FUNCTION public.usuario_es_gestor_en_alguna_empresa() IS
    'TRUE si el usuario autenticado es gestor (owner/admin/gestor) en alguna empresa. Guard del alta al catálogo global.';

-- ---------------------------------------------------------------- RPC de alta idempotente

CREATE OR REPLACE FUNCTION public.crear_fabricante(p_nombre text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id uuid;
BEGIN
    IF NOT public.usuario_es_gestor_en_alguna_empresa() THEN
        RAISE EXCEPTION 'sin permiso para dar de alta fabricantes'
            USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.fabricante (nombre)
    VALUES (btrim(p_nombre))
    ON CONFLICT (nombre_normalizado) DO UPDATE SET nombre = public.fabricante.nombre
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_fabricante(text) IS
    'Alta idempotente de fabricante (busca-o-crea por nombre normalizado). Valida rol gestor.';

CREATE OR REPLACE FUNCTION public.crear_modelo(p_fabricante_id uuid, p_nombre text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id uuid;
BEGIN
    IF NOT public.usuario_es_gestor_en_alguna_empresa() THEN
        RAISE EXCEPTION 'sin permiso para dar de alta modelos'
            USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.fabricante WHERE id = p_fabricante_id) THEN
        RAISE EXCEPTION 'el fabricante indicado no existe'
            USING ERRCODE = '23503';
    END IF;

    INSERT INTO public.modelo (fabricante_id, nombre)
    VALUES (p_fabricante_id, btrim(p_nombre))
    ON CONFLICT (fabricante_id, nombre_normalizado) DO UPDATE SET nombre = public.modelo.nombre
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_modelo(uuid, text) IS
    'Alta idempotente de modelo bajo un fabricante (cascada; busca-o-crea por (fabricante_id, nombre normalizado)). Valida rol gestor y existencia del fabricante.';

REVOKE ALL ON FUNCTION public.crear_fabricante(text)       FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.crear_modelo(uuid, text)     FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.crear_fabricante(text)    TO authenticated;
GRANT EXECUTE ON FUNCTION public.crear_modelo(uuid, text)  TO authenticated;
