-- Elimina el "tipo" de licencia (retirado de la UI en #125/#126) y restringe la
-- comunidad autónoma a la lista cerrada de las 19 CCAA.
--
-- No-breaking: crear/actualizar_licencia CONSERVAN su firma exacta
-- (uuid, text, text, date, date, text, text, text). p_tipo se queda como
-- argumento INERTE (los clientes ya envían null) para no romper grants ni el
-- guardarraíl 08; retirarlo de la firma sería un cleanup posterior. Aquí solo se
-- deja de escribir/leer la columna, se elimina, y se añade el CHECK de CCAA.

CREATE OR REPLACE FUNCTION public.crear_licencia(
    p_empresa_id         uuid,
    p_numero             text,
    p_tipo               text,
    p_fecha_expedicion   date,
    p_fecha_caducidad    date,
    p_comunidad_autonoma text,
    p_estado             text,
    p_notas              text DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id uuid;
BEGIN
    IF NOT public.usuario_es_gestor(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar licencias'
            USING ERRCODE = '42501';
    END IF;

    -- p_tipo se ignora a propósito (el tipo de licencia se retiró); se conserva
    -- en la firma para no romper clientes ni grants existentes.
    INSERT INTO public.licencia (
        empresa_id, numero, fecha_expedicion, fecha_caducidad,
        comunidad_autonoma, estado, notas
    ) VALUES (
        p_empresa_id, p_numero, p_fecha_expedicion, p_fecha_caducidad,
        p_comunidad_autonoma, p_estado, p_notas
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.actualizar_licencia(
    p_id                 uuid,
    p_numero             text,
    p_tipo               text,
    p_fecha_expedicion   date,
    p_fecha_caducidad    date,
    p_comunidad_autonoma text,
    p_estado             text,
    p_notas              text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.licencia WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'licencia no encontrada: %', p_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar licencias'
            USING ERRCODE = '42501';
    END IF;

    -- p_tipo se ignora a propósito (ver crear_licencia).
    UPDATE public.licencia SET
        numero             = p_numero,
        fecha_expedicion   = p_fecha_expedicion,
        fecha_caducidad    = p_fecha_caducidad,
        comunidad_autonoma = p_comunidad_autonoma,
        estado             = p_estado,
        notas              = p_notas
    WHERE id = p_id;
END;
$$;

-- La vista operativa exponía lic.tipo como licencia_tipo; ya nadie lo lee. Hay
-- que retirar esa columna antes de poder eliminar licencia.tipo (dependencia).
-- CREATE OR REPLACE no permite quitar columnas del medio: DROP + CREATE. Recupera
-- los grants por defecto de Supabase igual que el CREATE original.
DROP VIEW public.v_instalacion_actual;
CREATE VIEW public.v_instalacion_actual
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
    l.direccion               AS local_direccion,
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

COMMENT ON VIEW public.v_instalacion_actual IS
    'Instalaciones activas con sus joins habituales y baseline pre-calculada.';

-- Ya nadie lee ni escribe la columna.
ALTER TABLE public.licencia DROP COLUMN tipo;

-- Comunidad autónoma restringida a la lista cerrada (NULL permitido).
-- Lista de oro: IDÉNTICA a COMUNIDADES_AUTONOMAS de web y Android.
ALTER TABLE public.licencia
    ADD CONSTRAINT licencia_comunidad_autonoma_check
    CHECK (
        comunidad_autonoma IS NULL OR comunidad_autonoma IN (
            'Andalucía', 'Aragón', 'Asturias', 'Islas Baleares', 'Canarias',
            'Cantabria', 'Castilla-La Mancha', 'Castilla y León', 'Cataluña',
            'Comunidad Valenciana', 'Extremadura', 'Galicia', 'Madrid', 'Murcia',
            'Navarra', 'País Vasco', 'La Rioja', 'Ceuta', 'Melilla'
        )
    );
