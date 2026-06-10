-- =============================================================================
-- T-13b — Tipo `baseline_info` y función `obtener_baseline`.
--
-- Calcula los contadores y la fecha de referencia que sirven de baseline
-- para una nueva recaudación, eligiendo el evento más reciente entre:
--   1. La última recaudación FIRME anterior a `p_fecha`.
--   2. El último cambio de placa anterior a `p_fecha`.
--   3. La fecha de inicio + contadores base de la instalación.
--
-- En caso de empate de fechas (improbable en práctica pero posible),
-- gana el cambio de placa porque conceptualmente "resetea" la máquina.
--
-- Las recaudaciones ANULADAS se ignoran a propósito: la siguiente
-- recaudación recalcula la baseline como si la anulada nunca hubiera
-- ocurrido (ver design.md §3.9 y §HU-15).
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'baseline_info' AND typnamespace = 'public'::regnamespace
    ) THEN
        CREATE TYPE public.baseline_info AS (
            entradas         bigint,
            salidas          bigint,
            fecha_referencia timestamptz,
            origen           text,
            referencia_id    uuid
        );
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.obtener_baseline(
    p_instalacion_id uuid,
    p_fecha          timestamptz
) RETURNS public.baseline_info
LANGUAGE plpgsql
STABLE
PARALLEL SAFE
AS $$
DECLARE
    v_result public.baseline_info;
    v_inst   public.instalacion%ROWTYPE;
    v_rec    public.recaudacion%ROWTYPE;
    v_cp     public.cambio_placa%ROWTYPE;
BEGIN
    SELECT * INTO v_inst FROM public.instalacion WHERE id = p_instalacion_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'instalacion no encontrada: %', p_instalacion_id
            USING ERRCODE = 'no_data_found';
    END IF;

    SELECT *
      INTO v_rec
      FROM public.recaudacion
     WHERE instalacion_id = p_instalacion_id
       AND fecha < p_fecha
       AND estado = 'firme'
     ORDER BY fecha DESC
     LIMIT 1;

    SELECT *
      INTO v_cp
      FROM public.cambio_placa
     WHERE instalacion_id = p_instalacion_id
       AND fecha < p_fecha
     ORDER BY fecha DESC
     LIMIT 1;

    -- Empate -> gana cambio_placa (resetea máquina). Si no hay cambio_placa
    -- pero sí recaudación, esa manda. Si no hay nada, se usa la base.
    IF v_cp.id IS NOT NULL AND (v_rec.id IS NULL OR v_cp.fecha >= v_rec.fecha) THEN
        v_result.entradas         := v_cp.contador_entradas_nuevo;
        v_result.salidas          := v_cp.contador_salidas_nuevo;
        v_result.fecha_referencia := v_cp.fecha;
        v_result.origen           := 'cambio_placa';
        v_result.referencia_id    := v_cp.id;
    ELSIF v_rec.id IS NOT NULL THEN
        v_result.entradas         := v_rec.contador_entradas_actual;
        v_result.salidas          := v_rec.contador_salidas_actual;
        v_result.fecha_referencia := v_rec.fecha;
        v_result.origen           := 'recaudacion_anterior';
        v_result.referencia_id    := v_rec.id;
    ELSE
        v_result.entradas         := v_inst.contador_entradas_base;
        v_result.salidas          := v_inst.contador_salidas_base;
        v_result.fecha_referencia := v_inst.fecha_inicio::timestamptz;
        v_result.origen           := 'instalacion_base';
        v_result.referencia_id    := v_inst.id;
    END IF;

    RETURN v_result;
END;
$$;

COMMENT ON FUNCTION public.obtener_baseline(uuid, timestamptz) IS
    'Devuelve los contadores y fecha de referencia para calcular la próxima recaudación de una instalación.';
