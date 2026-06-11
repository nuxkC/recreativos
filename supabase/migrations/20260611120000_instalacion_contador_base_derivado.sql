-- =============================================================================
-- Contador base de instalación DERIVADO de la máquina (no se teclea) + RPCs de
-- escritura SECURITY DEFINER para instalación.
--
-- Migración ADITIVA. Rectifica un fallo de diseño: el alta de instalación pedía
-- `contador_*_base` a mano. El contador físico de una máquina es MONÓTONO y
-- CONTINUO durante toda su vida (solo se resetea en un cambio de placa), así que
-- introducir una base manual permitía teclear un valor MENOR que la última
-- lectura ya recaudada de esa máquina (p. ej. al moverla a otro local) → la
-- siguiente recaudación cobraría jugadas ya liquidadas. Cobro inflado.
--
-- A partir de ahora la base se HEREDA de la máquina:
--   1. Última recaudación FIRME de cualquier instalación de la máquina.
--   2. Último cambio de placa de cualquier instalación de la máquina.
--   3. Si no hay historial → contadores iniciales de la máquina.
-- (Mismo desempate que `obtener_baseline`: empate → gana cambio_placa.)
--
-- El cálculo es SERVER-SIDE (SSOT): lo fija un trigger BEFORE INSERT, de modo
-- que ningún cliente —ni un INSERT directo— puede inyectar una base errónea.
--
-- Además, alineado con el invariante "toda escritura pasa por función", se
-- añaden las RPCs `crear/actualizar/eliminar_instalacion` (SECURITY DEFINER,
-- validan rol+tenant). El REVOKE de la escritura directa sobre `instalacion`
-- se hará en una migración posterior, junto al rewire de los clientes, para no
-- romper la app en este paso.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Derivación del contador actual de una máquina (a una fecha de corte).
--    Reutiliza el tipo `baseline_info`. `origen` ∈
--    {'recaudacion_anterior','cambio_placa','maquina_inicial'}.
--    SECURITY DEFINER + sin EXECUTE para clientes: solo la usan el trigger y las
--    RPCs (que corren como owner). Así no se filtran lecturas cross-tenant vía
--    rpc() directo.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.obtener_contador_actual_maquina(
    p_maquina_id uuid,
    p_fecha      timestamptz
) RETURNS public.baseline_info
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_result public.baseline_info;
    v_maq    public.maquina%ROWTYPE;
    v_rec    public.recaudacion%ROWTYPE;
    v_cp     public.cambio_placa%ROWTYPE;
BEGIN
    SELECT * INTO v_maq FROM public.maquina WHERE id = p_maquina_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'maquina no encontrada: %', p_maquina_id
            USING ERRCODE = 'no_data_found';
    END IF;

    -- Última recaudación FIRME de CUALQUIER instalación de esta máquina.
    SELECT r.*
      INTO v_rec
      FROM public.recaudacion r
      JOIN public.instalacion i ON i.id = r.instalacion_id
     WHERE i.maquina_id = p_maquina_id
       AND r.fecha < p_fecha
       AND r.estado = 'firme'
     ORDER BY r.fecha DESC, r.created_at DESC, r.id DESC
     LIMIT 1;

    -- Último cambio de placa de CUALQUIER instalación de esta máquina.
    SELECT cp.*
      INTO v_cp
      FROM public.cambio_placa cp
      JOIN public.instalacion i ON i.id = cp.instalacion_id
     WHERE i.maquina_id = p_maquina_id
       AND cp.fecha < p_fecha
     ORDER BY cp.fecha DESC, cp.created_at DESC, cp.id DESC
     LIMIT 1;

    -- Empate -> gana cambio_placa (resetea la máquina).
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
        v_result.entradas         := v_maq.contador_entradas_inicial;
        v_result.salidas          := v_maq.contador_salidas_inicial;
        v_result.fecha_referencia := v_maq.created_at;
        v_result.origen           := 'maquina_inicial';
        v_result.referencia_id    := v_maq.id;
    END IF;

    RETURN v_result;
END;
$$;

COMMENT ON FUNCTION public.obtener_contador_actual_maquina(uuid, timestamptz) IS
    'Contador acumulado de una máquina a una fecha de corte (última recaudación firme / cambio de placa / contadores iniciales). Hereda la base de una instalación nueva.';

REVOKE ALL ON FUNCTION public.obtener_contador_actual_maquina(uuid, timestamptz)
    FROM PUBLIC, anon, authenticated;

