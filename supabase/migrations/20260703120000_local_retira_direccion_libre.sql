-- =============================================================================
-- T-277 (cierre) — Se retira la dirección de texto libre `local.direccion`.
--
-- La dirección estructurada (calle, código postal, municipio/provincia INE y
-- CCAA) es ya la ÚNICA fuente de verdad. En vez de guardar además una cadena
-- de texto libre (que se desincronizaría del origen estructurado: apaño), la
-- cadena de display se DERIVA on-read desde los campos estructurados.
--
-- Mecanismo: una función `public.direccion(local)` que compone la cadena. Al
-- llamarse `direccion` y recibir la fila de `local`, PostgREST la expone como
-- "computed column" y Postgres la resuelve vía notación funcional (`l.direccion`),
-- de modo que TODOS los consumidores server-side que ya leían `local.direccion`
-- (vistas `v_instalacion_actual`/`v_recaudacion_historica`, y los SELECT de las
-- Edge Functions de PDF: boletín de instalación y ticket de liquidación) siguen
-- funcionando SIN cambios, ahora leyendo el valor derivado.
--
-- Los clientes (web/Android) dejan de enviar `p_direccion` a las RPC y componen
-- su propia cadena de display con sus formateadores (`formatearDireccion` web /
-- `formatearDireccionLocal` Android). Cambio coordinado (breaking): `p_direccion`
-- era requerido; BBDD y clientes se despliegan juntos.
-- =============================================================================

-- 1) Función de composición = fuente única server-side de la cadena de display.
--    STABLE: lee las tablas de referencia (municipio/provincia) pero no muta.
--    Al tomar la fila de `local` es, además, una computed column de PostgREST.
CREATE OR REPLACE FUNCTION public.direccion(l public.local) RETURNS text
LANGUAGE sql
STABLE
SET search_path = public, pg_catalog
AS $$
    SELECT NULLIF(concat_ws(', ',
        NULLIF(l.calle, ''),
        NULLIF(concat_ws(' ',
            NULLIF(l.codigo_postal, ''),
            (SELECT m.nombre FROM public.municipio m WHERE m.codigo = l.municipio_codigo)
        ), ''),
        COALESCE(
            (SELECT p.nombre FROM public.provincia p WHERE p.codigo = l.provincia_codigo),
            NULLIF(l.comunidad_autonoma, '')
        )
    ), '')
$$;

COMMENT ON FUNCTION public.direccion(public.local) IS
    'Dirección de display DERIVADA de los campos estructurados (calle, CP, municipio, provincia; cae a CCAA). Reemplaza la antigua columna de texto libre: PostgREST la expone como computed column «direccion» y las vistas la resuelven vía l.direccion.';

REVOKE ALL ON FUNCTION public.direccion(public.local) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.direccion(public.local) TO authenticated, service_role;

-- 2) Vistas: se recrean para que `local_direccion` use la función explícita
--    `public.direccion(l)` en lugar de la columna. Así dejan de depender de la
--    columna y el DROP COLUMN posterior es posible. Mismo nombre/tipo de columna
--    (text) y mismo orden -> CREATE OR REPLACE conserva grants y dependientes.
CREATE OR REPLACE VIEW public.v_instalacion_actual
WITH (security_invoker = true) AS
SELECT
    i.id                       AS instalacion_id,
    i.empresa_id,
    i.maquina_id,
    i.licencia_id,
    i.local_id,
    i.fecha_inicio,
    i.tasa_semanal,
    i.porcentaje_local,
    i.contador_entradas_base,
    i.contador_salidas_base,
    i.estado,
    m.numero_serie            AS maquina_numero_serie,
    m.modelo                  AS maquina_modelo,
    m.fabricante              AS maquina_fabricante,
    m.valor_credito           AS maquina_valor_credito,
    l.nombre                  AS local_nombre,
    public.direccion(l)       AS local_direccion,
    lic.numero                AS licencia_numero,
    lic.fecha_caducidad       AS licencia_fecha_caducidad,
    bl.entradas               AS baseline_entradas,
    bl.salidas                AS baseline_salidas,
    bl.fecha_referencia       AS baseline_fecha,
    bl.origen                 AS baseline_origen,
    bl.referencia_id          AS baseline_referencia_id
