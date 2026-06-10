-- =============================================================================
-- T-203 — Boletines digitales de instalación.
--
-- Un "boletín de instalación" es el documento digital (PDF) que certifica la
-- instalación de una máquina recreativa en un local. Se genera por instalación
-- (alta) y se archiva en un bucket privado dedicado.
--
-- Decisiones (ver .kiro/specs/recre/design.md y conventions.md):
--   * Bucket NUEVO `boletines` (privado, solo PDF) en lugar de reutilizar
--     `tickets`: separa el ciclo de vida (1 boletín por instalación vs N
--     tickets por recaudación) y permite políticas/limpieza independientes.
--   * Referencia persistida como COLUMNAS en `instalacion` (`boletin_url`,
--     `boletin_generado_at`) en lugar de tabla histórica: el boletín es 1:1
--     con la instalación y determinista a partir de sus datos, igual que el
--     patrón `recaudacion.pdf_url`. La regeneración sobrescribe el objeto
--     (upsert) y refresca `boletin_generado_at`.
--   * Convención de path: `boletines/<empresa_id>/<instalacion_id>.pdf`.
--   * Migración ADITIVA: no se editan migraciones ya aplicadas.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Columnas de referencia del boletín en `instalacion`
-- -----------------------------------------------------------------------------
ALTER TABLE public.instalacion
    ADD COLUMN IF NOT EXISTS boletin_url         text,
    ADD COLUMN IF NOT EXISTS boletin_generado_at timestamptz;

COMMENT ON COLUMN public.instalacion.boletin_url IS
    'Path del boletín de instalación (PDF) en el bucket privado `boletines`. NULL si aún no se ha generado.';

COMMENT ON COLUMN public.instalacion.boletin_generado_at IS
    'Marca de tiempo de la última generación del boletín de instalación.';

-- -----------------------------------------------------------------------------
-- Bucket privado `boletines`
-- -----------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES
    ('boletines', 'boletines', false, 10485760, ARRAY['application/pdf'])
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Políticas sobre storage.objects para el bucket `boletines`
--
-- Replican el patrón de T-17 (`..._create_storage_buckets.sql`) acotadas a
-- este bucket. No se modifican las políticas existentes (no acumulables sin
-- recrearlas): se añaden políticas independientes, que en RLS se evalúan en OR
-- con las anteriores pero limitadas por `bucket_id = 'boletines'`.
-- -----------------------------------------------------------------------------

-- SELECT: cualquier miembro de la empresa dueña del path puede descargar.
CREATE POLICY "recre_storage_select_boletines" ON storage.objects
    FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'boletines'
        AND public.usuario_pertenece_a_empresa(((storage.foldername(name))[1])::uuid)
    );

-- INSERT: el boletín se genera/sube server-side con service_role (igual que
-- `tickets`). No se permite insert desde `authenticated`.

-- UPDATE: solo admin de la empresa dueña (correcciones puntuales).
-- El service_role siempre puede (bypassa RLS).
CREATE POLICY "recre_storage_update_boletines_admin" ON storage.objects
    FOR UPDATE
    TO authenticated
    USING (
        bucket_id = 'boletines'
        AND public.usuario_es_admin(((storage.foldername(name))[1])::uuid)
    )
    WITH CHECK (
        bucket_id = 'boletines'
        AND public.usuario_es_admin(((storage.foldername(name))[1])::uuid)
    );

-- DELETE: solo admin de la empresa dueña del path.
CREATE POLICY "recre_storage_delete_boletines_admin" ON storage.objects
    FOR DELETE
    TO authenticated
    USING (
        bucket_id = 'boletines'
        AND public.usuario_es_admin(((storage.foldername(name))[1])::uuid)
    );
