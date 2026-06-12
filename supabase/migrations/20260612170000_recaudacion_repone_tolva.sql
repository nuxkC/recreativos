-- =============================================================================
-- T-224 — La recaudación REPONE la merma de tolva ANTES del reparto (§5.6).
--
-- A diferencia de la recuperación de deuda (T-214, §5.5, que NO toca el SSOT y
-- se retiene de la parte_local), la reposición de tolva SÍ cambia el reparto:
-- el premio pagado de la tolva se devuelve físicamente a la máquina y lo asumen
-- local y empresa según su %. Por eso el SSOT (`_shared/calculo.ts` + espejo
-- `Calculo.kt`) ya calcula:
--
--   reposicion_tolva = min(neto, pendiente_tolva)      (de v_instalacion_tolva)
--   base_reparto     = neto − reposicion_tolva
--   parte_local      = round(base_reparto × % / 100, 2)
--   parte_empresa    = base_reparto − parte_local
--   Invariante: reposicion_tolva + parte_local + parte_empresa = neto.
--
-- Esta migración cierra el lado servidor:
--   1. Rectifica chk_recaudacion_partes para que ate el reparto a base_reparto
--      (parte_local + parte_empresa + reposicion_tolva = neto), no a neto.
--   2. persistir_recaudacion(): si reposicion_tolva > 0, inserta el movimiento de
--      reposición en tolva_movimiento de forma ATÓMICA con la recaudación,
--      revalidando contra el pendiente vivo.
--   3. revertir_recuperaciones_recaudacion(): al anular, borra también esa
--      reposición (la merma vuelve a estar pendiente).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Rectificar el invariante del reparto: ahora la suma cuadra con el neto solo
--    SUMANDO la reposición (parte_local + parte_empresa = base_reparto). Las
--    filas previas tienen reposicion_tolva = 0 (DEFAULT de T-223), así que la
--    rectificación es equivalente para el histórico.
-- -----------------------------------------------------------------------------
ALTER TABLE public.recaudacion DROP CONSTRAINT IF EXISTS chk_recaudacion_partes;
ALTER TABLE public.recaudacion
    ADD CONSTRAINT chk_recaudacion_partes
        CHECK (parte_local + parte_empresa + reposicion_tolva = recaudacion_neta);

-- -----------------------------------------------------------------------------
-- 2. persistir_recaudacion: + inserción atómica de la reposición de tolva.
--    Misma firma y retorno que T-214; CREATE OR REPLACE conserva los grants.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.persistir_recaudacion(
    p_recaudacion    jsonb,
    p_recuperaciones jsonb,
    p_usuario_id     uuid
) RETURNS SETOF public.recaudacion
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_row             public.recaudacion;
    v_r               jsonb;
    v_cid             uuid;
    v_importe         numeric(10, 2);
    v_saldo           numeric(10, 2);
    v_cred            public.credito_local%ROWTYPE;
    v_pendiente_tolva numeric(10, 2);
