-- =============================================================================
-- T-202 — Auditoría completa con tabla de eventos `audit_log`.
--
-- Registra eventos del dominio (recaudación creada/anulada, conflicto
-- detectado/resuelto, cambio de placa, alta/cierre de instalación,
-- invitaciones y cambios de rol) para trazabilidad multi-tenant.
--
-- Enfoque de poblado (ver design.md §Logging y conventions §Logging):
--   * TRIGGERS AFTER en las tablas cuyo cambio de estado es la verdad del
--     evento (`recaudacion`, `cambio_placa`, `instalacion`). Capturan el
--     evento venga de una Edge Function o de un CRUD directo (web/android),
--     y resuelven el actor con `auth.uid()` (NULL para acciones de
--     service_role / sistema).
--   * HELPER `_shared/audit.ts` (service_role) para acciones que SOLO conoce
--     la Edge Function y no se deducen de un único cambio de fila:
--     `usuario_invitado`, `rol_cambiado` (las dispara `invitar-usuario`).
--   No se duplica lógica: triggers y helper cubren conjuntos DISJUNTOS de
--   eventos.
--
-- Decisiones de robustez (críticas):
--   * `registrar_auditoria` es SECURITY DEFINER y CAPTURA cualquier error
--     (EXCEPTION WHEN OTHERS) para que la auditoría sea best-effort y NUNCA
--     bloquee ni revierta la operación de negocio (HU-10: "el servidor
--     siempre acepta la recaudación").
--   * `actor_usuario_id` NO lleva FK a `usuario`: así un actor sin perfil
--     o un usuario borrado no provoca violación de FK (que se tragaría el
--     EXCEPTION perdiendo el evento). El histórico conserva el uuid.
--   * `datos` jsonb guarda SOLO contexto NO sensible (ids de negocio,
--     importes agregados, flags). NUNCA emails, firmas, observaciones,
--     desgloses ni datos del titular del local (conventions §Logging:
--     "logging sin PII").
--
-- Migración aditiva: no edita migraciones aplicadas ni toca otras tablas.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Tabla audit_log
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.audit_log (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id       uuid        NOT NULL REFERENCES public.empresa(id) ON DELETE CASCADE,
    -- Sin FK a usuario a propósito (ver cabecera). NULL = acción de sistema.
    actor_usuario_id uuid,
    accion           text        NOT NULL CHECK (accion IN (
        'recaudacion_creada',
        'recaudacion_anulada',
        'conflicto_detectado',
        'conflicto_resuelto',
        'cambio_placa_creado',
        'instalacion_creada',
        'instalacion_cerrada',
        'usuario_invitado',
        'rol_cambiado'
    )),
    entidad_tabla    text        NOT NULL,
    entidad_id       uuid,
    datos            jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_log_datos_objeto
        CHECK (jsonb_typeof(datos) = 'object')
);

COMMENT ON TABLE public.audit_log IS
    'Bitácora de eventos de dominio por empresa (T-202). Inmutable: solo INSERT. datos jsonb sin PII.';

COMMENT ON COLUMN public.audit_log.actor_usuario_id IS
    'auth.uid() que originó el evento. NULL para acciones de sistema (service_role). Sin FK a propósito.';

COMMENT ON COLUMN public.audit_log.datos IS
    'Contexto NO sensible del evento (ids de negocio, importes, flags). NUNCA PII (emails, firmas, titular).';

-- Filtro habitual: eventos de una empresa por fecha (panel de auditoría).
CREATE INDEX IF NOT EXISTS idx_audit_log_empresa_created
    ON public.audit_log (empresa_id, created_at DESC);

-- Filtro por entidad concreta (histórico de una recaudación/instalación...).
CREATE INDEX IF NOT EXISTS idx_audit_log_entidad
    ON public.audit_log (entidad_tabla, entidad_id);

-- Filtro por acción (combinado con empresa) para la vista web con filtros.
CREATE INDEX IF NOT EXISTS idx_audit_log_empresa_accion
    ON public.audit_log (empresa_id, accion);

-- -----------------------------------------------------------------------------
-- registrar_auditoria: punto de inserción único, best-effort.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.registrar_auditoria(
    p_empresa_id    uuid,
    p_accion        text,
    p_entidad_tabla text,
    p_entidad_id    uuid,
    p_datos         jsonb DEFAULT '{}'::jsonb
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
BEGIN
    INSERT INTO public.audit_log (
        empresa_id, actor_usuario_id, accion, entidad_tabla, entidad_id, datos
    ) VALUES (
        p_empresa_id,
        auth.uid(),
        p_accion,
        p_entidad_tabla,
        p_entidad_id,
        COALESCE(p_datos, '{}'::jsonb)
    );
EXCEPTION WHEN OTHERS THEN
    -- La auditoría JAMÁS debe revertir ni bloquear la operación de negocio.
    RAISE WARNING 'registrar_auditoria(%) falló: %', p_accion, SQLERRM;
END;
$$;

COMMENT ON FUNCTION public.registrar_auditoria(uuid, text, text, uuid, jsonb) IS
    'Inserta un evento en audit_log resolviendo el actor con auth.uid(). Best-effort: traga errores.';

-- -----------------------------------------------------------------------------
-- Trigger: recaudacion (creada / anulada / conflicto detectado / resuelto)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.trg_audit_recaudacion()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM public.registrar_auditoria(
            NEW.empresa_id,
            'recaudacion_creada',
            'recaudacion',
            NEW.id,
            jsonb_build_object(
                'instalacion_id', NEW.instalacion_id,
                'recaudacion_neta', NEW.recaudacion_neta,
                'estado', NEW.estado,
                'conflicto', NEW.conflicto
            )
        );
        IF NEW.conflicto THEN
            PERFORM public.registrar_auditoria(
                NEW.empresa_id,
                'conflicto_detectado',
                'recaudacion',
                NEW.id,
                jsonb_build_object('instalacion_id', NEW.instalacion_id)
            );
        END IF;
    ELSIF TG_OP = 'UPDATE' THEN
        IF NEW.estado = 'anulada' AND OLD.estado IS DISTINCT FROM 'anulada' THEN
            PERFORM public.registrar_auditoria(
                NEW.empresa_id,
                'recaudacion_anulada',
                'recaudacion',
                NEW.id,
                jsonb_build_object('instalacion_id', NEW.instalacion_id)
            );
        END IF;
        IF NEW.revisado_en IS NOT NULL AND OLD.revisado_en IS NULL THEN
            PERFORM public.registrar_auditoria(
                NEW.empresa_id,
                'conflicto_resuelto',
                'recaudacion',
                NEW.id,
                jsonb_build_object(
                    'instalacion_id', NEW.instalacion_id,
                    'resolucion', NEW.resolucion
                )
            );
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_recaudacion ON public.recaudacion;
CREATE TRIGGER trg_audit_recaudacion
    AFTER INSERT OR UPDATE ON public.recaudacion
    FOR EACH ROW EXECUTE FUNCTION public.trg_audit_recaudacion();

-- -----------------------------------------------------------------------------
-- Trigger: cambio_placa (creado)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.trg_audit_cambio_placa()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
BEGIN
    PERFORM public.registrar_auditoria(
        NEW.empresa_id,
        'cambio_placa_creado',
        'cambio_placa',
        NEW.id,
        jsonb_build_object(
            'instalacion_id', NEW.instalacion_id,
            'contador_entradas_nuevo', NEW.contador_entradas_nuevo,
            'contador_salidas_nuevo', NEW.contador_salidas_nuevo
        )
    );
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_cambio_placa ON public.cambio_placa;
CREATE TRIGGER trg_audit_cambio_placa
    AFTER INSERT ON public.cambio_placa
    FOR EACH ROW EXECUTE FUNCTION public.trg_audit_cambio_placa();

-- -----------------------------------------------------------------------------
-- Trigger: instalacion (creada / cerrada)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.trg_audit_instalacion()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM public.registrar_auditoria(
            NEW.empresa_id,
            'instalacion_creada',
            'instalacion',
            NEW.id,
            jsonb_build_object(
                'maquina_id', NEW.maquina_id,
                'licencia_id', NEW.licencia_id,
                'local_id', NEW.local_id,
                'tasa_semanal', NEW.tasa_semanal,
                'porcentaje_local', NEW.porcentaje_local
            )
        );
    ELSIF TG_OP = 'UPDATE' THEN
        IF NEW.estado = 'cerrada' AND OLD.estado IS DISTINCT FROM 'cerrada' THEN
            PERFORM public.registrar_auditoria(
                NEW.empresa_id,
                'instalacion_cerrada',
                'instalacion',
                NEW.id,
                jsonb_build_object(
                    'maquina_id', NEW.maquina_id,
                    'licencia_id', NEW.licencia_id,
                    'local_id', NEW.local_id,
                    'fecha_fin', NEW.fecha_fin
                )
            );
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_instalacion ON public.instalacion;
CREATE TRIGGER trg_audit_instalacion
    AFTER INSERT OR UPDATE ON public.instalacion
    FOR EACH ROW EXECUTE FUNCTION public.trg_audit_instalacion();

-- -----------------------------------------------------------------------------
-- RLS: lectura restringida a owner/admin de la empresa dueña.
--   * SELECT: solo gestión (owner/admin) — la bitácora es información
--     sensible de control interno.
--   * INSERT/UPDATE/DELETE: sin policy => RLS bloquea a TODO cliente.
--     Solo escriben: los triggers (SECURITY DEFINER) y el helper de las
--     Edge Functions (service_role). El log es inmutable: no se actualiza
--     ni se borra (salvo CASCADE al borrar la empresa).
-- -----------------------------------------------------------------------------
ALTER TABLE public.audit_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY audit_log_select ON public.audit_log
    FOR SELECT
    USING (public.usuario_es_admin(empresa_id));
