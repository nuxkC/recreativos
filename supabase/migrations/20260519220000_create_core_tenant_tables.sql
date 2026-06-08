-- =============================================================================
-- T-10 — Tablas core multi-tenant: empresa, usuario, empresa_usuario.
--
-- Decisiones (ver .kiro/specs/recre/design.md §3.1-3.3 y §4):
--   * RLS NO se activa aquí. Se activa junto con las políticas en T-15
--     (`enable_rls_and_policies`). Hasta entonces solo el service_role
--     accede a estos datos en local.
--   * Roles modelados como TEXT + CHECK para poder añadir nuevos sin
--     ALTER TYPE (más flexible que un ENUM en migraciones futuras).
--   * `usuario.id` referencia `auth.users(id)`: 1:1 con la tabla de auth
--     gestionada por Supabase. Si Supabase borra el usuario, se borra el
--     perfil (CASCADE).
--   * `empresa_usuario.activo` permite revocar acceso sin perder histórico.
-- =============================================================================

-- Extensiones requeridas para gen_random_uuid().
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------------
-- empresa
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.empresa (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre          text        NOT NULL,
    cif             text,
    direccion       text,
    telefono        text,
    email           text,
    logo_url        text,
    zona_horaria    text        NOT NULL DEFAULT 'Europe/Madrid',
    ticket_cabecera text,
    ticket_pie      text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.empresa IS
    'Empresas explotadoras de máquinas recreativas. Cada empresa es un tenant aislado por RLS.';

COMMENT ON COLUMN public.empresa.zona_horaria IS
    'IANA tz usada para convertir timestamps al calcular semanas ISO. Default Europe/Madrid.';

-- -----------------------------------------------------------------------------
-- usuario (perfil que extiende auth.users)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.usuario (
    id               uuid        PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    nombre_completo  text        NOT NULL,
    telefono         text,
    avatar_url       text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.usuario IS
    'Perfil de usuario asociado 1:1 a auth.users. El email vive en auth.users.';

-- -----------------------------------------------------------------------------
-- empresa_usuario (membresías N:M con rol)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.empresa_usuario (
    empresa_id  uuid        NOT NULL REFERENCES public.empresa(id) ON DELETE CASCADE,
    usuario_id  uuid        NOT NULL REFERENCES public.usuario(id) ON DELETE CASCADE,
    rol         text        NOT NULL CHECK (rol IN ('owner', 'admin', 'gestor', 'tecnico', 'contable')),
    activo      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (empresa_id, usuario_id)
);

COMMENT ON TABLE public.empresa_usuario IS
    'Relación N:M usuario-empresa con rol. Un usuario puede pertenecer a varias empresas con roles distintos.';

-- Índice para resolver rápidamente "empresas activas de un usuario", usado por
-- las políticas RLS y por el selector de empresa en la app.
CREATE INDEX IF NOT EXISTS idx_empresa_usuario_usuario_activo
    ON public.empresa_usuario (usuario_id)
    WHERE activo = true;
