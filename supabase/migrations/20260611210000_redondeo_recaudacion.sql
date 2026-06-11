-- =============================================================================
-- T-211 — Redondeo opcional de la recaudación bruta (config por empresa).
--
-- Idea: cuando una empresa lo activa, el bruto de cada recaudación se lleva al
-- múltiplo más cercano de `redondeo_recaudacion` (p.ej. 10 €) falseando la
-- lectura de salidas lo justo. Ese contador ajustado se persiste como el real,
-- de modo que la diferencia rueda sola a la siguiente recaudación vía la
-- baseline (obtener_baseline lee `contador_salidas_actual`). No se pierde ni se
-- inventa dinero: solo se reparte en cortes "redondos".
--
-- El cálculo (quién ajusta y cuánto) vive en el SSOT `_shared/calculo.ts`; aquí
-- solo se añade la config y el rastro de auditoría. La lectura original del
-- técnico se conserva en `contador_salidas_leido` / `recaudacion_bruta_real`,
-- que NUNCA entran en ningún cálculo: son solo para que un admin pueda ver
-- cuánto se redondeó cada vez.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- empresa: unidad de redondeo. 0 = desactivado (comportamiento histórico).
-- -----------------------------------------------------------------------------
ALTER TABLE public.empresa
    ADD COLUMN IF NOT EXISTS redondeo_recaudacion smallint NOT NULL DEFAULT 0
        CHECK (redondeo_recaudacion >= 0);

COMMENT ON COLUMN public.empresa.redondeo_recaudacion IS
    'Unidad a la que se redondea el bruto de cada recaudación (0 = sin redondeo, p.ej. 10 = al múltiplo de 10 € más cercano). El ajuste se hace sobre el contador de salidas.';

-- -----------------------------------------------------------------------------
-- recaudacion: rastro de auditoría del redondeo. Nullable porque las filas
-- históricas (anteriores a esta feature) no lo tienen; las nuevas lo rellenan
-- siempre, y `redondeo_aplicado` (0 = sin redondeo) indica si hubo ajuste. No
-- se usa para calcular nada.
-- -----------------------------------------------------------------------------
ALTER TABLE public.recaudacion
    ADD COLUMN IF NOT EXISTS contador_salidas_leido bigint,
    ADD COLUMN IF NOT EXISTS recaudacion_bruta_real numeric(10,2),
    ADD COLUMN IF NOT EXISTS redondeo_aplicado      smallint;

COMMENT ON COLUMN public.recaudacion.contador_salidas_leido IS
    'Lectura de salidas que tecleó el técnico, antes del ajuste por redondeo. Solo auditoría; el contador oficial es contador_salidas_actual.';
COMMENT ON COLUMN public.recaudacion.recaudacion_bruta_real IS
    'Bruto real antes de redondear. Solo auditoría; el bruto oficial es recaudacion_bruta.';
COMMENT ON COLUMN public.recaudacion.redondeo_aplicado IS
    'Unidad de redondeo aplicada a esta recaudación (0/NULL = no se redondeó).';

-- -----------------------------------------------------------------------------
-- actualizar_ajustes_empresa: añade `p_redondeo_recaudacion`. Recreamos la
-- función (no se puede REPLACE cambiando la firma). El nuevo parámetro lleva
-- DEFAULT NULL y se aplica con COALESCE para que un cliente que aún no lo envíe
-- conserve el valor actual en lugar de resetearlo a 0.
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.actualizar_ajustes_empresa(
    uuid, text, text, text, text, text, text, text, text);

CREATE OR REPLACE FUNCTION public.actualizar_ajustes_empresa(
    p_empresa_id           uuid,
    p_nombre               text,
    p_cif                  text,
    p_direccion            text,
    p_telefono             text,
    p_email                text,
    p_zona_horaria         text,
    p_ticket_cabecera      text,
    p_ticket_pie           text,
    p_redondeo_recaudacion smallint DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
BEGIN
    IF NOT public.usuario_es_admin(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para editar la empresa'
            USING ERRCODE = '42501';
    END IF;

    IF p_redondeo_recaudacion IS NOT NULL AND p_redondeo_recaudacion < 0 THEN
        RAISE EXCEPTION 'redondeo_recaudacion no puede ser negativo'
            USING ERRCODE = '22023';
    END IF;

    UPDATE public.empresa SET
        nombre               = p_nombre,
        cif                  = p_cif,
        direccion            = p_direccion,
        telefono             = p_telefono,
        email                = p_email,
        zona_horaria         = p_zona_horaria,
        ticket_cabecera      = p_ticket_cabecera,
        ticket_pie           = p_ticket_pie,
        redondeo_recaudacion = COALESCE(p_redondeo_recaudacion, redondeo_recaudacion)
    WHERE id = p_empresa_id;
END;
$$;

COMMENT ON FUNCTION public.actualizar_ajustes_empresa(
    uuid, text, text, text, text, text, text, text, text, smallint) IS
    'Edita los ajustes editables de la empresa (incluye el redondeo de recaudación). Valida rol admin + tenant.';

REVOKE ALL ON FUNCTION public.actualizar_ajustes_empresa(
    uuid, text, text, text, text, text, text, text, text, smallint) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.actualizar_ajustes_empresa(
    uuid, text, text, text, text, text, text, text, text, smallint) TO authenticated;
