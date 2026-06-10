-- =============================================================================
-- Correcciones de seguridad y lógica (auditoría de BBDD/funciones).
--
-- Migración ADITIVA que rectifica defectos detectados en revisión, sin editar
-- migraciones ya aplicadas:
--
--   1. RLS cross-tenant: las políticas de INSERT de recaudacion,
--      lectura_no_recaudada y cambio_placa solo comprobaban el rol en
--      `empresa_id` del propio payload, sin atar `instalacion_id` a esa misma
--      empresa. Un operativo podía insertar filas apuntando a una instalación
--      de OTRO tenant (envenena su baseline / la bloquea).
--   2. `registrar_auditoria` (SECURITY DEFINER) era ejecutable por cualquier
--      authenticated → forja de audit_log cross-tenant. Se revoca.
--   3. Faltaban CHECK >= 0 en columnas de dinero (bruta/neta/partes/estimado).
--   4. Nada impedía dos recaudaciones 'firme' partiendo de la MISMA baseline
--      (doble facturación por carrera). Índice único parcial.
--   5. `validar_desglose_denominaciones` aceptaba valores JSON null (lógica
--      trivalente) saltándose la defensa estructural.
--   6. `obtener_baseline`: ORDER BY sin desempate determinista + cast de
--      `fecha_inicio` (date) a timestamptz con la TZ de sesión en vez de la de
--      la empresa.
--   7. `semanas_iso_entre` devolvía 0 con entradas NULL en vez de NULL.
--   8. `alerta`: la policy de UPDATE permitía a cualquier miembro alterar
--      TODAS las columnas (no solo marcar `leida`). Trigger de protección.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. RLS cross-tenant en INSERT de tablas operativas con instalacion_id.
--    Se ata `empresa_id` del payload a la empresa REAL de la instalación.
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS recaudacion_insert ON public.recaudacion;
CREATE POLICY recaudacion_insert ON public.recaudacion
    FOR INSERT
    WITH CHECK (
        public.usuario_es_operativo(empresa_id)
        AND tecnico_id = auth.uid()
        AND empresa_id = (
            SELECT i.empresa_id FROM public.instalacion i WHERE i.id = instalacion_id
        )
    );

DROP POLICY IF EXISTS lectura_no_recaudada_insert ON public.lectura_no_recaudada;
CREATE POLICY lectura_no_recaudada_insert ON public.lectura_no_recaudada
    FOR INSERT
    WITH CHECK (
        public.usuario_es_operativo(empresa_id)
        AND tecnico_id = auth.uid()
        AND empresa_id = (
            SELECT i.empresa_id FROM public.instalacion i WHERE i.id = instalacion_id
        )
    );

DROP POLICY IF EXISTS cambio_placa_insert ON public.cambio_placa;
CREATE POLICY cambio_placa_insert ON public.cambio_placa
    FOR INSERT
    WITH CHECK (
        public.usuario_es_operativo(empresa_id)
        AND empresa_id = (
            SELECT i.empresa_id FROM public.instalacion i WHERE i.id = instalacion_id
        )
    );

-- -----------------------------------------------------------------------------
-- 2. registrar_auditoria: SECURITY DEFINER que NO debe ser invocable
--    directamente por clientes. Solo la usan los triggers (SECURITY DEFINER,
--    propiedad del owner) y el helper de Edge Functions (service_role). Ambos
--    siguen funcionando tras revocar a public/anon/authenticated.
-- -----------------------------------------------------------------------------
REVOKE EXECUTE ON FUNCTION public.registrar_auditoria(uuid, text, text, uuid, jsonb)
    FROM PUBLIC, anon, authenticated;

-- -----------------------------------------------------------------------------
-- 3. CHECK >= 0 en importes que la lógica nunca debe permitir negativos.
--    (recaudacion_neta = bruta - tasa_total; un contador < baseline producía
--    bruta negativa y se persistía sin reparo a nivel BBDD).
-- -----------------------------------------------------------------------------
ALTER TABLE public.recaudacion
    ADD CONSTRAINT chk_recaudacion_bruta_no_negativa   CHECK (recaudacion_bruta >= 0),
    ADD CONSTRAINT chk_recaudacion_neta_no_negativa    CHECK (recaudacion_neta >= 0),
    ADD CONSTRAINT chk_recaudacion_parte_local_no_neg  CHECK (parte_local >= 0),
    ADD CONSTRAINT chk_recaudacion_parte_empresa_no_neg CHECK (parte_empresa >= 0);

