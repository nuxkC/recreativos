-- =============================================================================
-- T-17 — Buckets de Storage y políticas RLS.
--
-- Cinco buckets privados (sin acceso público). El acceso siempre se hace
-- vía signed URL generada server-side (Edge Function) o desde la app con
-- el token del usuario autenticado y la siguiente convención de path:
--
--     <bucket>/<empresa_id>/<resto>
--
-- Las políticas comprueban que el primer segmento del path coincide con
-- una empresa a la que pertenece el usuario.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Buckets
-- -----------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES
    ('firmas',           'firmas',           false,  5242880, ARRAY['image/png', 'image/jpeg']),
    ('fotos-contadores', 'fotos-contadores', false, 10485760, ARRAY['image/jpeg', 'image/png']),
    ('tickets',          'tickets',          false, 10485760, ARRAY['application/pdf']),
    ('logos',            'logos',            false,  2097152, ARRAY['image/png', 'image/jpeg', 'image/svg+xml']),
    ('cambios-placa',    'cambios-placa',    false, 10485760, ARRAY['image/jpeg', 'image/png'])
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Políticas sobre storage.objects
-- -----------------------------------------------------------------------------
-- SELECT: cualquier miembro de la empresa que sea dueña del path.
CREATE POLICY "recre_storage_select_own_tenant" ON storage.objects
    FOR SELECT
    TO authenticated
    USING (
        bucket_id IN ('firmas', 'fotos-contadores', 'tickets', 'logos', 'cambios-placa')
        AND public.usuario_pertenece_a_empresa(((storage.foldername(name))[1])::uuid)
    );

-- INSERT:
--   * `tickets` y `logos` se generan/suben server-side con service_role,
--     no permitimos insert desde authenticated.
--   * `firmas`, `fotos-contadores`, `cambios-placa`: cualquier rol
--     operativo (técnico+) puede subir si el path es de su empresa.
CREATE POLICY "recre_storage_insert_operativo" ON storage.objects
    FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id IN ('firmas', 'fotos-contadores', 'cambios-placa')
        AND public.usuario_es_operativo(((storage.foldername(name))[1])::uuid)
    );

-- UPDATE: solo admin (correcciones puntuales). El service_role siempre puede.
CREATE POLICY "recre_storage_update_admin" ON storage.objects
    FOR UPDATE
    TO authenticated
    USING (
        bucket_id IN ('firmas', 'fotos-contadores', 'tickets', 'logos', 'cambios-placa')
        AND public.usuario_es_admin(((storage.foldername(name))[1])::uuid)
    )
    WITH CHECK (
        bucket_id IN ('firmas', 'fotos-contadores', 'tickets', 'logos', 'cambios-placa')
        AND public.usuario_es_admin(((storage.foldername(name))[1])::uuid)
    );

-- DELETE: solo admin de la empresa dueña del path.
CREATE POLICY "recre_storage_delete_admin" ON storage.objects
    FOR DELETE
    TO authenticated
    USING (
        bucket_id IN ('firmas', 'fotos-contadores', 'tickets', 'logos', 'cambios-placa')
        AND public.usuario_es_admin(((storage.foldername(name))[1])::uuid)
    );
