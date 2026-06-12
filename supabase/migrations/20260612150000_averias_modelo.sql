-- =============================================================================
-- T-220 — Modelo de averías: trazabilidad de fallos y recambios por MÁQUINA.
--
-- Fase 1 del sistema de averías (ver design.md §3.16–§3.17). SOLO trazabilidad:
-- no toca dinero ni el SSOT del cálculo. La tolva (merma/recuperación) llega en
-- la Fase 2 (T-223/T-224), que AÑADE columnas a `averia` con migración aditiva.
--
-- Claves de diseño:
--   * El historial sigue a la MÁQUINA (averia.maquina_id), no a la instalación:
--     una máquina pasa por varios locales en su vida y su hoja de vida debe
--     atravesarlos. instalacion_id/local_id son SNAPSHOT del momento (re-apuntar
--     o cerrar la instalación no reescribe averías pasadas); se derivan
--     server-side de la instalación activa de la máquina al crear la avería.
--   * `maquina.estado='averiada'` pasa a ser CONSECUENCIA de tener ≥1 avería
--     abierta con `pone_maquina_fuera_servicio=true`. Lo mantiene
--     `recalcular_estado_maquina`, que SOLO pone/quita 'averiada'; nunca decide
--     instalada↔almacen por su cuenta (ese estado lo gestiona actualizar_maquina)
--     ni toca una máquina 'baja'. Un fallo leve no saca la máquina de servicio.
--   * Invariante de escritura del repo: los clientes solo SELECT; toda escritura
--     vía RPC SECURITY DEFINER (crear/actualizar/resolver_averia, crear/eliminar
--     _recambio) que valida rol operativo (técnico+) + tenant. REVOKE directo.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Tablas
-- -----------------------------------------------------------------------------
CREATE TABLE public.averia (
    id                          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id                  uuid          NOT NULL REFERENCES public.empresa(id)     ON DELETE RESTRICT,
    maquina_id                  uuid          NOT NULL REFERENCES public.maquina(id)     ON DELETE RESTRICT,
    -- Snapshot de dónde estaba la máquina al ocurrir (NULL si en almacén).
    instalacion_id              uuid          REFERENCES public.instalacion(id)          ON DELETE SET NULL,
    local_id                    uuid          REFERENCES public.local(id)                ON DELETE SET NULL,
    categoria                   text          NOT NULL
        CHECK (categoria IN ('atasco_billete', 'atasco_moneda', 'error', 'falta_pago', 'no_enciende', 'otro')),
    descripcion                 text,
    estado                      text          NOT NULL DEFAULT 'abierta'
        CHECK (estado IN ('abierta', 'en_reparacion', 'resuelta')),
    pone_maquina_fuera_servicio boolean       NOT NULL DEFAULT false,
    reportada_por               uuid          REFERENCES public.usuario(id),
    resuelta_por                uuid          REFERENCES public.usuario(id),
    fecha_reporte               timestamptz   NOT NULL DEFAULT now(),
    fecha_resolucion            timestamptz,
    notas                       text,
    created_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    -- una avería resuelta tiene fecha de resolución (coherencia del cierre).
    CONSTRAINT chk_averia_resuelta
        CHECK (estado <> 'resuelta' OR fecha_resolucion IS NOT NULL)
);

COMMENT ON TABLE public.averia IS
    'Avería de una máquina: qué falla y, vía averia_recambio, qué se cambió. Historial por máquina (maquina_id); instalacion_id/local_id son snapshot del momento.';
COMMENT ON COLUMN public.averia.maquina_id IS
    'Identidad estable: el historial/hoja de vida sigue a la máquina aunque cambie de local o instalación.';
COMMENT ON COLUMN public.averia.instalacion_id IS
    'Snapshot de la instalación activa al reportar (NULL si la máquina estaba en almacén). No se re-apunta.';
COMMENT ON COLUMN public.averia.pone_maquina_fuera_servicio IS
    'Si true, mientras la avería siga abierta la máquina figura como estado=averiada (lo mantiene recalcular_estado_maquina).';

CREATE INDEX idx_averia_maquina        ON public.averia (maquina_id, fecha_reporte DESC);  -- historial por máquina
CREATE INDEX idx_averia_empresa_estado ON public.averia (empresa_id, estado);              -- listados de abiertas

