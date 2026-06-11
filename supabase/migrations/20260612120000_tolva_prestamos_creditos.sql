-- =============================================================================
-- T-212 — Tolva y préstamos: deudas del local con recuperación (modelo backend).
--
-- Contexto de negocio (ver design.md §3.14 y §5.5):
--   * TOLVA: dinero físico que la empresa deja dentro de una máquina al
--     instalarla para que pueda empezar a pagar premios. Es informativo (saber
--     cuánto capital tiene la empresa "en la calle"), PERO además el local debe
--     a la empresa una fracción de esa tolva = `porcentaje_local` de la misma
--     (sigue el reparto real de la instalación, no un 50% fijo).
--   * PRÉSTAMO: dinero que la empresa presta a un local. Sin interés por defecto
--     (`tipo_interes` se guarda como dato, no se devenga en v1). Sin límite de
--     préstamos por local.
--
-- Ambos son DEUDAS del local con la empresa y se recuperan igual:
--   (a) en efectivo (el dueño paga), o
--   (b) reteniendo un % de su `parte_local` en cada recaudación —la
--       "recuperación automática"— que es T-214. Aquí solo se deja el MODELO
--       (créditos + libro mayor) y la configuración del % (empresa + override
--       por local). El cálculo SSOT no se toca en esta migración.
--
-- INVARIANTE CLAVE (traslado de tolva): la deuda de tolva PERTENECE AL LOCAL,
-- no a la instalación. `credito_local.instalacion_id` es solo un puntero
-- re-apuntable ("dónde está físicamente la tolva ahora"). Al cambiar de máquina
-- (= cerrar instalación + abrir otra) la MISMA tolva se traslada: se re-apunta
-- el crédito existente a la nueva instalación en vez de crear otra deuda,
-- evitando el doble cómputo. En esta tarea se dejan ESQUEMA + HOOK
-- (`p_tolva_continua_credito_id`) + GUARDARRAÍL; el flujo/UX completo de
-- traslado (y el ajuste del principal por delta) es trabajo futuro.
--
-- Escritura: SOLO vía función (invariante del repo). Las tablas nuevas tienen
-- RLS de solo lectura; toda mutación pasa por las RPCs SECURITY DEFINER de abajo
-- o, en T-214, por la Edge Function de recaudación con service_role.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Columnas nuevas.
-- -----------------------------------------------------------------------------

-- instalacion.tolva: dinero físico dejado en la máquina (informativo).
ALTER TABLE public.instalacion
    ADD COLUMN IF NOT EXISTS tolva numeric(10, 2) NOT NULL DEFAULT 0
        CHECK (tolva >= 0);

COMMENT ON COLUMN public.instalacion.tolva IS
    'Dinero físico (€) dejado en la máquina al instalarla para que pueda pagar premios. Informativo: capital de la empresa en esta máquina. La DEUDA del local por la tolva se modela aparte en credito_local.';

-- empresa.porcentaje_recuperacion: % por defecto de la parte_local que se
-- retiene en cada recaudación para amortizar deuda (0 = sin recuperación auto).
ALTER TABLE public.empresa
    ADD COLUMN IF NOT EXISTS porcentaje_recuperacion smallint NOT NULL DEFAULT 0
        CHECK (porcentaje_recuperacion BETWEEN 0 AND 100);

COMMENT ON COLUMN public.empresa.porcentaje_recuperacion IS
    'Porcentaje por defecto de la parte_local que se retiene en cada recaudación para amortizar deudas (tolva/préstamo) del local. 0 = sin recuperación automática. Un local puede sobreescribirlo con local.porcentaje_recuperacion.';

-- local.porcentaje_recuperacion: override por local (NULL = usa el de empresa).
ALTER TABLE public.local
    ADD COLUMN IF NOT EXISTS porcentaje_recuperacion smallint
        CHECK (porcentaje_recuperacion BETWEEN 0 AND 100);

COMMENT ON COLUMN public.local.porcentaje_recuperacion IS
    'Override del porcentaje de recuperación de este local. NULL = hereda empresa.porcentaje_recuperacion (regla COALESCE local, empresa).';

