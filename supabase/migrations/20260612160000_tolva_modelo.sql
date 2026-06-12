-- =============================================================================
-- T-223 — Tolva por avería: merma/reposición y tolva efectiva (Fase 2).
--
-- Migración ADITIVA sobre el modelo de averías de T-220. Añade el soporte de
-- "premio de tolva": cuando una máquina paga un premio de su tolva sin que el
-- contador de salidas lo registre, la tolva física baja (una MERMA). Esa merma
-- se RECUPERA en la siguiente recaudación reponiéndola a la tolva (una
-- REPOSICIÓN), y local/empresa la asumen según su % (el reparto real lo hace el
-- SSOT en T-224; aquí solo se monta el ledger y el alta de la merma).
--
-- Claves de diseño (design.md §3.16/§3.18/§5.6):
--   * `instalacion.tolva` pasa a interpretarse como tolva TEÓRICA (nivel
--     objetivo). La tolva EFECTIVA se DERIVA de un ledger append-only
--     (`tolva_movimiento`), nunca se almacena mutable — mismo patrón que
--     `v_credito_local_saldo`.
--   * Una avería con `afecta_tolva` inserta una `merma`; `crear_averia` la crea
--     de forma atómica con el alta. Solo se recupera estando INSTALADA.
--   * La merma por avería NO toca la deuda de tolva del cebado (§3.14): aquella
--     es lo que el local debe por la tolva física; esto es un premio compartido.
--   * Edge case §5.6: si la máquina se da de baja con merma pendiente y no habrá
--     recaudación de la que reponer, un admin la salda/condona vía RPC.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. averia += columnas de tolva (premio pagado de la tolva por la avería).
-- -----------------------------------------------------------------------------
ALTER TABLE public.averia
    ADD COLUMN IF NOT EXISTS afecta_tolva  boolean        NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS importe_tolva numeric(10, 2) NOT NULL DEFAULT 0;

ALTER TABLE public.averia
    -- importe siempre ≥ 0; solo hay importe si la avería afecta a la tolva; y solo
    -- se puede recuperar (merma) estando instalada (snapshot instalacion_id NOT NULL).
    ADD CONSTRAINT chk_averia_importe_tolva_pos CHECK (importe_tolva >= 0),
    ADD CONSTRAINT chk_averia_tolva_importe      CHECK (afecta_tolva OR importe_tolva = 0),
    ADD CONSTRAINT chk_averia_tolva_inst         CHECK (NOT afecta_tolva OR instalacion_id IS NOT NULL);

COMMENT ON COLUMN public.averia.afecta_tolva IS
    'TRUE si la avería pagó un premio de la tolva (merma a recuperar). Requiere instalación activa.';
COMMENT ON COLUMN public.averia.importe_tolva IS
    'Importe del premio pagado de la tolva (€). Genera una merma en tolva_movimiento; se repone en la siguiente recaudación (T-224).';

-- -----------------------------------------------------------------------------
-- 2. tolva_movimiento: ledger append-only de merma/reposición por instalación.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.tolva_movimiento (
    id              uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id      uuid           NOT NULL REFERENCES public.empresa(id)      ON DELETE RESTRICT,
    instalacion_id  uuid           NOT NULL REFERENCES public.instalacion(id)  ON DELETE RESTRICT,
    tipo            text           NOT NULL CHECK (tipo IN ('merma', 'reposicion')),
    importe         numeric(10, 2) NOT NULL CHECK (importe > 0),
    averia_id       uuid           REFERENCES public.averia(id),       -- merma: qué avería la causó
    recaudacion_id  uuid           REFERENCES public.recaudacion(id),  -- reposición: en qué recaudación se repuso (NULL si saldo admin)
    fecha           timestamptz    NOT NULL DEFAULT now(),
    usuario_id      uuid           REFERENCES public.usuario(id),
    notas           text,
    -- Coherencia del ledger: una merma siempre cuelga de una avería (nunca de una
    -- recaudación); una reposición nunca cuelga de una avería (sí de una
    -- recaudación, o de ninguna si es un saldo administrativo §5.6).
    CONSTRAINT chk_tolva_mov_origen CHECK (
        (tipo = 'merma'      AND averia_id IS NOT NULL AND recaudacion_id IS NULL)
        OR
        (tipo = 'reposicion' AND averia_id IS NULL)
    )
);

COMMENT ON TABLE public.tolva_movimiento IS
    'Ledger append-only de la tolva por instalación: merma (premio pagado por una avería) y reposición (recuperada en una recaudación o saldada por admin). La tolva efectiva se deriva de aquí en v_instalacion_tolva.';