ALTER TABLE public.lectura_no_recaudada
    ADD CONSTRAINT chk_lectura_bruto_estimado_no_negativo CHECK (bruto_estimado >= 0);

-- -----------------------------------------------------------------------------
-- 4. Anti doble-facturación: para una misma instalación no puede haber dos
--    recaudaciones FIRME que partan de la misma baseline. La baseline se
--    identifica por su evento origen (`baseline_id`); cuando es la base de la
--    instalación, `baseline_id` es NULL y se normaliza al uuid cero para que
--    el índice también lo trate como único. Al anular una recaudación deja de
--    contar (índice parcial WHERE estado='firme'), permitiendo re-recaudar.
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_recaudacion_baseline_firme
    ON public.recaudacion (
        instalacion_id,
        COALESCE(baseline_id, '00000000-0000-0000-0000-000000000000'::uuid)
    )
    WHERE estado = 'firme';

-- -----------------------------------------------------------------------------
-- 5. validar_desglose_denominaciones: rechazar valores JSON null explícitos.
--    `item ? 'clave'` es TRUE aunque el valor sea null, y los casts dejaban
--    v_denom/v_cantidad a NULL, cuyas comparaciones (NOT IN / < 0) dan NULL
--    (no TRUE) y NO disparaban el RETURN false. Forzamos el rechazo.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.validar_desglose_denominaciones(p_desglose jsonb)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
PARALLEL SAFE
AS $$
DECLARE
    item       jsonb;
    v_denom    numeric(5, 2);
    v_cantidad bigint;
BEGIN
    IF p_desglose IS NULL OR jsonb_typeof(p_desglose) <> 'array' THEN
        RETURN false;
    END IF;

    FOR item IN SELECT * FROM jsonb_array_elements(p_desglose) LOOP
        IF jsonb_typeof(item) <> 'object' THEN
            RETURN false;
        END IF;
        IF NOT (item ? 'denominacion' AND item ? 'cantidad') THEN
            RETURN false;
        END IF;
        -- Rechazo explícito de null JSON: sin esto el cast a NULL deja pasar
        -- la lógica trivalente (NULL NOT IN (...) = NULL, no FALSE).
        IF jsonb_typeof(item->'denominacion') = 'null'
           OR jsonb_typeof(item->'cantidad') = 'null' THEN
            RETURN false;
        END IF;

        BEGIN
            v_denom    := (item->>'denominacion')::numeric(5, 2);
            v_cantidad := (item->>'cantidad')::bigint;
        EXCEPTION WHEN others THEN
            RETURN false;
        END;

        IF v_denom IS NULL OR v_cantidad IS NULL THEN
            RETURN false;
        END IF;
        IF v_denom NOT IN (0.10, 0.20, 0.50, 1, 2, 5, 10, 20, 50) THEN
            RETURN false;
        END IF;
        IF v_cantidad < 0 THEN
            RETURN false;
        END IF;
    END LOOP;

    RETURN true;
END;
$$;

-- -----------------------------------------------------------------------------
-- 6. obtener_baseline: desempate determinista (created_at/id) y cálculo de la
--    fecha de referencia de la base en la zona horaria de la empresa, no la de
--    sesión. `fecha_inicio` es DATE; `::timestamptz` la fijaba a medianoche
--    UTC, adelantando/atrasando una semana ISO en TZs distintas de UTC.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.obtener_baseline(
    p_instalacion_id uuid,
    p_fecha          timestamptz
) RETURNS public.baseline_info
LANGUAGE plpgsql
STABLE
PARALLEL SAFE
AS $$
DECLARE
    v_result public.baseline_info;
    v_inst   public.instalacion%ROWTYPE;
    v_rec    public.recaudacion%ROWTYPE;
    v_cp     public.cambio_placa%ROWTYPE;
    v_tz     text;
