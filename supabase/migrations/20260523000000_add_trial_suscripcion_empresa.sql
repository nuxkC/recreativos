-- =============================================================================
-- T-200 — Onboarding self-service: estado de suscripción y trial por empresa.
--
-- Decisiones (ver .kiro/specs/recre/design.md §13 y conventions §Seguridad):
--
--   * DÓNDE viven los campos de trial → directamente en `empresa`.
--     Es la opción más simple y suficiente para el alcance de T-200 (solo
--     estado informativo del trial, sin facturación). Cuando T-201
--     (facturación/planes) entre, ahí sí conviene una tabla aparte
--     `empresa_suscripcion` con histórico de periodos y plan; este diseño
--     no la bloquea (se podrá migrar el estado actual a la nueva tabla).
--
--   * `estado_suscripcion` como TEXT + CHECK (no ENUM) por consistencia con
--     el resto del esquema (roles, estados) y para añadir estados futuros
--     sin ALTER TYPE.
--
--   * Backfill de empresas YA EXISTENTES como 'activa' con trial NULL: las
--     empresas creadas antes del self-service no son cuentas de prueba, así
--     que no deben mostrarse como trial ni "expirar". El DEFAULT 'trial' solo
--     aplica a empresas nuevas (las que se crean por registro self-service).
--
--   * Las columnas de suscripción NO deben poder modificarse desde el cliente
--     (un admin no debería poder auto-prorrogarse el trial). RLS no permite
--     restringir columnas concretas, así que añadimos un trigger BEFORE UPDATE
--     que bloquea cambios en estas columnas salvo que el caller sea
--     service_role (Edge Functions / jobs). La gestión real del ciclo de vida
--     de la suscripción es server-side (T-201).
--
--   * Migración aditiva: no rompe datos existentes ni toca otras tablas.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Columnas nuevas (nullable primero para poder backfillear).
-- -----------------------------------------------------------------------------
ALTER TABLE public.empresa
    ADD COLUMN IF NOT EXISTS estado_suscripcion text,
    ADD COLUMN IF NOT EXISTS trial_inicio        timestamptz,
    ADD COLUMN IF NOT EXISTS trial_fin           timestamptz;

-- -----------------------------------------------------------------------------
-- 2. Backfill: empresas existentes pasan a 'activa' (no son cuentas de prueba).
-- -----------------------------------------------------------------------------
UPDATE public.empresa
   SET estado_suscripcion = 'activa'
 WHERE estado_suscripcion IS NULL;

-- -----------------------------------------------------------------------------
-- 3. Default para empresas NUEVAS (registro self-service) + NOT NULL + CHECK.
-- -----------------------------------------------------------------------------
ALTER TABLE public.empresa
    ALTER COLUMN estado_suscripcion SET DEFAULT 'trial';

ALTER TABLE public.empresa
    ALTER COLUMN estado_suscripcion SET NOT NULL;

ALTER TABLE public.empresa
    ADD CONSTRAINT empresa_estado_suscripcion_check
    CHECK (estado_suscripcion IN ('trial', 'activa', 'suspendida', 'cancelada'));

-- Coherencia: si está en trial, debe tener ventana de trial definida.
ALTER TABLE public.empresa
    ADD CONSTRAINT empresa_trial_ventana_check
    CHECK (
        estado_suscripcion <> 'trial'
        OR (trial_inicio IS NOT NULL AND trial_fin IS NOT NULL AND trial_fin > trial_inicio)
    );

COMMENT ON COLUMN public.empresa.estado_suscripcion IS
    'Estado de la suscripción: trial | activa | suspendida | cancelada. Las empresas creadas por registro self-service nacen en trial; la gestión del ciclo de vida es server-side (T-201).';
COMMENT ON COLUMN public.empresa.trial_inicio IS
    'Inicio del periodo de prueba (NULL si la empresa nunca estuvo en trial).';
COMMENT ON COLUMN public.empresa.trial_fin IS
    'Fin del periodo de prueba. Solo informativo en T-200; el bloqueo por expiración llegará con T-201.';

-- Índice para localizar trials próximos a expirar (futuros recordatorios/cron).
CREATE INDEX IF NOT EXISTS idx_empresa_trial_fin
    ON public.empresa (trial_fin)
    WHERE estado_suscripcion = 'trial';

-- -----------------------------------------------------------------------------
-- 4. Trigger: proteger las columnas de suscripción de modificaciones del cliente.
--    Solo service_role (Edge Functions / jobs) puede cambiarlas. Las
--    operaciones normales (p. ej. el formulario de Ajustes) no tocan estas
--    columnas, así que OLD = NEW y el trigger no interfiere.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.proteger_suscripcion_empresa()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_role text := coalesce(auth.jwt() ->> 'role', 'service_role');
BEGIN
    IF v_role <> 'service_role' AND (
        NEW.estado_suscripcion IS DISTINCT FROM OLD.estado_suscripcion
        OR NEW.trial_inicio    IS DISTINCT FROM OLD.trial_inicio
        OR NEW.trial_fin       IS DISTINCT FROM OLD.trial_fin
    ) THEN
        RAISE EXCEPTION 'Los campos de suscripción solo se modifican server-side'
            USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION public.proteger_suscripcion_empresa() IS
    'BEFORE UPDATE en empresa: impide que un cliente (no service_role) altere estado_suscripcion/trial_*.';

DROP TRIGGER IF EXISTS trg_empresa_proteger_suscripcion ON public.empresa;
CREATE TRIGGER trg_empresa_proteger_suscripcion
    BEFORE UPDATE ON public.empresa
    FOR EACH ROW EXECUTE FUNCTION public.proteger_suscripcion_empresa();