CREATE INDEX idx_tolva_mov_instalacion ON public.tolva_movimiento(instalacion_id);
CREATE INDEX idx_tolva_mov_averia      ON public.tolva_movimiento(averia_id)      WHERE averia_id IS NOT NULL;
CREATE INDEX idx_tolva_mov_recaudacion ON public.tolva_movimiento(recaudacion_id) WHERE recaudacion_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 3. Tolva efectiva DERIVADA (nunca almacenada): teórica − merma + repuesto.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW public.v_instalacion_tolva
WITH (security_invoker = true) AS
SELECT
    i.id                                                              AS instalacion_id,
    i.empresa_id,
    i.local_id,
    i.maquina_id,
    i.estado,
    i.tolva                                                           AS teorica,
    COALESCE(SUM(m.importe) FILTER (WHERE m.tipo = 'merma'), 0)       AS merma,
    COALESCE(SUM(m.importe) FILTER (WHERE m.tipo = 'reposicion'), 0)  AS repuesto,
    i.tolva
        - COALESCE(SUM(m.importe) FILTER (WHERE m.tipo = 'merma'), 0)
        + COALESCE(SUM(m.importe) FILTER (WHERE m.tipo = 'reposicion'), 0)
                                                                      AS efectiva,
    -- pendiente = teórica − efectiva = merma − repuesto (lo que falta por reponer).
    -- Las reposiciones nunca superan la merma (se topan a min(neto, pendiente) /
    -- al pendiente exacto en el saldo admin), así que es ≥ 0 por invariante.
    COALESCE(SUM(m.importe) FILTER (WHERE m.tipo = 'merma'), 0)
        - COALESCE(SUM(m.importe) FILTER (WHERE m.tipo = 'reposicion'), 0)
                                                                      AS pendiente
FROM public.instalacion i
LEFT JOIN public.tolva_movimiento m ON m.instalacion_id = i.id
GROUP BY i.id;

COMMENT ON VIEW public.v_instalacion_tolva IS
    'Tolva por instalación: teórica (instalacion.tolva), merma y repuesto (Σ del ledger), efectiva (teórica−merma+repuesto) y pendiente (merma−repuesto). Fuente única del pendiente que la recaudación recupera (T-224).';