BEGIN
    SELECT * INTO v_inst FROM public.instalacion WHERE id = p_instalacion_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'instalacion no encontrada: %', p_instalacion_id
            USING ERRCODE = 'no_data_found';
    END IF;

    SELECT COALESCE(zona_horaria, 'Europe/Madrid') INTO v_tz
      FROM public.empresa WHERE id = v_inst.empresa_id;
    v_tz := COALESCE(v_tz, 'Europe/Madrid');

    SELECT *
      INTO v_rec
      FROM public.recaudacion
     WHERE instalacion_id = p_instalacion_id
       AND fecha < p_fecha
       AND estado = 'firme'
     ORDER BY fecha DESC, created_at DESC, id DESC
     LIMIT 1;

    SELECT *
      INTO v_cp
      FROM public.cambio_placa
     WHERE instalacion_id = p_instalacion_id
       AND fecha < p_fecha
     ORDER BY fecha DESC, created_at DESC, id DESC
     LIMIT 1;

    -- Empate -> gana cambio_placa (resetea máquina). Si no hay cambio_placa
    -- pero sí recaudación, esa manda. Si no hay nada, se usa la base.
    IF v_cp.id IS NOT NULL AND (v_rec.id IS NULL OR v_cp.fecha >= v_rec.fecha) THEN
        v_result.entradas         := v_cp.contador_entradas_nuevo;
        v_result.salidas          := v_cp.contador_salidas_nuevo;
        v_result.fecha_referencia := v_cp.fecha;
        v_result.origen           := 'cambio_placa';
        v_result.referencia_id    := v_cp.id;
    ELSIF v_rec.id IS NOT NULL THEN
        v_result.entradas         := v_rec.contador_entradas_actual;
        v_result.salidas          := v_rec.contador_salidas_actual;
        v_result.fecha_referencia := v_rec.fecha;
        v_result.origen           := 'recaudacion_anterior';
        v_result.referencia_id    := v_rec.id;
    ELSE
        v_result.entradas         := v_inst.contador_entradas_base;
        v_result.salidas          := v_inst.contador_salidas_base;
        -- Medianoche de fecha_inicio EN LA TZ DE LA EMPRESA (no la de sesión).
        v_result.fecha_referencia := (v_inst.fecha_inicio::timestamp AT TIME ZONE v_tz);
        v_result.origen           := 'instalacion_base';
        v_result.referencia_id    := v_inst.id;
    END IF;

    RETURN v_result;
END;
$$;

-- -----------------------------------------------------------------------------
-- 7. semanas_iso_entre: con cualquier extremo NULL devolver NULL (no 0). Así
--    el caller distingue "0 semanas" de "datos incompletos" y puede abortar.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.semanas_iso_entre(
    p_desde timestamptz,
    p_hasta timestamptz,
    p_tz    text DEFAULT 'Europe/Madrid'
) RETURNS integer
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT CASE
        WHEN p_desde IS NULL OR p_hasta IS NULL OR p_tz IS NULL THEN NULL
        ELSE GREATEST(
            0,
            (
                (date_trunc('week', (p_hasta AT TIME ZONE p_tz)))::date
                - (date_trunc('week', (p_desde AT TIME ZONE p_tz)))::date
            ) / 7
        )
    END;
$$;

