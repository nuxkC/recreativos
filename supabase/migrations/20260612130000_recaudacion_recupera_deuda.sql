-- =============================================================================
-- T-214 — La recaudación amortiza deuda del local (tolva/préstamo).
--
-- Diseño (ver design.md §5.5): en cada recaudación con parte_local > 0 y deuda
-- pendiente, se retiene un % de la parte_local del local para amortizar sus
-- deudas (tolva primero, luego FIFO). El dinero retenido NO es ingreso de la
-- empresa: es amortización. Por eso:
--   * `parte_empresa` NO cambia y el cálculo SSOT (`_shared/calculo.ts`) NO se
--     toca: la recuperación se calcula APARTE (`_shared/recuperacion.ts`).
--   * Se persiste `recaudacion.recuperado_total` (cuánto se retuvo) y
--     `pagado_local` (lo que se lleva el local) GENERADA.
--   * Cada retención queda en el libro mayor `recuperacion` (origen
--     'recaudacion', con `recaudacion_id`), reduciendo el saldo de la deuda.
--
-- Esta migración añade las columnas y DOS RPCs SECURITY DEFINER que usa la Edge
-- Function `crear-recaudacion`/`anular-recaudacion` (service_role):
--   1. persistir_recaudacion(): inserta la recaudación + sus recuperaciones +
--      salda los créditos que llegan a 0, TODO en una transacción (atómico:
--      nunca queda dinero retenido sin reflejar en la deuda, ni al revés).
--   2. revertir_recuperaciones_recaudacion(): al anular, borra las
--      recuperaciones de esa recaudación, reabre los créditos que dejan de
--      estar saldados y pone recuperado_total a 0.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Columnas en recaudacion.
-- -----------------------------------------------------------------------------
ALTER TABLE public.recaudacion
    ADD COLUMN IF NOT EXISTS recuperado_total numeric(10, 2) NOT NULL DEFAULT 0
        CHECK (recuperado_total >= 0);

COMMENT ON COLUMN public.recaudacion.recuperado_total IS
    'Parte de la parte_local retenida en esta recaudación para amortizar deudas del local (tolva/préstamo). 0 = no se recuperó nada. No es ingreso de la empresa: parte_empresa no cambia.';

-- pagado_local: lo que se lleva físicamente el local (= parte_local − retenido).
-- GENERADA para que sea imposible que se descuadre. El desglose_local debe
-- cuadrar con este importe (lo valida crear-recaudacion).
ALTER TABLE public.recaudacion
    ADD COLUMN IF NOT EXISTS pagado_local numeric(10, 2)
        GENERATED ALWAYS AS (parte_local - recuperado_total) STORED;

COMMENT ON COLUMN public.recaudacion.pagado_local IS
    'Importe que se lleva el local tras la recuperación (parte_local − recuperado_total). El desglose_local cuadra con esto.';

-- recuperado_total nunca puede superar la parte_local (pagado_local >= 0).
ALTER TABLE public.recaudacion
    DROP CONSTRAINT IF EXISTS chk_recaudacion_recuperado;
ALTER TABLE public.recaudacion
    ADD CONSTRAINT chk_recaudacion_recuperado
        CHECK (recuperado_total <= parte_local);

-- El desglose físico de la parte local cuadra con lo que SE ENTREGA al local
-- (pagado_local = parte_local − recuperado_total), no con parte_local: cuando hay
-- recuperación, el local recibe menos monedas. Rectifica el chk_desglose_local_suma
-- de 20260519230300 (que exigía == parte_local). Sin recuperación es equivalente.
ALTER TABLE public.recaudacion
    DROP CONSTRAINT IF EXISTS chk_desglose_local_suma;
ALTER TABLE public.recaudacion
    ADD CONSTRAINT chk_desglose_local_suma
        CHECK (sumar_desglose(desglose_local) = parte_local - recuperado_total);

