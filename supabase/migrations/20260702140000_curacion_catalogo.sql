-- Curación del catálogo global: renombrar y fusionar fabricantes/modelos,
-- restringido a admins de catálogo (usuario.es_admin_catalogo). Propaga el
-- nombre denormalizado a maquina.fabricante/maquina.modelo y, al fusionar,
-- repunta las FK y borra la entrada absorbida.

-- 1) Flag global de admin de catálogo (perfil 1:1 con auth.users).
ALTER TABLE public.usuario
    ADD COLUMN IF NOT EXISTS es_admin_catalogo boolean NOT NULL DEFAULT false;

-- Concede el flag al owner ya existente (no-op en un reset limpio: el usuario
-- aún no existe cuando corren las migraciones; el seed lo fija allí también).
UPDATE public.usuario SET es_admin_catalogo = true
    WHERE id = 'a0000000-0000-0000-0000-000000000001';

-- 2) Helper de permiso (revocado de todos; lo invocan las RPC SECURITY DEFINER).
CREATE OR REPLACE FUNCTION public.usuario_es_admin_catalogo()
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public, pg_catalog
AS $$
    SELECT COALESCE(
        (SELECT es_admin_catalogo FROM public.usuario WHERE id = auth.uid()),
        false
    );
$$;
REVOKE ALL ON FUNCTION public.usuario_es_admin_catalogo() FROM PUBLIC, anon, authenticated;

