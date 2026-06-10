-- =============================================================================
-- T-15b — Activación de RLS y políticas por tabla y rol.
--
-- Modelo de acceso (ver design.md §4 y §11):
--
--   | Tabla                | SELECT                 | INSERT/UPDATE/DELETE          |
--   |----------------------|------------------------|-------------------------------|
--   | empresa              | miembros de la empresa | UPDATE: owner+admin           |
--   | usuario              | propio + miembros      | UPDATE: propio                |
--   | empresa_usuario      | miembros               | owner+admin                   |
--   | licencia, maquina,   | miembros               | owner+admin+gestor            |
--   | local, instalacion   |                        |                               |
--   | cambio_placa         | miembros               | INSERT: tecnico+; UPDATE/DEL: admin |
--   | recaudacion          | miembros               | INSERT: tecnico+; UPDATE: admin; DEL: nadie |
--   | recaudacion_lock     | miembros               | INSERT/UPDATE/DEL: tecnico+ (propio)        |
--   | lectura_no_recaudada | miembros               | INSERT: tecnico+; UPDATE/DEL: admin |
--   | alerta               | miembros               | UPDATE (mark read): miembros; DELETE: admin |
--
-- service_role siempre puentea RLS (Supabase). Las Edge Functions que
-- requieran saltarse RLS lo harán con la service_role key, NUNCA expuesta
-- al cliente.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Activar RLS en todas las tablas
-- -----------------------------------------------------------------------------
ALTER TABLE public.empresa              ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.usuario              ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.empresa_usuario      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.licencia             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.maquina              ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.local                ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.instalacion          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cambio_placa         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recaudacion          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recaudacion_lock     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lectura_no_recaudada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.alerta               ENABLE ROW LEVEL SECURITY;

-- -----------------------------------------------------------------------------
-- empresa
-- -----------------------------------------------------------------------------
CREATE POLICY empresa_select ON public.empresa
    FOR SELECT
    USING (public.usuario_pertenece_a_empresa(id));

CREATE POLICY empresa_update ON public.empresa
    FOR UPDATE
    USING (public.usuario_es_admin(id))
    WITH CHECK (public.usuario_es_admin(id));

-- INSERT y DELETE de empresa solo por service_role (alta/baja vía Edge Function).

-- -----------------------------------------------------------------------------
-- usuario (perfil)
-- -----------------------------------------------------------------------------
CREATE POLICY usuario_select_self ON public.usuario
    FOR SELECT
    USING (id = auth.uid());

-- Permitimos ver perfiles de compañeros de empresa (para mostrar nombres en
-- listados de equipo, recaudaciones, etc.).
CREATE POLICY usuario_select_compañeros ON public.usuario
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1
              FROM public.empresa_usuario eu1
              JOIN public.empresa_usuario eu2 ON eu1.empresa_id = eu2.empresa_id
             WHERE eu1.usuario_id = auth.uid()
               AND eu2.usuario_id = public.usuario.id
               AND eu1.activo AND eu2.activo
        )
    );

CREATE POLICY usuario_insert_self ON public.usuario
    FOR INSERT
    WITH CHECK (id = auth.uid());

CREATE POLICY usuario_update_self ON public.usuario
    FOR UPDATE
    USING (id = auth.uid())
    WITH CHECK (id = auth.uid());

-- -----------------------------------------------------------------------------
-- empresa_usuario (membresías)
-- -----------------------------------------------------------------------------
CREATE POLICY empresa_usuario_select ON public.empresa_usuario
    FOR SELECT
    USING (public.usuario_pertenece_a_empresa(empresa_id));

CREATE POLICY empresa_usuario_modify ON public.empresa_usuario
    FOR ALL
    USING (public.usuario_es_admin(empresa_id))
    WITH CHECK (public.usuario_es_admin(empresa_id));

-- -----------------------------------------------------------------------------
-- Inventario (licencia, maquina, local, instalacion)
-- -----------------------------------------------------------------------------
CREATE POLICY licencia_select ON public.licencia
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));
CREATE POLICY licencia_modify ON public.licencia
    FOR ALL
    USING (public.usuario_es_gestor(empresa_id))
    WITH CHECK (public.usuario_es_gestor(empresa_id));

CREATE POLICY maquina_select ON public.maquina
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));
CREATE POLICY maquina_modify ON public.maquina
    FOR ALL
    USING (public.usuario_es_gestor(empresa_id))
    WITH CHECK (public.usuario_es_gestor(empresa_id));

CREATE POLICY local_select ON public.local
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));
CREATE POLICY local_modify ON public.local
    FOR ALL
    USING (public.usuario_es_gestor(empresa_id))
    WITH CHECK (public.usuario_es_gestor(empresa_id));

