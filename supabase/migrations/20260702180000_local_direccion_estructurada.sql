-- Dirección estructurada del local: comunidad autónoma (lista de oro), provincia
-- y municipio (FK a las tablas de referencia del INE), calle y código postal.
-- Se conserva `direccion` (texto libre) durante la transición; las columnas
-- nuevas son NULL hasta que los formularios (web/Android) empiecen a poblarlas.
-- Las RPC de escritura ganan cinco parámetros opcionales al final (DEFAULT NULL):
-- se DROPea la firma de 8 args y se recrea con 13, de modo que las llamadas
-- antiguas de 8 argumentos siguen resolviendo a la nueva firma vía defaults y el
-- cambio NO rompe a los clientes actuales (sin overload ambiguo).

-- 1) Columnas de dirección estructurada (aditivas, nullable en transición).
ALTER TABLE public.local
    ADD COLUMN IF NOT EXISTS comunidad_autonoma text,
    ADD COLUMN IF NOT EXISTS provincia_codigo    text REFERENCES public.provincia(codigo) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS municipio_codigo    text REFERENCES public.municipio(codigo) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS calle               text,
    ADD COLUMN IF NOT EXISTS codigo_postal       text;

-- 2) CCAA restringida a la lista de oro (IDÉNTICA al CHECK de licencia). NULL ok.
ALTER TABLE public.local
    ADD CONSTRAINT local_comunidad_autonoma_check
    CHECK (
        comunidad_autonoma IS NULL OR comunidad_autonoma IN (
            'Andalucía', 'Aragón', 'Asturias', 'Islas Baleares', 'Canarias',
            'Cantabria', 'Castilla-La Mancha', 'Castilla y León', 'Cataluña',
            'Comunidad Valenciana', 'Extremadura', 'Galicia', 'Madrid', 'Murcia',
            'Navarra', 'País Vasco', 'La Rioja', 'Ceuta', 'Melilla'
        )
    );

-- Código postal español: exactamente 5 dígitos (NULL permitido en transición).
ALTER TABLE public.local
    ADD CONSTRAINT local_codigo_postal_check
    CHECK (codigo_postal IS NULL OR codigo_postal ~ '^[0-9]{5}$');

-- 3) Coherencia jerárquica CCAA ⊃ provincia ⊃ municipio. Las FK ya garantizan
-- que los códigos existen; esto impide combinaciones incoherentes (p. ej. un
-- municipio de otra provincia, o una provincia de otra CCAA). Interno: lo
-- invocan las RPC (revocado de todos los roles de cliente).
CREATE OR REPLACE FUNCTION public._validar_direccion_local(
    p_comunidad_autonoma text,
    p_provincia_codigo   text,
    p_municipio_codigo   text
) RETURNS void
LANGUAGE plpgsql
STABLE
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_prov_ccaa text;
    v_muni_prov text;
BEGIN
    IF p_provincia_codigo IS NOT NULL THEN
        IF p_comunidad_autonoma IS NULL THEN
            RAISE EXCEPTION 'falta la comunidad autónoma para la provincia %', p_provincia_codigo
                USING ERRCODE = '23514';
        END IF;
        SELECT comunidad_autonoma INTO v_prov_ccaa
            FROM public.provincia WHERE codigo = p_provincia_codigo;
        IF v_prov_ccaa IS DISTINCT FROM p_comunidad_autonoma THEN
            RAISE EXCEPTION 'la provincia % no pertenece a «%»', p_provincia_codigo, p_comunidad_autonoma
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF p_municipio_codigo IS NOT NULL THEN
        IF p_provincia_codigo IS NULL THEN
            RAISE EXCEPTION 'falta la provincia para el municipio %', p_municipio_codigo
                USING ERRCODE = '23514';
        END IF;
        SELECT provincia_codigo INTO v_muni_prov
            FROM public.municipio WHERE codigo = p_municipio_codigo;
        IF v_muni_prov IS DISTINCT FROM p_provincia_codigo THEN
            RAISE EXCEPTION 'el municipio % no pertenece a la provincia %', p_municipio_codigo, p_provincia_codigo
                USING ERRCODE = '23514';
        END IF;
    END IF;
END;
$$;
REVOKE ALL ON FUNCTION public._validar_direccion_local(text, text, text) FROM PUBLIC, anon, authenticated;