-- 3) Renombrar fabricante.
CREATE OR REPLACE FUNCTION public.renombrar_fabricante(p_id uuid, p_nombre text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_nombre       text := btrim(p_nombre);
    v_norm         text := lower(btrim(p_nombre));
    v_norm_actual  text;
BEGIN
    IF NOT public.usuario_es_admin_catalogo() THEN
        RAISE EXCEPTION 'sin permiso para curar el catálogo' USING ERRCODE = '42501';
    END IF;
    IF v_nombre = '' THEN
        RAISE EXCEPTION 'el nombre no puede estar vacío' USING ERRCODE = '23514';
    END IF;

    SELECT nombre_normalizado INTO v_norm_actual FROM public.fabricante WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'fabricante no encontrado: %', p_id USING ERRCODE = 'no_data_found';
    END IF;

    -- Colisión con OTRO fabricante (mismo normalizado, distinto id) → fusionar.
    IF v_norm <> v_norm_actual AND EXISTS (
        SELECT 1 FROM public.fabricante WHERE nombre_normalizado = v_norm AND id <> p_id
    ) THEN
        RAISE EXCEPTION 'ya existe un fabricante «%»; usa fusionar', v_nombre
            USING ERRCODE = '23505';
    END IF;

    UPDATE public.fabricante SET nombre = v_nombre WHERE id = p_id;
    -- Reflow del texto denormalizado.
    UPDATE public.maquina SET fabricante = v_nombre WHERE fabricante_id = p_id;
END;
$$;

-- 4) Renombrar modelo (colisión acotada a su fabricante).
CREATE OR REPLACE FUNCTION public.renombrar_modelo(p_id uuid, p_nombre text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_nombre       text := btrim(p_nombre);
    v_norm         text := lower(btrim(p_nombre));
    v_norm_actual  text;
    v_fab          uuid;
BEGIN
    IF NOT public.usuario_es_admin_catalogo() THEN
        RAISE EXCEPTION 'sin permiso para curar el catálogo' USING ERRCODE = '42501';
    END IF;
    IF v_nombre = '' THEN
        RAISE EXCEPTION 'el nombre no puede estar vacío' USING ERRCODE = '23514';
    END IF;

    SELECT nombre_normalizado, fabricante_id INTO v_norm_actual, v_fab
        FROM public.modelo WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'modelo no encontrado: %', p_id USING ERRCODE = 'no_data_found';
    END IF;

    IF v_norm <> v_norm_actual AND EXISTS (
        SELECT 1 FROM public.modelo
        WHERE fabricante_id = v_fab AND nombre_normalizado = v_norm AND id <> p_id
    ) THEN
        RAISE EXCEPTION 'ya existe un modelo «%» en ese fabricante; usa fusionar', v_nombre
            USING ERRCODE = '23505';
    END IF;

    UPDATE public.modelo SET nombre = v_nombre WHERE id = p_id;
    UPDATE public.maquina SET modelo = v_nombre WHERE modelo_id = p_id;
END;
$$;

-- 5) Fusionar modelo (exige mismo fabricante para no romper la coherencia).
CREATE OR REPLACE FUNCTION public.fusionar_modelo(p_origen uuid, p_destino uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_fab_origen    uuid;
    v_fab_destino   uuid;
    v_nombre_destino text;
BEGIN
    IF NOT public.usuario_es_admin_catalogo() THEN
        RAISE EXCEPTION 'sin permiso para curar el catálogo' USING ERRCODE = '42501';
    END IF;
    IF p_origen = p_destino THEN
        RAISE EXCEPTION 'origen y destino no pueden ser iguales' USING ERRCODE = '22023';
    END IF;

    SELECT fabricante_id INTO v_fab_origen FROM public.modelo WHERE id = p_origen;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'modelo origen no encontrado: %', p_origen USING ERRCODE = 'no_data_found';
    END IF;
    SELECT fabricante_id, nombre INTO v_fab_destino, v_nombre_destino
        FROM public.modelo WHERE id = p_destino;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'modelo destino no encontrado: %', p_destino USING ERRCODE = 'no_data_found';
    END IF;
    IF v_fab_origen <> v_fab_destino THEN
        RAISE EXCEPTION 'solo se pueden fusionar modelos del mismo fabricante'
            USING ERRCODE = '22023';
    END IF;

    -- Repuntar máquinas del origen al destino (FK + texto) y borrar el absorbido.
    UPDATE public.maquina SET modelo_id = p_destino, modelo = v_nombre_destino
        WHERE modelo_id = p_origen;
    DELETE FROM public.modelo WHERE id = p_origen;
END;
$$;

-- 6) Fusionar fabricante (mueve modelos hijos, dedup por colisión, repunta máquinas).
CREATE OR REPLACE FUNCTION public.fusionar_fabricante(p_origen uuid, p_destino uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_nombre_destino text;
    m                record;
    v_dest_modelo    uuid;
    v_dest_mod_nombre text;
BEGIN
    IF NOT public.usuario_es_admin_catalogo() THEN
        RAISE EXCEPTION 'sin permiso para curar el catálogo' USING ERRCODE = '42501';
    END IF;
    IF p_origen = p_destino THEN
        RAISE EXCEPTION 'origen y destino no pueden ser iguales' USING ERRCODE = '22023';
    END IF;

    SELECT nombre INTO v_nombre_destino FROM public.fabricante WHERE id = p_destino;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'fabricante destino no encontrado: %', p_destino USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.fabricante WHERE id = p_origen) THEN
        RAISE EXCEPTION 'fabricante origen no encontrado: %', p_origen USING ERRCODE = 'no_data_found';
    END IF;

    -- Cada modelo del ORIGEN: si el DESTINO no tiene uno con ese nombre
    -- normalizado, se mueve; si colisiona, se fusiona (máquinas al del destino)
    -- y se borra el del origen.
    FOR m IN
        SELECT id, nombre_normalizado FROM public.modelo WHERE fabricante_id = p_origen
    LOOP
        SELECT id, nombre INTO v_dest_modelo, v_dest_mod_nombre
            FROM public.modelo
            WHERE fabricante_id = p_destino AND nombre_normalizado = m.nombre_normalizado;
        IF v_dest_modelo IS NULL THEN
            UPDATE public.modelo SET fabricante_id = p_destino WHERE id = m.id;
        ELSE
            UPDATE public.maquina SET modelo_id = v_dest_modelo, modelo = v_dest_mod_nombre
                WHERE modelo_id = m.id;
            DELETE FROM public.modelo WHERE id = m.id;
        END IF;
    END LOOP;

    -- Repuntar todas las máquinas del fabricante origen al destino (FK + texto).
    UPDATE public.maquina SET fabricante_id = p_destino, fabricante = v_nombre_destino
        WHERE fabricante_id = p_origen;

    -- Borrar el fabricante absorbido (ya sin modelos ni máquinas que lo referencien).
    DELETE FROM public.fabricante WHERE id = p_origen;
END;
$$;

-- 7) Grants: EXECUTE solo a authenticated (el guard interno exige admin).
REVOKE ALL ON FUNCTION public.renombrar_fabricante(uuid, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.renombrar_modelo(uuid, text)     FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.fusionar_fabricante(uuid, uuid)  FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.fusionar_modelo(uuid, uuid)      FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.renombrar_fabricante(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.renombrar_modelo(uuid, text)     TO authenticated;
GRANT EXECUTE ON FUNCTION public.fusionar_fabricante(uuid, uuid)  TO authenticated;
GRANT EXECUTE ON FUNCTION public.fusionar_modelo(uuid, uuid)      TO authenticated;