-- -----------------------------------------------------------------------------
-- 4. recaudacion += reposicion_tolva (cuánto repuso esta recaudación).
--    La rectificación del CHECK del reparto (parte_local+parte_empresa = neto −
--    reposicion) la hace T-224, cuando el SSOT empieza a poblar esta columna.
-- -----------------------------------------------------------------------------
ALTER TABLE public.recaudacion
    ADD COLUMN IF NOT EXISTS reposicion_tolva numeric(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE public.recaudacion
    ADD CONSTRAINT chk_recaudacion_reposicion_tolva CHECK (reposicion_tolva >= 0);

COMMENT ON COLUMN public.recaudacion.reposicion_tolva IS
    'Importe repuesto a la tolva en esta recaudación (recupera merma pendiente antes del reparto, §5.6). Poblado por el SSOT en T-224; por defecto 0.';

-- -----------------------------------------------------------------------------
-- 5. crear_averia: nueva firma con tolva. Inserta la MERMA atómicamente.
--    Se DROPa la firma de 6 args de T-220 y se recrea con 2 args opcionales más
--    (afecta_tolva, importe_tolva), con DEFAULT para que los llamadores actuales
--    (web/android de T-221/T-222) sigan resolviendo a esta función vía defaults.
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.crear_averia(uuid, uuid, text, text, boolean, text);

CREATE OR REPLACE FUNCTION public.crear_averia(
    p_empresa_id                  uuid,
    p_maquina_id                  uuid,
    p_categoria                   text,
    p_descripcion                 text    DEFAULT NULL,
    p_pone_maquina_fuera_servicio boolean DEFAULT false,
    p_notas                       text    DEFAULT NULL,
    p_afecta_tolva                boolean DEFAULT false,
    p_importe_tolva               numeric DEFAULT 0
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
    v_afecta_tolva   boolean       := COALESCE(p_afecta_tolva, false);
    v_importe_tolva  numeric(10,2) := round(COALESCE(p_importe_tolva, 0), 2);
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

    -- Un premio de tolva solo se recupera con la máquina instalada y con importe
    -- real; si no, lo rechazamos con un error claro (en vez de un CHECK crudo).
    IF v_afecta_tolva THEN
        IF v_instalacion_id IS NULL THEN
            RAISE EXCEPTION 'no se puede registrar merma de tolva: la máquina no está instalada'
                USING ERRCODE = '22023';
        END IF;
        IF v_importe_tolva <= 0 THEN
            RAISE EXCEPTION 'el importe de tolva debe ser > 0 cuando la avería afecta a la tolva'
                USING ERRCODE = '22023';
        END IF;
    ELSE
        v_importe_tolva := 0;  -- coherencia con chk_averia_tolva_importe
    END IF;

    INSERT INTO public.averia (
        empresa_id, maquina_id, instalacion_id, local_id, categoria, descripcion,
        estado, pone_maquina_fuera_servicio, reportada_por, notas,
        afecta_tolva, importe_tolva
    ) VALUES (
        p_empresa_id, p_maquina_id, v_instalacion_id, v_local_id, p_categoria, p_descripcion,
        'abierta', COALESCE(p_pone_maquina_fuera_servicio, false), auth.uid(), p_notas,
        v_afecta_tolva, v_importe_tolva
    )
    RETURNING id INTO v_id;

    -- La merma baja la tolva efectiva; se repondrá en la próxima recaudación.
    IF v_afecta_tolva THEN
        INSERT INTO public.tolva_movimiento (
            empresa_id, instalacion_id, tipo, importe, averia_id, usuario_id, notas
        ) VALUES (
            p_empresa_id, v_instalacion_id, 'merma', v_importe_tolva, v_id, auth.uid(),
            'Merma por avería: premio pagado de la tolva'
        );
    END IF;

    -- Solo recalculamos si esta avería puede sacar la máquina de servicio; así un
    -- fallo leve nunca altera el estado de la máquina.
    IF COALESCE(p_pone_maquina_fuera_servicio, false) THEN
        PERFORM public.recalcular_estado_maquina(p_maquina_id);
    END IF;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_averia(uuid, uuid, text, text, boolean, text, boolean, numeric) IS
    'Alta de avería. Valida rol operativo + tenant; deriva instalacion/local snapshot de la instalación activa; opcionalmente pone la máquina fuera de servicio. Si afecta_tolva, exige instalación + importe > 0 e inserta la merma en tolva_movimiento. Devuelve el id.';

-- -----------------------------------------------------------------------------
-- 6. saldar_tolva_pendiente: cierre administrativo de la merma pendiente (§5.6).
--    Cuando una instalación se da de baja con merma pendiente y no habrá
--    recaudación de la que reponer, un admin la salda insertando la reposición
--    que falta (sin recaudación asociada). Análogo a condonar_credito.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.saldar_tolva_pendiente(
    p_instalacion_id uuid,
    p_notas          text DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
    v_pendiente  numeric(10,2);
    v_id         uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id
      FROM public.instalacion WHERE id = p_instalacion_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'instalación no encontrada: %', p_instalacion_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_admin(v_empresa_id) THEN
        RAISE EXCEPTION 'solo un administrador puede saldar la merma de tolva pendiente'
            USING ERRCODE = '42501';
    END IF;

    SELECT pendiente INTO v_pendiente
      FROM public.v_instalacion_tolva WHERE instalacion_id = p_instalacion_id;
    IF COALESCE(v_pendiente, 0) <= 0 THEN
        RAISE EXCEPTION 'la instalación % no tiene merma de tolva pendiente', p_instalacion_id
            USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.tolva_movimiento (
        empresa_id, instalacion_id, tipo, importe, recaudacion_id, usuario_id, notas
    ) VALUES (
        v_empresa_id, p_instalacion_id, 'reposicion', v_pendiente, NULL, auth.uid(),
        COALESCE(p_notas, 'Saldo administrativo de merma de tolva pendiente (baja sin reposición futura)')
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.saldar_tolva_pendiente(uuid, text) IS
    'Cierra administrativamente la merma de tolva pendiente de una instalación (edge case de baja §5.6): inserta la reposición que falta, sin recaudación asociada. Requiere rol admin.';

-- -----------------------------------------------------------------------------
-- 7. RLS + permisos.
-- -----------------------------------------------------------------------------
ALTER TABLE public.tolva_movimiento ENABLE ROW LEVEL SECURITY;

CREATE POLICY tolva_movimiento_select ON public.tolva_movimiento
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));

-- Toda escritura pasa por las RPCs (crear_averia / la recaudación de T-224 /
-- saldar_tolva_pendiente); los clientes solo leen.
REVOKE INSERT, UPDATE, DELETE ON public.tolva_movimiento FROM authenticated, anon;

-- crear_averia cambió de firma: revocar/conceder sobre la nueva (8 args).
REVOKE ALL ON FUNCTION public.crear_averia(uuid, uuid, text, text, boolean, text, boolean, numeric) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.crear_averia(uuid, uuid, text, text, boolean, text, boolean, numeric) TO authenticated;

REVOKE ALL ON FUNCTION public.saldar_tolva_pendiente(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.saldar_tolva_pendiente(uuid, text) TO authenticated;