BEGIN
    -- Inserta la recaudación. Listamos columnas explícitamente para (a) excluir
    -- la generada pagado_local y (b) dejar que created_at/updated_at tomen su
    -- DEFAULT. jsonb_populate_record castea cada valor a su tipo de columna.
    INSERT INTO public.recaudacion (
        id, empresa_id, instalacion_id, tecnico_id, fecha,
        contador_entradas_anterior, contador_salidas_anterior,
        contador_entradas_actual, contador_salidas_actual,
        contador_salidas_leido, recaudacion_bruta_real, redondeo_aplicado,
        valor_credito_aplicado, recaudacion_bruta, semanas_aplicadas,
        tasa_semanal_aplicada, tasa_total_aplicada, recaudacion_neta,
        porcentaje_local_aplicado, parte_local, parte_empresa, recuperado_total,
        reposicion_tolva,
        desglose_total, desglose_local,
        firma_url, foto_entradas_url, foto_salidas_url,
        ocr_entradas_valor, ocr_salidas_valor, pdf_url, observaciones,
        dispositivo_id, idempotency_key, baseline_origen, baseline_id,
        conflicto, bruto_recalculado, neto_recalculado,
        parte_local_recalculada, parte_empresa_recalculada,
        estado
    )
    SELECT
        r.id, r.empresa_id, r.instalacion_id, r.tecnico_id, r.fecha,
        r.contador_entradas_anterior, r.contador_salidas_anterior,
        r.contador_entradas_actual, r.contador_salidas_actual,
        r.contador_salidas_leido, r.recaudacion_bruta_real, r.redondeo_aplicado,
        r.valor_credito_aplicado, r.recaudacion_bruta, r.semanas_aplicadas,
        r.tasa_semanal_aplicada, r.tasa_total_aplicada, r.recaudacion_neta,
        r.porcentaje_local_aplicado, r.parte_local, r.parte_empresa, COALESCE(r.recuperado_total, 0),
        COALESCE(r.reposicion_tolva, 0),
        r.desglose_total, r.desglose_local,
        r.firma_url, r.foto_entradas_url, r.foto_salidas_url,
        r.ocr_entradas_valor, r.ocr_salidas_valor, r.pdf_url, r.observaciones,
        r.dispositivo_id, r.idempotency_key, r.baseline_origen, r.baseline_id,
        COALESCE(r.conflicto, false), r.bruto_recalculado, r.neto_recalculado,
        r.parte_local_recalculada, r.parte_empresa_recalculada,
        COALESCE(NULLIF(r.estado, ''), 'firme')
    FROM jsonb_populate_record(NULL::public.recaudacion, p_recaudacion) AS r
    RETURNING * INTO v_row;

    -- Imputa cada recuperación de deuda (§5.5) al crédito correspondiente,
    -- revalidando el saldo vivo DENTRO de la transacción (cierra la carrera si el
    -- saldo cambió entre el cálculo en TS y este INSERT). Bloquea el crédito.
    IF p_recuperaciones IS NOT NULL THEN
        FOR v_r IN SELECT * FROM jsonb_array_elements(p_recuperaciones) LOOP
            v_cid     := (v_r ->> 'credito_id')::uuid;
            v_importe := round((v_r ->> 'importe')::numeric, 2);

            IF v_importe <= 0 THEN
                CONTINUE;
            END IF;

            SELECT * INTO v_cred FROM public.credito_local WHERE id = v_cid FOR UPDATE;
            IF NOT FOUND THEN
                RAISE EXCEPTION 'credito de deuda no encontrado: %', v_cid USING ERRCODE = 'no_data_found';
            END IF;
            IF v_cred.empresa_id <> v_row.empresa_id THEN
                RAISE EXCEPTION 'credito % no pertenece a la empresa de la recaudacion', v_cid
                    USING ERRCODE = 'foreign_key_violation';
            END IF;
            IF v_cred.estado <> 'abierto' THEN
                RAISE EXCEPTION 'credito % no esta abierto', v_cid USING ERRCODE = '22023';
            END IF;

            SELECT saldo INTO v_saldo FROM public.v_credito_local_saldo WHERE credito_id = v_cid;
            IF v_importe > v_saldo THEN
                RAISE EXCEPTION 'recuperacion (%) supera el saldo vivo (%) del credito %', v_importe, v_saldo, v_cid
                    USING ERRCODE = '23514';
            END IF;

            INSERT INTO public.recuperacion (
                empresa_id, local_id, credito_id, origen, importe, recaudacion_id, usuario_id, notas
            ) VALUES (
                v_cred.empresa_id, v_cred.local_id, v_cid, 'recaudacion', v_importe, v_row.id, p_usuario_id,
                'Retenido de la parte del local en la recaudacion'
            );

            IF v_saldo - v_importe <= 0 THEN
                UPDATE public.credito_local SET estado = 'saldado' WHERE id = v_cid;
            END IF;
        END LOOP;
    END IF;

    -- Reposición de tolva (§5.6): el premio recuperado vuelve FÍSICAMENTE a la
    -- tolva como un movimiento de reposición, atómico con la recaudación. Se
    -- revalida contra el pendiente vivo (cierra la carrera con un saldo admin de
    -- saldar_tolva_pendiente). La tolva es por instalación = una sola máquina, así
    -- que no hay la carrera cruzada que sí tiene la deuda (por local).
    IF COALESCE(v_row.reposicion_tolva, 0) > 0 THEN
        SELECT pendiente INTO v_pendiente_tolva
          FROM public.v_instalacion_tolva WHERE instalacion_id = v_row.instalacion_id;
        IF v_row.reposicion_tolva > COALESCE(v_pendiente_tolva, 0) THEN
            RAISE EXCEPTION 'reposicion de tolva (%) supera el pendiente vivo (%) de la instalacion %',
                v_row.reposicion_tolva, v_pendiente_tolva, v_row.instalacion_id
                USING ERRCODE = '23514';
        END IF;

        INSERT INTO public.tolva_movimiento (
            empresa_id, instalacion_id, tipo, importe, recaudacion_id, usuario_id, notas
        ) VALUES (
            v_row.empresa_id, v_row.instalacion_id, 'reposicion', v_row.reposicion_tolva, v_row.id, p_usuario_id,
            'Reposicion de tolva recuperada en la recaudacion'
        );
    END IF;

    RETURN NEXT v_row;
    RETURN;