-- -----------------------------------------------------------------------------
-- 2. credito_local: deuda del local con la empresa (tolva o préstamo).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.credito_local (
    id             uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id     uuid          NOT NULL REFERENCES public.empresa(id) ON DELETE RESTRICT,
    local_id       uuid          NOT NULL REFERENCES public.local(id)   ON DELETE RESTRICT,
    tipo           text          NOT NULL CHECK (tipo IN ('tolva', 'prestamo')),

    -- Puntero re-apuntable a la instalación donde está la tolva AHORA. Solo
    -- para tipo='tolva' (un préstamo no cuelga de ninguna máquina). ON DELETE
    -- SET NULL: si se borra la instalación, la deuda permanece con el local.
    instalacion_id uuid          REFERENCES public.instalacion(id) ON DELETE SET NULL,

    -- Importe originado de la deuda. Tolva: round(porcentaje_local × tolva).
    -- Préstamo: lo prestado. El saldo vivo se deriva en v_credito_local_saldo.
    principal      numeric(10, 2) NOT NULL CHECK (principal > 0),
    tipo_interes   numeric(5, 2)  NOT NULL DEFAULT 0 CHECK (tipo_interes >= 0),

    fecha          date          NOT NULL,
    estado         text          NOT NULL DEFAULT 'abierto'
        CHECK (estado IN ('abierto', 'saldado', 'condonado')),
    notas          text,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    -- Un préstamo nunca apunta a una instalación; una tolva normalmente sí
    -- (puede quedar a NULL si la máquina se retira y la deuda sigue viva).
    CONSTRAINT chk_credito_prestamo_sin_instalacion
        CHECK (tipo = 'tolva' OR instalacion_id IS NULL)
);

COMMENT ON TABLE public.credito_local IS
    'Deudas del local con la empresa: tolva (fracción de la tolva física) o préstamo. La deuda pertenece al local; instalacion_id es un puntero re-apuntable para la tolva. El saldo vivo se calcula en v_credito_local_saldo.';
COMMENT ON COLUMN public.credito_local.instalacion_id IS
    'Tolva: instalación donde está físicamente la tolva ahora (re-apuntable al cambiar de máquina, para no duplicar la deuda). Préstamo: siempre NULL.';
COMMENT ON COLUMN public.credito_local.principal IS
    'Importe originado de la deuda. Tolva = round(porcentaje_local × tolva / 100, 2). Préstamo = lo prestado.';
COMMENT ON COLUMN public.credito_local.tipo_interes IS
    'Tipo de interés del préstamo (%). Se guarda como dato; no se devenga en v1 (default 0).';

CREATE INDEX IF NOT EXISTS idx_credito_local_local_estado
    ON public.credito_local (local_id, estado);
CREATE INDEX IF NOT EXISTS idx_credito_local_empresa
    ON public.credito_local (empresa_id);

-- credito_local lleva updated_at: enganchamos el trigger genérico set_updated_at.
DROP TRIGGER IF EXISTS trg_credito_local_updated_at ON public.credito_local;
CREATE TRIGGER trg_credito_local_updated_at
    BEFORE UPDATE ON public.credito_local
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- -----------------------------------------------------------------------------
-- 3. recuperacion: libro mayor de abonos a una deuda (efectivo o recaudación).
--    Append-only (sin updated_at). Da la trazabilidad de "cuánto se quitó, de
--    dónde y en qué recaudaciones".
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.recuperacion (
    id             uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id     uuid          NOT NULL REFERENCES public.empresa(id) ON DELETE RESTRICT,
    local_id       uuid          NOT NULL REFERENCES public.local(id)   ON DELETE RESTRICT,
    credito_id     uuid          NOT NULL REFERENCES public.credito_local(id) ON DELETE RESTRICT,
    origen         text          NOT NULL CHECK (origen IN ('efectivo', 'recaudacion')),
    importe        numeric(10, 2) NOT NULL CHECK (importe > 0),

    -- De qué recaudación se retuvo (trazabilidad). Obligatorio si
    -- origen='recaudacion'; NULL si fue un pago en efectivo.
    recaudacion_id uuid          REFERENCES public.recaudacion(id) ON DELETE RESTRICT,
    fecha          timestamptz   NOT NULL DEFAULT now(),
    usuario_id     uuid          REFERENCES public.usuario(id) ON DELETE SET NULL,
    notas          text,
    created_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT chk_recuperacion_origen_recaudacion
        CHECK (
            (origen = 'recaudacion' AND recaudacion_id IS NOT NULL)
            OR (origen = 'efectivo' AND recaudacion_id IS NULL)
        )
);

