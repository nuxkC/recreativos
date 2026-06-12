-- T-216 — El concepto (notas) es OBLIGATORIO al dar de alta un préstamo.
--
-- Sin concepto acabábamos con deudas de las que no se sabe el porqué. La tolva
-- no lo necesita (su concepto es implícito); solo aplica a `crear_prestamo`.
-- `registrar_recuperacion_efectivo` y `condonar_credito` siguen con notas
-- opcional (no son alta de deuda).
--
-- Recreamos la función (CREATE OR REPLACE preserva grants) con la misma firma
-- y añadimos la validación tras los checks de principal/tipo_interés. Migración
-- aditiva: no se edita la de T-212.

CREATE OR REPLACE FUNCTION public.crear_prestamo(
    p_empresa_id   uuid,
    p_local_id     uuid,
    p_principal    numeric,
    p_tipo_interes numeric DEFAULT 0,
    p_fecha        date    DEFAULT NULL,
    p_notas        text    DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id    uuid;
    v_tz    text;
    v_fecha date;
BEGIN
    IF NOT public.usuario_es_gestor(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar deudas del local'
            USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.local WHERE id = p_local_id AND empresa_id = p_empresa_id) THEN
        RAISE EXCEPTION 'local % no pertenece a la empresa %', p_local_id, p_empresa_id
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    IF p_principal IS NULL OR p_principal <= 0 THEN
        RAISE EXCEPTION 'el principal del préstamo debe ser > 0'
            USING ERRCODE = '22023';
    END IF;
    IF COALESCE(p_tipo_interes, 0) < 0 THEN
        RAISE EXCEPTION 'el tipo de interés no puede ser negativo'
            USING ERRCODE = '22023';
    END IF;
    -- T-216: el préstamo requiere un concepto.
    IF p_notas IS NULL OR btrim(p_notas) = '' THEN
        RAISE EXCEPTION 'el préstamo requiere un concepto'
            USING ERRCODE = '22023';
    END IF;

    SELECT COALESCE(zona_horaria, 'Europe/Madrid') INTO v_tz FROM public.empresa WHERE id = p_empresa_id;
    v_fecha := COALESCE(p_fecha, (now() AT TIME ZONE COALESCE(v_tz, 'Europe/Madrid'))::date);

    INSERT INTO public.credito_local (
        empresa_id, local_id, tipo, instalacion_id,
        principal, tipo_interes, fecha, estado, notas
    ) VALUES (
        p_empresa_id, p_local_id, 'prestamo', NULL,
        round(p_principal, 2), COALESCE(p_tipo_interes, 0), v_fecha, 'abierto', btrim(p_notas)
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_prestamo(uuid, uuid, numeric, numeric, date, text) IS
    'Alta de préstamo a un local. Valida rol gestor + tenant y exige concepto (notas no vacío). Devuelve el id de la deuda.';