-- -----------------------------------------------------------------------------
-- 8. alerta: restringir el UPDATE de miembros a la columna `leida`. RLS no
--    permite acotar columnas, así que usamos un trigger BEFORE UPDATE (mismo
--    patrón que proteger_suscripcion_empresa). El service_role (Edge Functions)
--    y los owner/admin pueden modificar el resto.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.proteger_columnas_alerta()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_role text := coalesce(auth.jwt() ->> 'role', 'service_role');
BEGIN
    -- service_role (jobs/Edge) y admins de la empresa pueden tocar todo.
    IF v_role = 'service_role' OR public.usuario_es_admin(OLD.empresa_id) THEN
        RETURN NEW;
    END IF;
    -- El resto de miembros solo puede cambiar `leida`.
    IF NEW.empresa_id   IS DISTINCT FROM OLD.empresa_id
       OR NEW.tipo      IS DISTINCT FROM OLD.tipo
       OR NEW.referencia_id IS DISTINCT FROM OLD.referencia_id
       OR NEW.mensaje   IS DISTINCT FROM OLD.mensaje
       OR NEW.destinatario_usuario_id IS DISTINCT FROM OLD.destinatario_usuario_id
       OR NEW.creada_en IS DISTINCT FROM OLD.creada_en THEN
        RAISE EXCEPTION 'Solo se puede marcar la alerta como leída'
            USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION public.proteger_columnas_alerta() IS
    'BEFORE UPDATE en alerta: un miembro no-admin solo puede cambiar la columna leida.';

DROP TRIGGER IF EXISTS trg_alerta_proteger_columnas ON public.alerta;
CREATE TRIGGER trg_alerta_proteger_columnas
    BEFORE UPDATE ON public.alerta
    FOR EACH ROW EXECUTE FUNCTION public.proteger_columnas_alerta();

-- -----------------------------------------------------------------------------
-- 9. fecha_referencia_baseline: fecha de referencia (timestamptz) del evento
--    baseline que vio el cliente. La usa `crear-recaudacion` cuando hay
--    conflicto para recalcular las semanas con la baseline del dispositivo y
--    persistir el reparto físico real. Para 'instalacion_base' usa la TZ de la
--    empresa (igual criterio que obtener_baseline). SECURITY INVOKER: respeta
--    RLS, el caller solo resuelve baselines de su propia empresa.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fecha_referencia_baseline(
    p_instalacion_id uuid,
    p_origen         text,
    p_baseline_id    uuid
) RETURNS timestamptz
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_fecha        timestamptz;
    v_fecha_inicio date;
    v_empresa_id   uuid;
    v_tz           text;
BEGIN
    IF p_origen = 'recaudacion_anterior' THEN
        SELECT fecha INTO v_fecha FROM public.recaudacion WHERE id = p_baseline_id;
    ELSIF p_origen = 'cambio_placa' THEN
        SELECT fecha INTO v_fecha FROM public.cambio_placa WHERE id = p_baseline_id;
    ELSE -- instalacion_base
        SELECT i.fecha_inicio, i.empresa_id INTO v_fecha_inicio, v_empresa_id
          FROM public.instalacion i WHERE i.id = p_instalacion_id;
        SELECT COALESCE(zona_horaria, 'Europe/Madrid') INTO v_tz
          FROM public.empresa WHERE id = v_empresa_id;
        v_fecha := (v_fecha_inicio::timestamp AT TIME ZONE COALESCE(v_tz, 'Europe/Madrid'));
    END IF;

    IF v_fecha IS NULL THEN
        RAISE EXCEPTION 'No se pudo resolver la fecha de referencia de la baseline (% / %)',
            p_origen, p_baseline_id USING ERRCODE = 'no_data_found';
    END IF;
    RETURN v_fecha;
END;
$$;

COMMENT ON FUNCTION public.fecha_referencia_baseline(uuid, text, uuid) IS
    'Fecha de referencia del evento baseline que vio el cliente; usada al recalcular semanas en conflictos.';

-- -----------------------------------------------------------------------------
-- 10. resumen_mensual_envio: control de idempotencia del email de liquidación.
--     Una fila por (local, mes) marca que ya se envió. `resumen-mensual` hace
--     un claim atómico (INSERT ON CONFLICT DO NOTHING) antes de enviar, así una
--     segunda ejecución del cron no reenvía liquidaciones duplicadas. La escribe
--     solo el job (service_role); los admin pueden consultarla.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.resumen_mensual_envio (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id  uuid        NOT NULL REFERENCES public.empresa(id) ON DELETE CASCADE,
    local_id    uuid        NOT NULL REFERENCES public.local(id) ON DELETE CASCADE,
    mes         text        NOT NULL CHECK (mes ~ '^\d{4}-\d{2}$'),
    enviado_en  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_resumen_mensual_envio UNIQUE (local_id, mes)
);