END;
$$;

COMMENT ON FUNCTION public.persistir_recaudacion(jsonb, jsonb, uuid) IS
    'Inserta una recaudación, sus recuperaciones de deuda (§5.5) y su reposición de tolva (§5.6) de forma atómica. La usa crear-recaudacion (service_role). Revalida saldos de crédito y el pendiente de tolva dentro de la transacción.';

-- -----------------------------------------------------------------------------
-- 3. revertir_recuperaciones_recaudacion: + revertir la reposición de tolva.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.revertir_recuperaciones_recaudacion(
    p_recaudacion_id uuid
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_afectados uuid[];
BEGIN
    SELECT array_agg(DISTINCT credito_id) INTO v_afectados
      FROM public.recuperacion
     WHERE recaudacion_id = p_recaudacion_id;

    DELETE FROM public.recuperacion WHERE recaudacion_id = p_recaudacion_id;

    -- Reabrir los créditos que, tras quitar estas recuperaciones, dejan de estar
    -- saldados (su saldo vuelve a ser > 0). Si otra recaudación los mantenía
    -- saldados, el saldo sigue 0 y no se reabren.
    IF v_afectados IS NOT NULL THEN
        UPDATE public.credito_local c
           SET estado = 'abierto'
         WHERE c.id = ANY (v_afectados)
           AND c.estado = 'saldado'
           AND (SELECT saldo FROM public.v_credito_local_saldo WHERE credito_id = c.id) > 0;
    END IF;

    -- Revertir también la reposición de tolva de esta recaudación (§5.6): se borra
    -- su movimiento de reposición y la merma vuelve a estar pendiente, lista para
    -- recuperarse en una recaudación futura. La fila de recaudación NO se reescribe:
    -- conserva reposicion_tolva (y recuperado_total) como histórico, igual que el
    -- resto de cifras de una recaudación anulada.
    DELETE FROM public.tolva_movimiento
     WHERE recaudacion_id = p_recaudacion_id AND tipo = 'reposicion';
END;
$$;

COMMENT ON FUNCTION public.revertir_recuperaciones_recaudacion(uuid) IS
    'Al anular una recaudación, deshace la DEUDA (borra su ledger de recuperaciones y reabre créditos) y la REPOSICIÓN de tolva (borra su movimiento; la merma vuelve a estar pendiente). No reescribe la recaudación (conserva sus cifras como histórico).';