COMMENT ON TABLE public.recuperacion IS
    'Abonos a una deuda (credito_local): en efectivo o reteniendo de una recaudación. Append-only. Da la trazabilidad de qué recaudación amortizó qué deuda.';

CREATE INDEX IF NOT EXISTS idx_recuperacion_credito
    ON public.recuperacion (credito_id);
CREATE INDEX IF NOT EXISTS idx_recuperacion_local
    ON public.recuperacion (local_id);
CREATE INDEX IF NOT EXISTS idx_recuperacion_recaudacion
    ON public.recuperacion (recaudacion_id) WHERE recaudacion_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 4. Vistas de saldo (security_invoker: heredan la RLS del que consulta, igual
--    que el resto de vistas del repo).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW public.v_credito_local_saldo
WITH (security_invoker = true) AS
SELECT
    c.id             AS credito_id,
    c.empresa_id,
    c.local_id,
    c.tipo,
    c.instalacion_id,
    c.principal,
    c.tipo_interes,
    c.fecha,
    c.estado,
    c.notas,
    COALESCE(SUM(r.importe), 0)               AS recuperado,
    c.principal - COALESCE(SUM(r.importe), 0) AS saldo
FROM public.credito_local c
LEFT JOIN public.recuperacion r ON r.credito_id = c.id
GROUP BY c.id;

COMMENT ON VIEW public.v_credito_local_saldo IS
    'Cada deuda con su importe recuperado y saldo vivo (principal − Σ recuperaciones).';

-- Saldo de deuda agregado por local (solo deudas ABIERTAS). Alimenta el libro
-- mayor del local y la tarjeta "capital en la calle".
CREATE OR REPLACE VIEW public.v_local_saldo
WITH (security_invoker = true) AS
SELECT
    l.id        AS local_id,
    l.empresa_id,
    COALESCE(SUM(s.saldo), 0)                                    AS saldo_total,
    COALESCE(SUM(s.saldo) FILTER (WHERE s.tipo = 'tolva'), 0)    AS saldo_tolva,
    COALESCE(SUM(s.saldo) FILTER (WHERE s.tipo = 'prestamo'), 0) AS saldo_prestamo,
    COALESCE(SUM(s.principal), 0)                               AS principal_total,
    COALESCE(SUM(s.recuperado), 0)                              AS recuperado_total,
    COUNT(s.credito_id)                                         AS num_deudas_abiertas
FROM public.local l
LEFT JOIN public.v_credito_local_saldo s
       ON s.local_id = l.id AND s.estado = 'abierto'
GROUP BY l.id;

COMMENT ON VIEW public.v_local_saldo IS
    'Saldo de deuda agregado por local (solo deudas abiertas), desglosado en tolva/préstamo. Alimenta el libro mayor y el "capital en la calle".';

-- -----------------------------------------------------------------------------
-- 5. RPCs de escritura (SECURITY DEFINER; puentean RLS, validan rol+tenant).
-- -----------------------------------------------------------------------------

