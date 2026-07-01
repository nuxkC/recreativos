-- =============================================================================
-- La máquina referencia el catálogo global fabricante/modelo.
--
-- Enfoque NO-BREAKING: crear/actualizar_maquina conservan su firma de texto y
-- resuelven internamente el nombre->id del catálogo (busca-o-crea), poblando
-- las FK nuevas y denormalizando el nombre canónico en las columnas de texto
-- (que web/Android siguen leyendo). Las columnas de texto se conservan; su
-- borrado y la FK NOT NULL son un PR de cierre posterior.
-- =============================================================================

-- ---------------------------------------------------------------- FK aditivas (nullable en transición)

ALTER TABLE public.maquina
    ADD COLUMN IF NOT EXISTS fabricante_id uuid REFERENCES public.fabricante(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS modelo_id     uuid REFERENCES public.modelo(id)     ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS maquina_fabricante_id_idx ON public.maquina (fabricante_id);
CREATE INDEX IF NOT EXISTS maquina_modelo_id_idx     ON public.maquina (modelo_id);

-- ---------------------------------------------------------------- helper interno: resolver texto -> catálogo

CREATE OR REPLACE FUNCTION public._resolver_catalogo(p_fabricante text, p_modelo text)
RETURNS TABLE (fab_id uuid, mod_id uuid, fab_nombre text, mod_nombre text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_fab_id     uuid;
    v_mod_id     uuid;
    v_fab_nombre text;
    v_mod_nombre text;
BEGIN
    IF p_fabricante IS NOT NULL AND btrim(p_fabricante) <> '' THEN
        INSERT INTO public.fabricante (nombre) VALUES (btrim(p_fabricante))
        ON CONFLICT (nombre_normalizado) DO UPDATE SET nombre = public.fabricante.nombre
        RETURNING id, nombre INTO v_fab_id, v_fab_nombre;

        IF p_modelo IS NOT NULL AND btrim(p_modelo) <> '' THEN
            INSERT INTO public.modelo (fabricante_id, nombre) VALUES (v_fab_id, btrim(p_modelo))
            ON CONFLICT (fabricante_id, nombre_normalizado) DO UPDATE SET nombre = public.modelo.nombre
            RETURNING id, nombre INTO v_mod_id, v_mod_nombre;
        END IF;
    ELSE
        -- sin fabricante no se puede enlazar el modelo al catálogo: se conserva el texto crudo
        v_mod_nombre := NULLIF(btrim(p_modelo), '');
    END IF;

    RETURN QUERY SELECT v_fab_id, v_mod_id, v_fab_nombre, v_mod_nombre;
END;
$$;

COMMENT ON FUNCTION public._resolver_catalogo(text, text) IS
    'Interno: busca-o-crea fabricante/modelo en el catálogo global y devuelve ids + nombres canónicos. Solo lo llaman las RPC de máquina (SECURITY DEFINER).';

REVOKE ALL ON FUNCTION public._resolver_catalogo(text, text) FROM PUBLIC, anon, authenticated;

-- ---------------------------------------------------------------- RPC (misma firma; ahora poblan FK + denormalizan)

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
    v_id  uuid;
    v_cat record;
BEGIN
    IF NOT public.usuario_es_gestor(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar maquinas'
            USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_cat FROM public._resolver_catalogo(p_fabricante, p_modelo);

    INSERT INTO public.maquina (
        empresa_id, numero_serie, modelo, fabricante, modelo_id, fabricante_id, valor_credito,
        contador_entradas_inicial, contador_salidas_inicial, estado, notas
    ) VALUES (
        p_empresa_id, p_numero_serie, v_cat.mod_nombre, v_cat.fab_nombre, v_cat.mod_id, v_cat.fab_id, p_valor_credito,
        p_contador_entradas_inicial, p_contador_salidas_inicial, p_estado, p_notas
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text) IS
    'Alta de maquina. Valida rol gestor + tenant. Resuelve fabricante/modelo al catálogo global (busca-o-crea) y denormaliza el nombre. Devuelve el id.';

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
    v_cat        record;
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

    SELECT * INTO v_cat FROM public._resolver_catalogo(p_fabricante, p_modelo);

    UPDATE public.maquina SET
        numero_serie              = p_numero_serie,
        modelo                    = v_cat.mod_nombre,
        fabricante                = v_cat.fab_nombre,
        modelo_id                 = v_cat.mod_id,
        fabricante_id             = v_cat.fab_id,
        valor_credito             = p_valor_credito,
        contador_entradas_inicial = p_contador_entradas_inicial,
        contador_salidas_inicial  = p_contador_salidas_inicial,
        estado                    = p_estado,
        notas                     = p_notas
    WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.actualizar_maquina(uuid, text, text, text, numeric, bigint, bigint, text, text) IS
    'Edición de maquina. Valida rol gestor + tenant. Resuelve fabricante/modelo al catálogo global y denormaliza el nombre.';

-- ---------------------------------------------------------------- backfill idempotente de datos existentes (cloud/dev)

DO $$
DECLARE
    m       RECORD;
    v_cat   record;
BEGIN
    FOR m IN
        SELECT id, fabricante, modelo FROM public.maquina
         WHERE fabricante_id IS NULL AND fabricante IS NOT NULL AND btrim(fabricante) <> ''
    LOOP
        SELECT * INTO v_cat FROM public._resolver_catalogo(m.fabricante, m.modelo);
        UPDATE public.maquina SET
            fabricante_id = v_cat.fab_id,
            modelo_id     = v_cat.mod_id,
            fabricante    = v_cat.fab_nombre,
            modelo        = v_cat.mod_nombre
        WHERE id = m.id;
    END LOOP;
END $$;
