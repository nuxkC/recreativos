-- =============================================================================
-- T-13a — Función `semanas_iso_entre`.
--
-- Cuenta las semanas ISO de calendario distintas entre dos timestamps,
-- excluyendo la semana de referencia e incluyendo la actual. Es la regla
-- definida en `.kiro/specs/recre/design.md §5.2` para aplicar la tasa.
--
-- Implementación:
--   1. Convertimos ambos a hora local (`AT TIME ZONE`).
--   2. Truncamos al inicio de la semana ISO (lunes) con date_trunc('week', ...).
--   3. Restamos los lunes y dividimos por 7 → número de semanas distintas.
--
-- Esta forma evita complicaciones con cambios de año ISO porque
-- `date_trunc('week', ...)` ya gestiona la semana ISO correctamente.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.semanas_iso_entre(
    p_desde timestamptz,
    p_hasta timestamptz,
    p_tz    text DEFAULT 'Europe/Madrid'
) RETURNS integer
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT GREATEST(
        0,
        (
            (date_trunc('week', (p_hasta AT TIME ZONE p_tz)))::date
            - (date_trunc('week', (p_desde AT TIME ZONE p_tz)))::date
        ) / 7
    );
$$;

COMMENT ON FUNCTION public.semanas_iso_entre(timestamptz, timestamptz, text) IS
    'Número de semanas ISO de calendario entre dos timestamps (excluye la semana de referencia, incluye la actual).';