-- -----------------------------------------------------------------------------
-- 2. Trigger BEFORE INSERT: la base de la instalación se HEREDA de la máquina.
--    Sobrescribe siempre lo que venga del cliente. Como es BEFORE INSERT, el
--    NOT NULL de las columnas se evalúa DESPUÉS, así que un INSERT que omita la
--    base es válido (el trigger la rellena).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.set_contador_base_instalacion()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_tz       text;
    v_fecha    timestamptz;
    v_contador public.baseline_info;
BEGIN
    SELECT COALESCE(zona_horaria, 'Europe/Madrid') INTO v_tz
      FROM public.empresa WHERE id = NEW.empresa_id;
    v_tz := COALESCE(v_tz, 'Europe/Madrid');

    -- Medianoche de la fecha de inicio EN LA TZ DE LA EMPRESA (igual criterio
    -- que obtener_baseline para el origen 'instalacion_base').
    v_fecha := (NEW.fecha_inicio::timestamp AT TIME ZONE v_tz);

    v_contador := public.obtener_contador_actual_maquina(NEW.maquina_id, v_fecha);

    NEW.contador_entradas_base := v_contador.entradas;
    NEW.contador_salidas_base  := v_contador.salidas;

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION public.set_contador_base_instalacion() IS
    'Rellena instalacion.contador_*_base con la lectura heredada de la máquina. La base nunca se teclea.';

REVOKE ALL ON FUNCTION public.set_contador_base_instalacion()
    FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_set_contador_base_instalacion ON public.instalacion;
CREATE TRIGGER trg_set_contador_base_instalacion
    BEFORE INSERT ON public.instalacion
    FOR EACH ROW
    EXECUTE FUNCTION public.set_contador_base_instalacion();

-- -----------------------------------------------------------------------------
-- 3. RPCs de escritura de instalación (capa de escritura vía función).
--    SECURITY DEFINER: puentean RLS, por eso validan rol+tenant a mano.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.crear_instalacion(
    p_empresa_id       uuid,
    p_maquina_id       uuid,
    p_licencia_id      uuid,
    p_local_id         uuid,
    p_fecha_inicio     date,
    p_tasa_semanal     numeric,
    p_porcentaje_local numeric,
    p_notas            text DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id uuid;
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

    -- contador_*_base se omiten a propósito: los rellena el trigger
    -- trg_set_contador_base_instalacion heredándolos de la máquina.
    INSERT INTO public.instalacion (
        empresa_id, maquina_id, licencia_id, local_id,
        fecha_inicio, tasa_semanal, porcentaje_local,
        estado, notas
    ) VALUES (
        p_empresa_id, p_maquina_id, p_licencia_id, p_local_id,
        p_fecha_inicio, p_tasa_semanal, p_porcentaje_local,
        'activa', p_notas
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text) IS
    'Alta de instalación. La base de contadores se hereda de la máquina (no es parámetro).';

CREATE OR REPLACE FUNCTION public.actualizar_instalacion(
    p_id               uuid,
    p_fecha_inicio     date,
    p_tasa_semanal     numeric,
    p_porcentaje_local numeric,
    p_notas            text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.instalacion WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'instalacion no encontrada: %', p_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar instalaciones'
            USING ERRCODE = '42501';
    END IF;

    -- La base de contadores es INMUTABLE tras el alta (se heredó de la máquina).
    -- No se reescribe aquí aunque cambie fecha_inicio.
    UPDATE public.instalacion
       SET fecha_inicio     = p_fecha_inicio,
           tasa_semanal     = p_tasa_semanal,
           porcentaje_local = p_porcentaje_local,
           notas            = p_notas,
           updated_at       = now()
     WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.actualizar_instalacion(uuid, date, numeric, numeric, text) IS
    'Edita condiciones de una instalación. No toca la base de contadores (inmutable).';

CREATE OR REPLACE FUNCTION public.eliminar_instalacion(p_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.instalacion WHERE id = p_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'instalacion no encontrada: %', p_id
            USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar instalaciones'
            USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.instalacion WHERE id = p_id;
END;
$$;

COMMENT ON FUNCTION public.eliminar_instalacion(uuid) IS
    'Borra una instalación. Valida rol gestor + tenant.';

-- Solo `authenticated` (clientes autenticados) ejecuta las RPCs de escritura.
REVOKE ALL ON FUNCTION public.crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.actualizar_instalacion(uuid, date, numeric, numeric, text)                 FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.eliminar_instalacion(uuid)                                                 FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.actualizar_instalacion(uuid, date, numeric, numeric, text)              TO authenticated;
GRANT EXECUTE ON FUNCTION public.eliminar_instalacion(uuid)                                              TO authenticated;
