-- =============================================================================
-- T-101 — Tabla `device_token` para notificaciones push (FCM).
--
-- Guarda el token de registro FCM de cada dispositivo asociado a un usuario
-- dentro de una empresa. Lo usa `enviar-push` (server-side, service_role)
-- para resolver a qué dispositivos enviar una notificación (p. ej. la
-- resolución de un conflicto al técnico que la subió).
--
-- Decisiones (ver .kiro/specs/recre/design.md §8 y conventions §Seguridad):
--   * `token` es UNIQUE global: FCM emite un token único por instalación de
--     app y dispositivo; si reaparece en otra empresa/usuario, el upsert
--     reasigna su propietario (ON CONFLICT (token)).
--   * `plataforma` como TEXT + CHECK por flexibilidad futura (ios/web) sin
--     ALTER TYPE.
--   * RLS: un usuario solo gestiona SUS PROPIOS tokens y solo dentro de una
--     empresa a la que pertenece. El envío real lo hace service_role, que
--     puentea RLS.
--   * Migración aditiva: no toca tablas existentes.
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.device_token (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id  uuid        NOT NULL REFERENCES public.empresa(id) ON DELETE CASCADE,
    usuario_id  uuid        NOT NULL REFERENCES public.usuario(id) ON DELETE CASCADE,
    token       text        NOT NULL,
    plataforma  text        NOT NULL DEFAULT 'android'
                            CHECK (plataforma IN ('android', 'ios', 'web')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT device_token_token_key UNIQUE (token)
);

COMMENT ON TABLE public.device_token IS
    'Tokens de registro push (FCM) por dispositivo/usuario/empresa. Consumido por enviar-push (service_role).';

COMMENT ON COLUMN public.device_token.token IS
    'Token de registro FCM. Único: un token solo pertenece a un (usuario, empresa) a la vez.';

-- Resolver rápidamente "tokens de este técnico en esta empresa" al notificar.
CREATE INDEX IF NOT EXISTS idx_device_token_empresa_usuario
    ON public.device_token (empresa_id, usuario_id);

-- Trigger updated_at (reutiliza la función genérica de T-14a).
DROP TRIGGER IF EXISTS trg_device_token_updated_at ON public.device_token;
CREATE TRIGGER trg_device_token_updated_at
    BEFORE UPDATE ON public.device_token
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- -----------------------------------------------------------------------------
-- RLS: cada usuario gestiona solo sus propios tokens, dentro de su empresa.
-- -----------------------------------------------------------------------------
ALTER TABLE public.device_token ENABLE ROW LEVEL SECURITY;

CREATE POLICY device_token_select ON public.device_token
    FOR SELECT
    USING (
        usuario_id = auth.uid()
        AND public.usuario_pertenece_a_empresa(empresa_id)
    );

CREATE POLICY device_token_insert ON public.device_token
    FOR INSERT
    WITH CHECK (
        usuario_id = auth.uid()
        AND public.usuario_pertenece_a_empresa(empresa_id)
    );

CREATE POLICY device_token_update ON public.device_token
    FOR UPDATE
    USING (
        usuario_id = auth.uid()
        AND public.usuario_pertenece_a_empresa(empresa_id)
    )
    WITH CHECK (
        usuario_id = auth.uid()
        AND public.usuario_pertenece_a_empresa(empresa_id)
    );

CREATE POLICY device_token_delete ON public.device_token
    FOR DELETE
    USING (usuario_id = auth.uid());