CREATE TABLE public.averia_recambio (
    id          uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id  uuid           NOT NULL REFERENCES public.empresa(id) ON DELETE RESTRICT,
    averia_id   uuid           NOT NULL REFERENCES public.averia(id)  ON DELETE CASCADE,
    pieza       text           NOT NULL,
    cantidad    integer        NOT NULL DEFAULT 1 CHECK (cantidad > 0),
    -- Coste INFORMATIVO (gasto de mantenimiento de la empresa); NO se recupera
    -- de la recaudación (solo el premio de tolva, Fase 2 §5.6).
    coste       numeric(10, 2) CHECK (coste >= 0),
    notas       text,
    created_at  timestamptz    NOT NULL DEFAULT now(),
    updated_at  timestamptz    NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.averia_recambio IS
    'Pieza/recambio sustituido al reparar una avería (1 avería → N recambios). coste informativo, no se recupera.';

CREATE INDEX idx_averia_recambio_averia ON public.averia_recambio (averia_id);

-- updated_at automático (mismo trigger genérico que el resto de tablas).
CREATE TRIGGER trg_averia_updated_at
    BEFORE UPDATE ON public.averia
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
CREATE TRIGGER trg_averia_recambio_updated_at
    BEFORE UPDATE ON public.averia_recambio
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- -----------------------------------------------------------------------------
-- 2. Vista de historial (security_invoker: hereda la RLS del que consulta, igual
--    que el resto de vistas del repo).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW public.v_averia
WITH (security_invoker = true) AS
SELECT
    a.*,
    m.numero_serie AS maquina_numero_serie,
    m.modelo       AS maquina_modelo,
    l.nombre       AS local_nombre,
    (SELECT count(*) FROM public.averia_recambio r WHERE r.averia_id = a.id) AS num_recambios
FROM public.averia a
JOIN public.maquina m ON m.id = a.maquina_id
LEFT JOIN public.local l ON l.id = a.local_id;

COMMENT ON VIEW public.v_averia IS
    'Avería enriquecida con nº de serie/modelo de la máquina, nombre del local (snapshot) y nº de recambios. Para listados e historial por máquina (filtrar por maquina_id, ordenar por fecha_reporte DESC).';

-- -----------------------------------------------------------------------------
-- 3. Sincronización de maquina.estado con las averías abiertas.
--    Solo PONE o QUITA 'averiada'; nunca decide instalada↔almacen por su cuenta
--    (ese estado lo gestiona actualizar_maquina). Una máquina 'baja' no se toca.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.recalcular_estado_maquina(p_maquina_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_estado    text;
    v_fuera     boolean;
    v_instalada boolean;
BEGIN
    SELECT estado INTO v_estado FROM public.maquina WHERE id = p_maquina_id;
    IF NOT FOUND OR v_estado = 'baja' THEN
        RETURN;  -- máquina retirada: su estado no se gestiona aquí.
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM public.averia
         WHERE maquina_id = p_maquina_id
           AND estado IN ('abierta', 'en_reparacion')
           AND pone_maquina_fuera_servicio
    ) INTO v_fuera;

    IF v_fuera THEN
        IF v_estado <> 'averiada' THEN
            UPDATE public.maquina SET estado = 'averiada' WHERE id = p_maquina_id;
        END IF;
    ELSIF v_estado = 'averiada' THEN
        -- Ya no queda avería que la deje fuera de servicio: vuelve al estado
        -- operativo (instalada si tiene instalación activa, si no almacén).
        SELECT EXISTS (
            SELECT 1 FROM public.instalacion
             WHERE maquina_id = p_maquina_id AND estado = 'activa'
        ) INTO v_instalada;
        UPDATE public.maquina
           SET estado = CASE WHEN v_instalada THEN 'instalada' ELSE 'almacen' END
         WHERE id = p_maquina_id;
    END IF;
END;
$$;

COMMENT ON FUNCTION public.recalcular_estado_maquina(uuid) IS
    'Sincroniza maquina.estado con sus averías abiertas: pone averiada si hay alguna fuera-de-servicio abierta, la quita (→ instalada/almacen) si no. No toca instalada/almacen/baja por su cuenta. Uso interno de las RPCs de avería.';

-- -----------------------------------------------------------------------------
-- 4. RPCs de escritura (SECURITY DEFINER, validan rol operativo + tenant).
-- -----------------------------------------------------------------------------

-- 4.1 Alta de avería. Deriva el snapshot instalacion/local de la instalación
--     activa de la máquina (≤1 garantizado por uq_instalacion_maquina_activa).
CREATE OR REPLACE FUNCTION public.crear_averia(
    p_empresa_id                  uuid,
    p_maquina_id                  uuid,
    p_categoria                   text,
    p_descripcion                 text    DEFAULT NULL,
    p_pone_maquina_fuera_servicio boolean DEFAULT false,
    p_notas                       text    DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id             uuid;
    v_maq_empresa    uuid;
    v_instalacion_id uuid;
    v_local_id       uuid;
BEGIN
    IF NOT public.usuario_es_operativo(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para registrar averías'
            USING ERRCODE = '42501';
    END IF;

    SELECT empresa_id INTO v_maq_empresa FROM public.maquina WHERE id = p_maquina_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'máquina no encontrada: %', p_maquina_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF v_maq_empresa <> p_empresa_id THEN
        RAISE EXCEPTION 'la máquina % no pertenece a la empresa', p_maquina_id
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    SELECT id, local_id INTO v_instalacion_id, v_local_id
      FROM public.instalacion
     WHERE maquina_id = p_maquina_id AND estado = 'activa'
     LIMIT 1;

    INSERT INTO public.averia (
        empresa_id, maquina_id, instalacion_id, local_id, categoria, descripcion,
        estado, pone_maquina_fuera_servicio, reportada_por, notas
    ) VALUES (
        p_empresa_id, p_maquina_id, v_instalacion_id, v_local_id, p_categoria, p_descripcion,
        'abierta', COALESCE(p_pone_maquina_fuera_servicio, false), auth.uid(), p_notas
    )
    RETURNING id INTO v_id;

    -- Solo recalculamos si esta avería puede sacar la máquina de servicio; así un
    -- fallo leve nunca altera el estado de la máquina.
    IF COALESCE(p_pone_maquina_fuera_servicio, false) THEN
        PERFORM public.recalcular_estado_maquina(p_maquina_id);
    END IF;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_averia(uuid, uuid, text, text, boolean, text) IS
    'Alta de avería. Valida rol operativo + tenant; deriva instalacion/local snapshot de la instalación activa; opcionalmente pone la máquina fuera de servicio. Devuelve el id.';

-- 4.2 Edición de los datos de la avería (no cambia estado: eso lo hace resolver).
CREATE OR REPLACE FUNCTION public.actualizar_averia(
    p_id                          uuid,
    p_categoria                   text,
    p_descripcion                 text,
    p_pone_maquina_fuera_servicio boolean,
    p_notas                       text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
    v_maquina_id uuid;
BEGIN
    SELECT empresa_id, maquina_id INTO v_empresa_id, v_maquina_id
      FROM public.averia WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'avería no encontrada: %', p_id USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_operativo(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar averías' USING ERRCODE = '42501';
    END IF;

    UPDATE public.averia SET
        categoria                   = p_categoria,
        descripcion                 = p_descripcion,
        pone_maquina_fuera_servicio = COALESCE(p_pone_maquina_fuera_servicio, false),
        notas                       = p_notas
    WHERE id = p_id;

    -- El flag pudo cambiar en cualquier sentido: resincronizamos.
    PERFORM public.recalcular_estado_maquina(v_maquina_id);
END;
$$;

COMMENT ON FUNCTION public.actualizar_averia(uuid, text, text, boolean, text) IS
    'Edición de una avería abierta (categoría, descripción, fuera-de-servicio, notas). Valida rol operativo + tenant y resincroniza el estado de la máquina.';

-- 4.3 Resolución (cierre) de la avería.
CREATE OR REPLACE FUNCTION public.resolver_averia(
    p_id               uuid,
    p_notas_resolucion text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
    v_maquina_id uuid;
    v_estado     text;
BEGIN
    SELECT empresa_id, maquina_id, estado INTO v_empresa_id, v_maquina_id, v_estado
      FROM public.averia WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'avería no encontrada: %', p_id USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_operativo(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar averías' USING ERRCODE = '42501';
    END IF;
    IF v_estado = 'resuelta' THEN
        RAISE EXCEPTION 'la avería ya está resuelta' USING ERRCODE = '22023';
    END IF;

    UPDATE public.averia SET
        estado           = 'resuelta',
        fecha_resolucion = now(),
        resuelta_por     = auth.uid(),
        -- conserva las notas previas y, si las hay, añade las de resolución.
        notas            = CASE
                              WHEN p_notas_resolucion IS NULL OR btrim(p_notas_resolucion) = '' THEN notas
                              WHEN notas IS NULL OR btrim(notas) = '' THEN p_notas_resolucion
                              ELSE notas || E'\n' || p_notas_resolucion
                           END
    WHERE id = p_id;

    PERFORM public.recalcular_estado_maquina(v_maquina_id);
END;
$$;

COMMENT ON FUNCTION public.resolver_averia(uuid, text) IS
    'Cierra una avería (estado resuelta + fecha/usuario de resolución, anexa notas) y resincroniza el estado de la máquina. Valida rol operativo + tenant.';

-- 4.4 Alta de un recambio sobre una avería.
CREATE OR REPLACE FUNCTION public.crear_recambio(
    p_averia_id uuid,
    p_pieza     text,
    p_cantidad  integer DEFAULT 1,
    p_coste     numeric DEFAULT NULL,
    p_notas     text    DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id         uuid;
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.averia WHERE id = p_averia_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'avería no encontrada: %', p_averia_id USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_operativo(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar averías' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.averia_recambio (empresa_id, averia_id, pieza, cantidad, coste, notas)
    VALUES (v_empresa_id, p_averia_id, p_pieza, COALESCE(p_cantidad, 1), p_coste, p_notas)
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_recambio(uuid, text, integer, numeric, text) IS
    'Registra un recambio sustituido en una avería. Valida rol operativo + tenant (derivado de la avería). Devuelve el id.';

-- 4.5 Borrado de un recambio (corrección de errores de carga).
CREATE OR REPLACE FUNCTION public.eliminar_recambio(p_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.averia_recambio WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'recambio no encontrado: %', p_id USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_operativo(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar averías' USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.averia_recambio WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.eliminar_recambio(uuid) IS
    'Borra un recambio. Valida rol operativo + tenant (derivado del recambio).';

-- -----------------------------------------------------------------------------
-- 5. RLS: los clientes solo LEEN (acotado por tenant); escritura solo vía RPC.
-- -----------------------------------------------------------------------------
ALTER TABLE public.averia          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.averia_recambio ENABLE ROW LEVEL SECURITY;

CREATE POLICY averia_select ON public.averia
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));
CREATE POLICY averia_recambio_select ON public.averia_recambio
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));

-- Las default privileges de Supabase conceden escritura a authenticated/anon al
-- crear la tabla; la revocamos (toda escritura pasa por las RPCs de arriba).
REVOKE INSERT, UPDATE, DELETE ON public.averia          FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.averia_recambio FROM authenticated, anon;

-- -----------------------------------------------------------------------------
-- 6. Permisos de las funciones.
--    recalcular_estado_maquina es interna (solo la llaman las RPCs); ningún
--    cliente la ejecuta. Las RPCs de escritura: solo authenticated, no anon.
-- -----------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.recalcular_estado_maquina(uuid)                       FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION public.crear_averia(uuid, uuid, text, text, boolean, text)   FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.actualizar_averia(uuid, text, text, boolean, text)    FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.resolver_averia(uuid, text)                           FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.crear_recambio(uuid, text, integer, numeric, text)    FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.eliminar_recambio(uuid)                               FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.crear_averia(uuid, uuid, text, text, boolean, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.actualizar_averia(uuid, text, text, boolean, text)  TO authenticated;
GRANT EXECUTE ON FUNCTION public.resolver_averia(uuid, text)                         TO authenticated;
GRANT EXECUTE ON FUNCTION public.crear_recambio(uuid, text, integer, numeric, text)  TO authenticated;
GRANT EXECUTE ON FUNCTION public.eliminar_recambio(uuid)                             TO authenticated;
