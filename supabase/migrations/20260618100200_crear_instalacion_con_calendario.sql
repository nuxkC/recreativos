-- =============================================================================
-- Planificación de recaudación — P1.3: crear_instalacion fija el calendario.
--
-- Instalar la primera máquina de un local es el momento natural de pactar cada
-- cuántas semanas y desde qué fecha se recauda. Se añaden dos parámetros
-- OPCIONALES a crear_instalacion que, si vienen, fijan el calendario del local
-- (misma config que la ficha de local / actualizar_calendario_local).
--
-- Añadir parámetros cambia la FIRMA → es una función distinta por overloading.
-- Para no dejar dos overloads ambiguos se DROPEA primero la firma de 10 args
-- (mismo patrón que 20260612120000 hizo con la de 8) y se recrea con 12.
-- El cuerpo de tolva/deuda es idéntico al vigente; solo se añade el bloque de
-- calendario (validación + UPDATE del local).
-- Spec: docs/superpowers/specs/2026-06-18-planificacion-recaudacion-design.md §4/§6.1.
-- =============================================================================

DROP FUNCTION IF EXISTS public.crear_instalacion(
    uuid, uuid, uuid, uuid, date, numeric, numeric, text, numeric, uuid);

CREATE OR REPLACE FUNCTION public.crear_instalacion(
    p_empresa_id                uuid,
    p_maquina_id                uuid,
    p_licencia_id               uuid,
    p_local_id                  uuid,
    p_fecha_inicio              date,
    p_tasa_semanal              numeric,
    p_porcentaje_local          numeric,
    p_notas                     text     DEFAULT NULL,
    p_tolva                     numeric  DEFAULT 0,
    p_tolva_continua_credito_id uuid     DEFAULT NULL,
    p_cadencia_semanas          smallint DEFAULT NULL,
    p_fecha_inicio_recaudacion  date     DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id    uuid;
    v_deuda numeric(10, 2);
    v_cred  public.credito_local%ROWTYPE;
BEGIN
    IF NOT public.usuario_es_gestor(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar instalaciones'
            USING ERRCODE = '42501';
    END IF;

    -- Aislamiento cross-tenant: máquina, licencia y local de la MISMA empresa.
    IF NOT EXISTS (SELECT 1 FROM public.maquina  WHERE id = p_maquina_id  AND empresa_id = p_empresa_id)
       OR NOT EXISTS (SELECT 1 FROM public.licencia WHERE id = p_licencia_id AND empresa_id = p_empresa_id)
       OR NOT EXISTS (SELECT 1 FROM public.local    WHERE id = p_local_id    AND empresa_id = p_empresa_id) THEN
        RAISE EXCEPTION 'maquina/licencia/local no pertenecen a la empresa %', p_empresa_id
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    IF COALESCE(p_tolva, 0) < 0 THEN
        RAISE EXCEPTION 'la tolva no puede ser negativa' USING ERRCODE = '22023';
    END IF;

    -- Calendario de recaudación opcional: coherencia cadencia ↔ fecha y
    -- cadencia > 0. Se valida ANTES de insertar para fallar pronto; si algo no
    -- cuadra, la transacción entera (también la instalación) se deshace.
    IF (p_cadencia_semanas IS NULL) <> (p_fecha_inicio_recaudacion IS NULL) THEN
        RAISE EXCEPTION 'cadencia_semanas y fecha_inicio_recaudacion deben fijarse juntas o ninguna'
            USING ERRCODE = '22023';
    END IF;
    IF p_cadencia_semanas IS NOT NULL AND p_cadencia_semanas <= 0 THEN
        RAISE EXCEPTION 'cadencia_semanas debe ser > 0: %', p_cadencia_semanas
            USING ERRCODE = '22023';
    END IF;

    -- contador_*_base se omiten a propósito: los rellena el trigger
    -- trg_set_contador_base_instalacion heredándolos de la máquina.
    INSERT INTO public.instalacion (
        empresa_id, maquina_id, licencia_id, local_id,
        fecha_inicio, tasa_semanal, porcentaje_local,
        estado, notas, tolva
    ) VALUES (
        p_empresa_id, p_maquina_id, p_licencia_id, p_local_id,
        p_fecha_inicio, p_tasa_semanal, p_porcentaje_local,
        'activa', p_notas, COALESCE(p_tolva, 0)
    )
    RETURNING id INTO v_id;

    -- --- Deuda de tolva ------------------------------------------------------
    -- (A) TRASLADO: la MISMA tolva cambia de máquina. Se re-apunta la deuda
    --     existente a la nueva instalación; NO se crea otra (evita duplicar).
    IF p_tolva_continua_credito_id IS NOT NULL THEN
        SELECT * INTO v_cred FROM public.credito_local
         WHERE id = p_tolva_continua_credito_id;
        IF NOT FOUND
           OR v_cred.local_id   <> p_local_id
           OR v_cred.empresa_id <> p_empresa_id
           OR v_cred.tipo       <> 'tolva'
           OR v_cred.estado     <> 'abierto' THEN
            RAISE EXCEPTION 'credito de tolva a trasladar inválido para este local: %', p_tolva_continua_credito_id
                USING ERRCODE = '22023';
        END IF;
        -- El AJUSTE del principal por delta de tolva (más → deuda adicional;
        -- menos → condonación parcial) es parte del flujo de traslado completo
        -- (trabajo futuro). Aquí solo se re-apunta el puntero preservando el
        -- principal vivo; instalacion.tolva ya refleja el importe físico real.
        UPDATE public.credito_local
           SET instalacion_id = v_id
         WHERE id = p_tolva_continua_credito_id;

    -- (B) TOLVA NUEVA: la fracción que debe el local = porcentaje_local × tolva.
    ELSIF COALESCE(p_tolva, 0) > 0 THEN
        v_deuda := round(COALESCE(p_tolva, 0) * p_porcentaje_local / 100, 2);
        IF v_deuda > 0 THEN
            -- Guardarraíl anti doble-cómputo: si el local YA tiene una tolva
            -- abierta y NO se indicó traslado, puede ser una máquina movida
            -- contada como nueva. No bloqueamos (un local puede tener varias
            -- máquinas, cada una con su tolva), pero lo dejamos avisado para que
            -- el cliente pregunte "¿es un traslado?" (T-213/T-215).
            IF EXISTS (
                SELECT 1 FROM public.credito_local
                 WHERE local_id = p_local_id AND tipo = 'tolva' AND estado = 'abierto'
            ) THEN
                RAISE WARNING 'el local % ya tiene una tolva abierta; si es la misma maquina movida usa p_tolva_continua_credito_id para no duplicar la deuda', p_local_id;
            END IF;

            INSERT INTO public.credito_local (
                empresa_id, local_id, tipo, instalacion_id,
                principal, tipo_interes, fecha, estado, notas
            ) VALUES (
                p_empresa_id, p_local_id, 'tolva', v_id,
                v_deuda, 0, p_fecha_inicio, 'abierto',
                'Deuda de tolva al instalar (porcentaje_local de la tolva fisica)'
            );
        END IF;
    END IF;

    -- Calendario: si se indicó, fíjalo en el local (p_local_id ya validado como
    -- de la empresa arriba). El operario NO se toca aquí: se asigna en la ficha
    -- de local (no es parte del alta de máquina).
    IF p_cadencia_semanas IS NOT NULL THEN
        UPDATE public.local SET
            cadencia_semanas         = p_cadencia_semanas,
            fecha_inicio_recaudacion = p_fecha_inicio_recaudacion,
            updated_at               = now()
        WHERE id = p_local_id;
    END IF;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text, numeric, uuid, smallint, date) IS
    'Alta de instalación. La base de contadores se hereda de la máquina (trigger). Si p_tolva > 0 crea la deuda de tolva del local (porcentaje_local × tolva). Con p_tolva_continua_credito_id traslada (re-apunta) una tolva existente. Con p_cadencia_semanas + p_fecha_inicio_recaudacion fija el calendario de recaudación del local (Planificación P1).';

REVOKE ALL    ON FUNCTION public.crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text, numeric, uuid, smallint, date) FROM PUBLIC, anon;
GRANT  EXECUTE ON FUNCTION public.crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text, numeric, uuid, smallint, date) TO authenticated;
