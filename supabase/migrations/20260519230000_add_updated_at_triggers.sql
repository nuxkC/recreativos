-- =============================================================================
-- T-14a — Trigger genérico `set_updated_at` aplicado a todas las tablas que
-- llevan `updated_at`.
--
-- Decisiones:
--   * Función única SECURITY DEFINER no es necesaria: corre con los
--     privilegios del usuario que dispara el UPDATE. Marcamos VOLATILE.
--   * `recaudacion` también lleva updated_at por consistencia, aunque la
--     fila sea casi inmutable (solo se modifica al anular/resolver).
-- =============================================================================

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION public.set_updated_at() IS
    'Trigger BEFORE UPDATE que actualiza la columna updated_at a now().';

DO $$
DECLARE
    t text;
    tables text[] := ARRAY[
        'empresa',
        'usuario',
        'empresa_usuario',
        'licencia',
        'maquina',
        'local',
        'instalacion',
        'cambio_placa',
        'recaudacion'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format(
            'DROP TRIGGER IF EXISTS trg_%1$s_updated_at ON public.%1$s;',
            t
        );
        EXECUTE format(
            'CREATE TRIGGER trg_%1$s_updated_at
                BEFORE UPDATE ON public.%1$s
                FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();',
            t
        );
    END LOOP;
END;
$$;