COMMENT ON TABLE public.resumen_mensual_envio IS
    'Control de idempotencia del email de resumen mensual: una fila por (local, mes) ya enviado.';

ALTER TABLE public.resumen_mensual_envio ENABLE ROW LEVEL SECURITY;

-- Lectura para owner/admin de la empresa (trazabilidad). Escritura: solo el job
-- con service_role (sin policies de INSERT/UPDATE/DELETE → RLS las bloquea para
-- usuarios normales; service_role las puentea).
CREATE POLICY resumen_mensual_envio_select ON public.resumen_mensual_envio
    FOR SELECT
    USING (public.usuario_es_admin(empresa_id));

-- -----------------------------------------------------------------------------
-- 11. registrar_empresa_con_owner: impedir que un usuario cree varias empresas
--     self-service. Sin esto, un usuario con sesión podía registrar empresas
--     ilimitadas (doble-submit / encadenar trials de 14 días). Se rechaza si ya
--     es owner activo de alguna. (El alta por email/password ya estaba protegida
--     por el conflicto de email en Auth; este guard cubre el caso con sesión.)
--     CREATE OR REPLACE en migración nueva: la original 20260523000100 no se edita.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.registrar_empresa_con_owner(
    p_usuario_id      uuid,
    p_nombre_empresa  text,
    p_nombre_completo text,
    p_trial_dias      integer DEFAULT 14
)
RETURNS TABLE (
    empresa_id         uuid,
    estado_suscripcion text,
    trial_inicio       timestamptz,
    trial_fin          timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id      uuid;
    v_inicio          timestamptz := now();
    v_fin             timestamptz;
    v_nombre_empresa  text := btrim(coalesce(p_nombre_empresa, ''));
    v_nombre_completo text := btrim(coalesce(p_nombre_completo, ''));
BEGIN
    IF v_nombre_empresa = '' THEN
        RAISE EXCEPTION 'nombre_empresa_vacio' USING ERRCODE = '23514';
    END IF;
    IF v_nombre_completo = '' THEN
        RAISE EXCEPTION 'nombre_completo_vacio' USING ERRCODE = '23514';
    END IF;
    IF p_trial_dias IS NULL OR p_trial_dias <= 0 THEN
        RAISE EXCEPTION 'trial_dias_invalido' USING ERRCODE = '22023';
    END IF;

    -- Un usuario solo puede ser owner de UNA empresa por la vía self-service.
    IF EXISTS (
        SELECT 1 FROM public.empresa_usuario
         WHERE usuario_id = p_usuario_id AND rol = 'owner' AND activo = true
    ) THEN
        RAISE EXCEPTION 'usuario_ya_es_owner' USING ERRCODE = '23505';
    END IF;

    v_fin := v_inicio + make_interval(days => p_trial_dias);

    INSERT INTO public.empresa (nombre, estado_suscripcion, trial_inicio, trial_fin)
    VALUES (v_nombre_empresa, 'trial', v_inicio, v_fin)
    RETURNING id INTO v_empresa_id;

    INSERT INTO public.usuario (id, nombre_completo)
    VALUES (p_usuario_id, v_nombre_completo)
    ON CONFLICT (id) DO UPDATE
        SET nombre_completo = coalesce(
            nullif(btrim(public.usuario.nombre_completo), ''),
            excluded.nombre_completo
        );

    INSERT INTO public.empresa_usuario (empresa_id, usuario_id, rol, activo)
    VALUES (v_empresa_id, p_usuario_id, 'owner', true);

    RETURN QUERY
        SELECT v_empresa_id, 'trial'::text, v_inicio, v_fin;
END;
$$;

-- Re-aplicamos los permisos: CREATE OR REPLACE conserva el dueño pero dejamos
-- explícito que solo service_role la ejecuta (igual que la migración original).
REVOKE ALL ON FUNCTION public.registrar_empresa_con_owner(uuid, text, text, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.registrar_empresa_con_owner(uuid, text, text, integer) FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.registrar_empresa_con_owner(uuid, text, text, integer) TO service_role;