FROM public.instalacion i
JOIN public.maquina m ON m.id = i.maquina_id
JOIN public.local l ON l.id = i.local_id
JOIN public.licencia lic ON lic.id = i.licencia_id
LEFT JOIN LATERAL public.obtener_baseline(i.id, now()) AS bl ON true
WHERE i.estado = 'activa';

CREATE OR REPLACE VIEW public.v_recaudacion_historica
WITH (security_invoker = true) AS
SELECT
    r.*,
    i.local_id,
    i.maquina_id,
    i.licencia_id,
    l.nombre            AS local_nombre,
    public.direccion(l) AS local_direccion,
    m.numero_serie      AS maquina_numero_serie,
    m.modelo            AS maquina_modelo,
    m.fabricante        AS maquina_fabricante,
    lic.numero          AS licencia_numero
FROM public.recaudacion r
JOIN public.instalacion i   ON i.id   = r.instalacion_id
JOIN public.local       l   ON l.id   = i.local_id
JOIN public.maquina     m   ON m.id   = i.maquina_id
JOIN public.licencia    lic ON lic.id = i.licencia_id;

-- 3) RPC crear_local: se retira `p_direccion` (firma 13 -> 12 args). DROP de la
--    firma vieja + CREATE de la nueva; se reemiten grants/comment.
DROP FUNCTION IF EXISTS public.crear_local(uuid, text, text, text, text, text, text, text, text, text, text, text, text);
CREATE FUNCTION public.crear_local(
    p_empresa_id         uuid,
    p_nombre             text,
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
        empresa_id, nombre, cif_o_nif, titular_nombre,
        telefono, email, notas,
        comunidad_autonoma, provincia_codigo, municipio_codigo, calle, codigo_postal
    ) VALUES (
        p_empresa_id, p_nombre, p_cif_o_nif, p_titular_nombre,
        p_telefono, p_email, p_notas,
        p_comunidad_autonoma, p_provincia_codigo, p_municipio_codigo, p_calle, p_codigo_postal
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

-- 4) RPC actualizar_local: mismo retiro de `p_direccion`.
DROP FUNCTION IF EXISTS public.actualizar_local(uuid, text, text, text, text, text, text, text, text, text, text, text, text);
CREATE FUNCTION public.actualizar_local(
    p_id                 uuid,
    p_nombre             text,
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

-- 5) Grants + documentación de la firma nueva (12 args).
REVOKE ALL ON FUNCTION public.crear_local(uuid, text, text, text, text, text, text, text, text, text, text, text)      FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.actualizar_local(uuid, text, text, text, text, text, text, text, text, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.crear_local(uuid, text, text, text, text, text, text, text, text, text, text, text)      TO authenticated;
GRANT EXECUTE ON FUNCTION public.actualizar_local(uuid, text, text, text, text, text, text, text, text, text, text, text) TO authenticated;

COMMENT ON FUNCTION public.crear_local(uuid, text, text, text, text, text, text, text, text, text, text, text) IS
    'Alta de local con dirección estructurada opcional. Valida rol gestor + tenant y coherencia CCAA⊃provincia⊃municipio. Devuelve el id.';
COMMENT ON FUNCTION public.actualizar_local(uuid, text, text, text, text, text, text, text, text, text, text, text) IS
    'Edición de local. Valida rol gestor + tenant y coherencia de dirección. Sobreescribe todos los campos (pasar NULL limpia).';

-- 6) Ya nadie escribe ni lee la columna (los consumidores usan public.direccion).
ALTER TABLE public.local DROP COLUMN direccion;
