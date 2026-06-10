-- =============================================================================
-- T-14b — Validadores estructurales de denominaciones.
--
-- Como complemento defensivo a la validación profunda de la Edge Function
-- `crear-recaudacion` (T-21), añadimos:
--   * `validar_desglose_denominaciones(jsonb)`: comprueba la forma del array
--     y que las denominaciones sean del conjunto permitido.
--   * `sumar_desglose(jsonb)`: calcula el total económico del desglose.
--   * Constraints en `recaudacion` que aseguran que los desgloses cuadran
--     con la recaudación bruta y la parte del local.
--
-- De este modo, ni siquiera un acceso directo con service_role puede
-- guardar una recaudación inconsistente.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.validar_desglose_denominaciones(p_desglose jsonb)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
PARALLEL SAFE
AS $$
DECLARE
    item       jsonb;
    v_denom    numeric(5, 2);
    v_cantidad bigint;
BEGIN
    IF p_desglose IS NULL OR jsonb_typeof(p_desglose) <> 'array' THEN
        RETURN false;
    END IF;

    FOR item IN SELECT * FROM jsonb_array_elements(p_desglose) LOOP
        IF jsonb_typeof(item) <> 'object' THEN
            RETURN false;
        END IF;
        IF NOT (item ? 'denominacion' AND item ? 'cantidad') THEN
            RETURN false;
        END IF;

        BEGIN
            v_denom    := (item->>'denominacion')::numeric(5, 2);
            v_cantidad := (item->>'cantidad')::bigint;
        EXCEPTION WHEN others THEN
            RETURN false;
        END;

        IF v_denom NOT IN (0.10, 0.20, 0.50, 1, 2, 5, 10, 20, 50) THEN
            RETURN false;
        END IF;
        IF v_cantidad < 0 THEN
            RETURN false;
        END IF;
    END LOOP;

    RETURN true;
END;
$$;

COMMENT ON FUNCTION public.validar_desglose_denominaciones(jsonb) IS
    'TRUE si el jsonb es un array de {denominacion, cantidad} con denominaciones permitidas y cantidades >= 0.';

CREATE OR REPLACE FUNCTION public.sumar_desglose(p_desglose jsonb)
RETURNS numeric(10, 2)
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT COALESCE(
        SUM(
            ((item->>'denominacion')::numeric(10, 2))
            * ((item->>'cantidad')::bigint)
        ),
        0
    )::numeric(10, 2)
    FROM jsonb_array_elements(p_desglose) AS t(item)
$$;

COMMENT ON FUNCTION public.sumar_desglose(jsonb) IS
    'Suma el valor económico de un desglose de denominaciones (denominacion * cantidad).';

-- Constraints sobre recaudacion: el desglose debe ser válido y cuadrar.
ALTER TABLE public.recaudacion
    ADD CONSTRAINT chk_desglose_total_estructura
        CHECK (public.validar_desglose_denominaciones(desglose_total));

ALTER TABLE public.recaudacion
    ADD CONSTRAINT chk_desglose_local_estructura
        CHECK (public.validar_desglose_denominaciones(desglose_local));

ALTER TABLE public.recaudacion
    ADD CONSTRAINT chk_desglose_total_suma
        CHECK (public.sumar_desglose(desglose_total) = recaudacion_bruta);

ALTER TABLE public.recaudacion
    ADD CONSTRAINT chk_desglose_local_suma
        CHECK (public.sumar_desglose(desglose_local) = parte_local);