-- -----------------------------------------------------------------------------
-- 2. persistir_recaudacion: inserta la recaudación + sus recuperaciones de forma
--    atómica. La invoca la Edge Function con service_role (que ya validó
--    rol+tenant en TS). El reparto y las cifras vienen ya calculadas (SSOT en
--    TS); aquí solo se persiste de forma consistente.
--
--    p_recaudacion   : jsonb con TODAS las columnas de la fila (incluye
--                      recuperado_total; NO incluye pagado_local, que es
--                      generada). Se mapea con jsonb_populate_record.
--    p_recuperaciones: jsonb array de {credito_id, importe} a imputar.
--    p_usuario_id    : técnico que recauda (autor de las recuperaciones).
-- -----------------------------------------------------------------------------
-- SETOF (devuelve la fila insertada) para que supabase-js `.rpc(...).single()`
-- reciba la recaudación como objeto. DROP previo porque cambia el tipo de retorno.
DROP FUNCTION IF EXISTS public.persistir_recaudacion(jsonb, jsonb, uuid);
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
    v_row     public.recaudacion;
    v_r       jsonb;
    v_cid     uuid;
    v_importe numeric(10, 2);
    v_saldo   numeric(10, 2);
    v_cred    public.credito_local%ROWTYPE;
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
        r.desglose_total, r.desglose_local,
        r.firma_url, r.foto_entradas_url, r.foto_salidas_url,
        r.ocr_entradas_valor, r.ocr_salidas_valor, r.pdf_url, r.observaciones,
        r.dispositivo_id, r.idempotency_key, r.baseline_origen, r.baseline_id,
        COALESCE(r.conflicto, false), r.bruto_recalculado, r.neto_recalculado,
        r.parte_local_recalculada, r.parte_empresa_recalculada,
        COALESCE(NULLIF(r.estado, ''), 'firme')
    FROM jsonb_populate_record(NULL::public.recaudacion, p_recaudacion) AS r
    RETURNING * INTO v_row;

    -- Imputa cada recuperación al crédito correspondiente, revalidando el saldo
    -- vivo DENTRO de la transacción (cierra la carrera si el saldo cambió entre
    -- el cálculo en TS y este INSERT). Bloquea el crédito para serializar.
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

    RETURN NEXT v_row;
    RETURN;
END;
$$;

COMMENT ON FUNCTION public.persistir_recaudacion(jsonb, jsonb, uuid) IS
    'Inserta una recaudación y sus recuperaciones (amortización de deuda) de forma atómica. La usa crear-recaudacion (service_role). Revalida el saldo de cada crédito dentro de la transacción.';

-- -----------------------------------------------------------------------------
-- 3. revertir_recuperaciones_recaudacion: al anular una recaudación, deshace sus
--    recuperaciones (la deuda vuelve a deberse) y pone recuperado_total a 0.
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

    -- La fila de recaudación NO se reescribe: una recaudación anulada conserva
    -- sus cifras originales como histórico (igual que bruto/neto/parte_local).
    -- `recuperado_total`/`pagado_local` quedan como registro de lo que se retuvo;
    -- lo que se deshace es la DEUDA (ledger + estado del crédito), ya hecho arriba.
    -- Además, reescribir recuperado_total rompería chk_desglose_local_suma, que
    -- ata el desglose entregado a pagado_local.
END;
$$;

COMMENT ON FUNCTION public.revertir_recuperaciones_recaudacion(uuid) IS
    'Al anular una recaudación, deshace la DEUDA: borra su ledger de recuperaciones y reabre los créditos que dejan de estar saldados. No reescribe la recaudación (conserva sus cifras como histórico).';

-- -----------------------------------------------------------------------------
-- 4. Permisos: estas RPCs solo las llama el service_role (Edge Functions). Ni
--    authenticated ni anon pueden ejecutarlas (no son CRUD de cliente).
-- -----------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.persistir_recaudacion(jsonb, jsonb, uuid)              FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.revertir_recuperaciones_recaudacion(uuid)              FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.persistir_recaudacion(jsonb, jsonb, uuid)           TO service_role;
GRANT EXECUTE ON FUNCTION public.revertir_recuperaciones_recaudacion(uuid)           TO service_role;