CREATE POLICY instalacion_select ON public.instalacion
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));
CREATE POLICY instalacion_modify ON public.instalacion
    FOR ALL
    USING (public.usuario_es_gestor(empresa_id))
    WITH CHECK (public.usuario_es_gestor(empresa_id));

-- -----------------------------------------------------------------------------
-- cambio_placa
-- -----------------------------------------------------------------------------
CREATE POLICY cambio_placa_select ON public.cambio_placa
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));

CREATE POLICY cambio_placa_insert ON public.cambio_placa
    FOR INSERT
    WITH CHECK (public.usuario_es_operativo(empresa_id));

CREATE POLICY cambio_placa_admin_modify ON public.cambio_placa
    FOR UPDATE
    USING (public.usuario_es_admin(empresa_id))
    WITH CHECK (public.usuario_es_admin(empresa_id));

CREATE POLICY cambio_placa_admin_delete ON public.cambio_placa
    FOR DELETE
    USING (public.usuario_es_admin(empresa_id));

-- -----------------------------------------------------------------------------
-- recaudacion (inmutable; solo admin puede UPDATE para anular/resolver)
-- -----------------------------------------------------------------------------
CREATE POLICY recaudacion_select ON public.recaudacion
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));

CREATE POLICY recaudacion_insert ON public.recaudacion
    FOR INSERT
    WITH CHECK (
        public.usuario_es_operativo(empresa_id)
        AND tecnico_id = auth.uid()
    );

CREATE POLICY recaudacion_admin_update ON public.recaudacion
    FOR UPDATE
    USING (public.usuario_es_admin(empresa_id))
    WITH CHECK (public.usuario_es_admin(empresa_id));

-- DELETE: nadie. No se borran recaudaciones (solo se anulan vía UPDATE).

-- -----------------------------------------------------------------------------
-- recaudacion_lock (lock optimista; cada técnico maneja el suyo)
-- -----------------------------------------------------------------------------
CREATE POLICY recaudacion_lock_select ON public.recaudacion_lock
    FOR SELECT
    USING (
        public.usuario_pertenece_a_empresa(
            (SELECT empresa_id FROM public.instalacion WHERE id = instalacion_id)
        )
    );

CREATE POLICY recaudacion_lock_insert ON public.recaudacion_lock
    FOR INSERT
    WITH CHECK (
        tecnico_id = auth.uid()
        AND public.usuario_es_operativo(
            (SELECT empresa_id FROM public.instalacion WHERE id = instalacion_id)
        )
    );

CREATE POLICY recaudacion_lock_update ON public.recaudacion_lock
    FOR UPDATE
    USING (
        tecnico_id = auth.uid()
        AND public.usuario_es_operativo(
            (SELECT empresa_id FROM public.instalacion WHERE id = instalacion_id)
        )
    )
    WITH CHECK (
        tecnico_id = auth.uid()
        AND public.usuario_es_operativo(
            (SELECT empresa_id FROM public.instalacion WHERE id = instalacion_id)
        )
    );

CREATE POLICY recaudacion_lock_delete ON public.recaudacion_lock
    FOR DELETE
    USING (
        tecnico_id = auth.uid()
        OR public.usuario_es_admin(
            (SELECT empresa_id FROM public.instalacion WHERE id = instalacion_id)
        )
    );

-- -----------------------------------------------------------------------------
-- lectura_no_recaudada
-- -----------------------------------------------------------------------------
CREATE POLICY lectura_no_recaudada_select ON public.lectura_no_recaudada
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));

CREATE POLICY lectura_no_recaudada_insert ON public.lectura_no_recaudada
    FOR INSERT
    WITH CHECK (
        public.usuario_es_operativo(empresa_id)
        AND tecnico_id = auth.uid()
    );

CREATE POLICY lectura_no_recaudada_admin_modify ON public.lectura_no_recaudada
    FOR ALL
    USING (public.usuario_es_admin(empresa_id))
    WITH CHECK (public.usuario_es_admin(empresa_id));

-- -----------------------------------------------------------------------------
-- alerta
-- -----------------------------------------------------------------------------
CREATE POLICY alerta_select ON public.alerta
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));

-- Marcar como leída: cualquier miembro puede actualizar las alertas que ve.
CREATE POLICY alerta_update ON public.alerta
    FOR UPDATE
    USING (public.usuario_pertenece_a_empresa(empresa_id))
    WITH CHECK (public.usuario_pertenece_a_empresa(empresa_id));

CREATE POLICY alerta_admin_delete ON public.alerta
    FOR DELETE
    USING (public.usuario_es_admin(empresa_id));

-- INSERT de alertas siempre lo hace una Edge Function con service_role,
-- por lo que no se necesita policy de INSERT (RLS bloquea por defecto).