-- 4) crear_local: +5 parámetros de dirección (DEFAULT NULL). Se sustituye la
-- firma de 8 args por la de 13; las llamadas de 8 args resuelven por defaults.
DROP FUNCTION IF EXISTS public.crear_local(uuid, text, text, text, text, text, text, text);
CREATE OR REPLACE FUNCTION public.crear_local(
    p_empresa_id         uuid,
    p_nombre             text,
    p_direccion          text,
    p_cif_o_nif          text,
    p_titular_nombre     text,
    p_telefono           text,
    p_email              text,
    p_notas              text DEFAULT NULL,
    p_comunidad_autonoma text DEFAULT NULL,
    p_provincia_codigo   text DEFAULT NULL,
    p_municipio_codigo   text DEFAULT NULL,
    p_calle              text DEFAULT NULL,
    p_codigo_postal      text DEFAULT NULL
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

    PERFORM public._validar_direccion_local(
        p_comunidad_autonoma, p_provincia_codigo, p_municipio_codigo
    );

    INSERT INTO public.local (
        empresa_id, nombre, direccion, cif_o_nif, titular_nombre,
        telefono, email, notas,
        comunidad_autonoma, provincia_codigo, municipio_codigo, calle, codigo_postal
    ) VALUES (
        p_empresa_id, p_nombre, p_direccion, p_cif_o_nif, p_titular_nombre,
        p_telefono, p_email, p_notas,
        p_comunidad_autonoma, p_provincia_codigo, p_municipio_codigo, p_calle, p_codigo_postal
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

-- 5) actualizar_local: mismos +5 parámetros (semántica de sobreescritura como
-- el resto de campos: pasar NULL limpia el valor).
DROP FUNCTION IF EXISTS public.actualizar_local(uuid, text, text, text, text, text, text, text);
CREATE OR REPLACE FUNCTION public.actualizar_local(
    p_id                 uuid,
    p_nombre             text,
    p_direccion          text,
    p_cif_o_nif          text,
    p_titular_nombre     text,
    p_telefono           text,
    p_email              text,
    p_notas              text DEFAULT NULL,
    p_comunidad_autonoma text DEFAULT NULL,
    p_provincia_codigo   text DEFAULT NULL,
    p_municipio_codigo   text DEFAULT NULL,
    p_calle              text DEFAULT NULL,
    p_codigo_postal      text DEFAULT NULL
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

    PERFORM public._validar_direccion_local(
        p_comunidad_autonoma, p_provincia_codigo, p_municipio_codigo
    );

    UPDATE public.local SET
        nombre             = p_nombre,
        direccion          = p_direccion,
        cif_o_nif          = p_cif_o_nif,
        titular_nombre     = p_titular_nombre,
        telefono           = p_telefono,
        email              = p_email,
        notas              = p_notas,
        comunidad_autonoma = p_comunidad_autonoma,
        provincia_codigo   = p_provincia_codigo,
        municipio_codigo   = p_municipio_codigo,
        calle              = p_calle,
        codigo_postal      = p_codigo_postal
    WHERE id = p_id;
END;
$$;

-- 6) Grants: EXECUTE solo a authenticated (el guard interno exige gestor).
REVOKE ALL ON FUNCTION public.crear_local(uuid, text, text, text, text, text, text, text, text, text, text, text, text)      FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.actualizar_local(uuid, text, text, text, text, text, text, text, text, text, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.crear_local(uuid, text, text, text, text, text, text, text, text, text, text, text, text)      TO authenticated;
GRANT EXECUTE ON FUNCTION public.actualizar_local(uuid, text, text, text, text, text, text, text, text, text, text, text, text) TO authenticated;

-- 7) Documentación (el DROP borró los COMMENT de la firma anterior).
COMMENT ON FUNCTION public.crear_local(uuid, text, text, text, text, text, text, text, text, text, text, text, text) IS
    'Alta de local con dirección estructurada opcional. Valida rol gestor + tenant y coherencia CCAA⊃provincia⊃municipio. Devuelve el id.';
-- actualizar_local sobreescribe TODOS los campos, incluida la dirección
-- estructurada: pasar NULL la limpia (necesario para la cascada, donde cambiar
-- de CCAA debe resetear provincia/municipio). Por eso los clientes deben enviar
-- siempre los 13 args. Riesgo de transición controlado: ninguna fila tiene
-- dirección estructurada hasta que web/Android (PR-3/PR-4) empiecen a poblarla,
-- y ambos clientes deben desplegarse con la firma de 13 args antes de esa fecha.
COMMENT ON FUNCTION public.actualizar_local(uuid, text, text, text, text, text, text, text, text, text, text, text, text) IS
    'Edición de local. Valida rol gestor + tenant y coherencia de dirección. Sobreescribe todos los campos (incluida la dirección estructurada): pasar NULL limpia. Los clientes deben enviar los 13 args.';