-- 5.1 Alta de préstamo a un local.
CREATE OR REPLACE FUNCTION public.crear_prestamo(
    p_empresa_id   uuid,
    p_local_id     uuid,
    p_principal    numeric,
    p_tipo_interes numeric DEFAULT 0,
    p_fecha        date    DEFAULT NULL,
    p_notas        text    DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_id    uuid;
    v_tz    text;
    v_fecha date;
BEGIN
    IF NOT public.usuario_es_gestor(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar deudas del local'
            USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.local WHERE id = p_local_id AND empresa_id = p_empresa_id) THEN
        RAISE EXCEPTION 'local % no pertenece a la empresa %', p_local_id, p_empresa_id
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    IF p_principal IS NULL OR p_principal <= 0 THEN
        RAISE EXCEPTION 'el principal del préstamo debe ser > 0'
            USING ERRCODE = '22023';
    END IF;
    IF COALESCE(p_tipo_interes, 0) < 0 THEN
        RAISE EXCEPTION 'el tipo de interés no puede ser negativo'
            USING ERRCODE = '22023';
    END IF;

    SELECT COALESCE(zona_horaria, 'Europe/Madrid') INTO v_tz FROM public.empresa WHERE id = p_empresa_id;
    v_fecha := COALESCE(p_fecha, (now() AT TIME ZONE COALESCE(v_tz, 'Europe/Madrid'))::date);

    INSERT INTO public.credito_local (
        empresa_id, local_id, tipo, instalacion_id,
        principal, tipo_interes, fecha, estado, notas
    ) VALUES (
        p_empresa_id, p_local_id, 'prestamo', NULL,
        round(p_principal, 2), COALESCE(p_tipo_interes, 0), v_fecha, 'abierto', p_notas
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_prestamo(uuid, uuid, numeric, numeric, date, text) IS
    'Alta de préstamo a un local. Valida rol gestor + tenant. Devuelve el id de la deuda.';

-- 5.2 Abono en efectivo a una deuda (recuperación manual).
CREATE OR REPLACE FUNCTION public.registrar_recuperacion_efectivo(
    p_credito_id uuid,
    p_importe    numeric,
    p_notas      text DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_cred  public.credito_local%ROWTYPE;
    v_saldo numeric(10, 2);
    v_imp   numeric(10, 2);
    v_id    uuid;
BEGIN
    SELECT * INTO v_cred FROM public.credito_local WHERE id = p_credito_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'deuda no encontrada: %', p_credito_id USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_cred.empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para registrar abonos'
            USING ERRCODE = '42501';
    END IF;
    IF v_cred.estado <> 'abierto' THEN
        RAISE EXCEPTION 'la deuda % no está abierta (estado %)', p_credito_id, v_cred.estado
            USING ERRCODE = '22023';
    END IF;
    IF p_importe IS NULL OR p_importe <= 0 THEN
        RAISE EXCEPTION 'el importe del abono debe ser > 0' USING ERRCODE = '22023';
    END IF;

    v_imp := round(p_importe, 2);
    SELECT saldo INTO v_saldo FROM public.v_credito_local_saldo WHERE credito_id = p_credito_id;
    IF v_imp > v_saldo THEN
        RAISE EXCEPTION 'el abono (%) supera el saldo vivo (%)', v_imp, v_saldo
            USING ERRCODE = '23514';
    END IF;

    INSERT INTO public.recuperacion (
        empresa_id, local_id, credito_id, origen, importe, recaudacion_id, usuario_id, notas
    ) VALUES (
        v_cred.empresa_id, v_cred.local_id, p_credito_id, 'efectivo', v_imp, NULL, auth.uid(), p_notas
    )
    RETURNING id INTO v_id;

    -- Saldar la deuda si el abono la deja a cero.
    IF v_saldo - v_imp <= 0 THEN
        UPDATE public.credito_local SET estado = 'saldado' WHERE id = p_credito_id;
    END IF;

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.registrar_recuperacion_efectivo(uuid, numeric, text) IS
    'Registra un abono en efectivo a una deuda. Valida rol gestor + tenant, que la deuda esté abierta y que el importe no supere el saldo. Salda la deuda si llega a cero.';

-- 5.3 Condonar (perdonar) una deuda. Acción sensible: requiere admin.
CREATE OR REPLACE FUNCTION public.condonar_credito(
    p_credito_id uuid,
    p_notas      text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
    v_estado     text;
BEGIN
    SELECT empresa_id, estado INTO v_empresa_id, v_estado
      FROM public.credito_local WHERE id = p_credito_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'deuda no encontrada: %', p_credito_id USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_admin(v_empresa_id) THEN
        RAISE EXCEPTION 'solo un administrador puede condonar deudas'
            USING ERRCODE = '42501';
    END IF;
    IF v_estado <> 'abierto' THEN
        RAISE EXCEPTION 'la deuda % no está abierta (estado %)', p_credito_id, v_estado
            USING ERRCODE = '22023';
    END IF;

    UPDATE public.credito_local
       SET estado = 'condonado',
           notas  = COALESCE(p_notas, notas)
     WHERE id = p_credito_id;
END;
$$;

COMMENT ON FUNCTION public.condonar_credito(uuid, text) IS
    'Condona (perdona) una deuda abierta. Acción sensible: requiere rol admin. El saldo restante deja de contar en v_local_saldo.';

-- 5.4 Override del % de recuperación de un local. NULL = vuelve a heredar el de
--     la empresa. RPC dedicada (no se mete en actualizar_local) precisamente
--     para poder distinguir "poner NULL" de "no tocar".
CREATE OR REPLACE FUNCTION public.set_porcentaje_recuperacion_local(
    p_local_id   uuid,
    p_porcentaje smallint
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
    v_empresa_id uuid;
BEGIN
    SELECT empresa_id INTO v_empresa_id FROM public.local WHERE id = p_local_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'local no encontrado: %', p_local_id USING ERRCODE = 'no_data_found';
    END IF;
    IF NOT public.usuario_es_gestor(v_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para gestionar locales'
            USING ERRCODE = '42501';
    END IF;
    IF p_porcentaje IS NOT NULL AND (p_porcentaje < 0 OR p_porcentaje > 100) THEN
        RAISE EXCEPTION 'porcentaje_recuperacion fuera de rango [0,100]: %', p_porcentaje
            USING ERRCODE = '22023';
    END IF;

    UPDATE public.local SET porcentaje_recuperacion = p_porcentaje WHERE id = p_local_id;
END;
$$;

COMMENT ON FUNCTION public.set_porcentaje_recuperacion_local(uuid, smallint) IS
    'Fija (o pone a NULL = heredar empresa) el override del % de recuperación de un local. Valida rol gestor + tenant.';

-- 5.5 crear_instalacion: ahora acepta la TOLVA y crea/traslada su deuda.
--     Se recrea la firma (añade p_tolva y p_tolva_continua_credito_id, ambos con
--     DEFAULT para no romper a los clientes que aún llaman con 8 args).
DROP FUNCTION IF EXISTS public.crear_instalacion(
    uuid, uuid, uuid, uuid, date, numeric, numeric, text);

CREATE OR REPLACE FUNCTION public.crear_instalacion(
    p_empresa_id                uuid,
    p_maquina_id                uuid,
    p_licencia_id               uuid,
    p_local_id                  uuid,
    p_fecha_inicio              date,
    p_tasa_semanal              numeric,
    p_porcentaje_local          numeric,
    p_notas                     text    DEFAULT NULL,
    p_tolva                     numeric DEFAULT 0,
    p_tolva_continua_credito_id uuid    DEFAULT NULL
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

    RETURN v_id;
END;
$$;

COMMENT ON FUNCTION public.crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text, numeric, uuid) IS
    'Alta de instalación. La base de contadores se hereda de la máquina (trigger). Si p_tolva > 0 crea la deuda de tolva del local (porcentaje_local × tolva). Con p_tolva_continua_credito_id traslada (re-apunta) una tolva existente al cambiar de máquina, sin duplicar la deuda.';

-- 5.6 actualizar_ajustes_empresa: añade `p_porcentaje_recuperacion`. Se recrea
--     la firma (no se puede REPLACE cambiándola). El nuevo parámetro lleva
--     DEFAULT NULL + COALESCE para que un cliente que aún no lo envíe conserve
--     el valor actual en lugar de resetearlo.
DROP FUNCTION IF EXISTS public.actualizar_ajustes_empresa(
    uuid, text, text, text, text, text, text, text, text, smallint);

CREATE OR REPLACE FUNCTION public.actualizar_ajustes_empresa(
    p_empresa_id              uuid,
    p_nombre                  text,
    p_cif                     text,
    p_direccion               text,
    p_telefono                text,
    p_email                   text,
    p_zona_horaria            text,
    p_ticket_cabecera         text,
    p_ticket_pie              text,
    p_redondeo_recaudacion    smallint DEFAULT NULL,
    p_porcentaje_recuperacion smallint DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
BEGIN
    IF NOT public.usuario_es_admin(p_empresa_id) THEN
        RAISE EXCEPTION 'sin permiso para editar la empresa'
            USING ERRCODE = '42501';
    END IF;

    IF p_redondeo_recaudacion IS NOT NULL AND p_redondeo_recaudacion < 0 THEN
        RAISE EXCEPTION 'redondeo_recaudacion no puede ser negativo'
            USING ERRCODE = '22023';
    END IF;
    IF p_porcentaje_recuperacion IS NOT NULL
       AND (p_porcentaje_recuperacion < 0 OR p_porcentaje_recuperacion > 100) THEN
        RAISE EXCEPTION 'porcentaje_recuperacion fuera de rango [0,100]: %', p_porcentaje_recuperacion
            USING ERRCODE = '22023';
    END IF;

    UPDATE public.empresa SET
        nombre                  = p_nombre,
        cif                     = p_cif,
        direccion               = p_direccion,
        telefono                = p_telefono,
        email                   = p_email,
        zona_horaria            = p_zona_horaria,
        ticket_cabecera         = p_ticket_cabecera,
        ticket_pie              = p_ticket_pie,
        redondeo_recaudacion    = COALESCE(p_redondeo_recaudacion, redondeo_recaudacion),
        porcentaje_recuperacion = COALESCE(p_porcentaje_recuperacion, porcentaje_recuperacion)
    WHERE id = p_empresa_id;
END;
$$;

COMMENT ON FUNCTION public.actualizar_ajustes_empresa(
    uuid, text, text, text, text, text, text, text, text, smallint, smallint) IS
    'Edita los ajustes editables de la empresa (incluye redondeo y % de recuperación por defecto). Valida rol admin + tenant.';

-- -----------------------------------------------------------------------------
-- 6. RLS de las tablas nuevas: solo lectura para clientes; escritura solo vía
--    las funciones de arriba (o Edge service_role en T-214).
-- -----------------------------------------------------------------------------
ALTER TABLE public.credito_local ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recuperacion  ENABLE ROW LEVEL SECURITY;

CREATE POLICY credito_local_select ON public.credito_local
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));
CREATE POLICY recuperacion_select ON public.recuperacion
    FOR SELECT USING (public.usuario_pertenece_a_empresa(empresa_id));

GRANT SELECT ON public.credito_local, public.recuperacion TO authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.credito_local FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.recuperacion  FROM authenticated, anon;

-- -----------------------------------------------------------------------------
-- 7. Permisos de las RPCs: solo `authenticated` ejecuta; nunca `anon`.
-- -----------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.crear_prestamo(uuid, uuid, numeric, numeric, date, text)                                    FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.registrar_recuperacion_efectivo(uuid, numeric, text)                                        FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.condonar_credito(uuid, text)                                                                FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.set_porcentaje_recuperacion_local(uuid, smallint)                                           FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text, numeric, uuid)      FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.actualizar_ajustes_empresa(uuid, text, text, text, text, text, text, text, text, smallint, smallint) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.crear_prestamo(uuid, uuid, numeric, numeric, date, text)                                    TO authenticated;
GRANT EXECUTE ON FUNCTION public.registrar_recuperacion_efectivo(uuid, numeric, text)                                        TO authenticated;
GRANT EXECUTE ON FUNCTION public.condonar_credito(uuid, text)                                                                TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_porcentaje_recuperacion_local(uuid, smallint)                                           TO authenticated;
GRANT EXECUTE ON FUNCTION public.crear_instalacion(uuid, uuid, uuid, uuid, date, numeric, numeric, text, numeric, uuid)      TO authenticated;
GRANT EXECUTE ON FUNCTION public.actualizar_ajustes_empresa(uuid, text, text, text, text, text, text, text, text, smallint, smallint) TO authenticated;
