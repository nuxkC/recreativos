-- =============================================================================
-- T-12 — Tablas operativas y de auditoría:
--   recaudacion, cambio_placa, recaudacion_lock, lectura_no_recaudada, alerta.
--
-- Decisiones (ver .kiro/specs/recre/design.md §3.8-3.12, §5 y §8):
--   * `recaudacion` es inmutable: no se borra. Para corregir errores se
--     usa `estado = 'anulada'` con motivo y autor.
--   * `desglose_total` y `desglose_local` van como jsonb embebidos para
--     mantener la fila autocontenida; la validación de denominaciones
--     vive en la Edge Function `crear-recaudacion` (T-21) y queda
--     reforzada con un CHECK estructural mínimo aquí.
--   * `idempotency_key` único globalmente: la clave la genera el cliente
--     (UUID) y evita duplicados al sincronizar tras offline.
--   * `baseline_*` viaja con la recaudación para que el server pueda
--     detectar conflictos comparando contra la baseline real al persistir.
--   * Las anulaciones cambian la baseline para la SIGUIENTE recaudación,
--     que se calculará server-side ignorando las anuladas (T-13/T-21).
--   * `recaudacion_lock` con `expires_at` permite recuperarse de bloqueos
--     huérfanos automáticamente (TTL 30 min, ver constants.ts).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- recaudacion
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.recaudacion (
    id                              uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id                      uuid           NOT NULL REFERENCES public.empresa(id) ON DELETE RESTRICT,
    instalacion_id                  uuid           NOT NULL REFERENCES public.instalacion(id) ON DELETE RESTRICT,
    tecnico_id                      uuid           NOT NULL REFERENCES public.usuario(id) ON DELETE RESTRICT,
    fecha                           timestamptz    NOT NULL,

    -- Lecturas de contadores
    contador_entradas_anterior      bigint         NOT NULL CHECK (contador_entradas_anterior >= 0),
    contador_salidas_anterior       bigint         NOT NULL CHECK (contador_salidas_anterior >= 0),
    contador_entradas_actual        bigint         NOT NULL CHECK (contador_entradas_actual >= 0),
    contador_salidas_actual         bigint         NOT NULL CHECK (contador_salidas_actual >= 0),

    -- Snapshot del valor de crédito en el momento de la recaudación
    valor_credito_aplicado          numeric(4, 2)  NOT NULL CHECK (valor_credito_aplicado > 0),

    -- Importes calculados (verdad: la del server)
    recaudacion_bruta               numeric(10, 2) NOT NULL,
    semanas_aplicadas               integer        NOT NULL CHECK (semanas_aplicadas >= 0),
    tasa_semanal_aplicada           numeric(8, 2)  NOT NULL CHECK (tasa_semanal_aplicada >= 0),
    tasa_total_aplicada             numeric(10, 2) NOT NULL CHECK (tasa_total_aplicada >= 0),
    recaudacion_neta                numeric(10, 2) NOT NULL,
    porcentaje_local_aplicado       numeric(5, 2)  NOT NULL CHECK (porcentaje_local_aplicado BETWEEN 0 AND 100),
    parte_local                     numeric(10, 2) NOT NULL,
    parte_empresa                   numeric(10, 2) NOT NULL,

    -- Desglose físico de denominaciones (validación profunda en Edge Function)
    desglose_total                  jsonb          NOT NULL,
    desglose_local                  jsonb          NOT NULL,

    -- Evidencia
    firma_url                       text,
    foto_entradas_url               text,
    foto_salidas_url                text,
    ocr_entradas_valor              bigint,
    ocr_salidas_valor               bigint,
    pdf_url                         text,

    observaciones                   text,

    -- Trazabilidad de sincronización
    dispositivo_id                  text,
    idempotency_key                 text           NOT NULL,
    baseline_origen                 text           NOT NULL
        CHECK (baseline_origen IN ('recaudacion_anterior', 'cambio_placa', 'instalacion_base')),
    baseline_id                     uuid,

    -- Conflicto detectado por el server al persistir
    conflicto                       boolean        NOT NULL DEFAULT false,
    bruto_recalculado               numeric(10, 2),
    neto_recalculado                numeric(10, 2),
    parte_local_recalculada         numeric(10, 2),
    parte_empresa_recalculada       numeric(10, 2),
    revisado_por                    uuid           REFERENCES public.usuario(id) ON DELETE SET NULL,
    revisado_en                     timestamptz,
    resolucion                      text           CHECK (resolucion IN ('aceptada', 'sustituida', 'anulada')),
    resolucion_notas                text,

    -- Estado de la recaudación
    estado                          text           NOT NULL DEFAULT 'firme'
        CHECK (estado IN ('firme', 'anulada')),
    motivo_anulacion                text,
    anulada_por                     uuid           REFERENCES public.usuario(id) ON DELETE SET NULL,
    anulada_en                      timestamptz,

    created_at                      timestamptz    NOT NULL DEFAULT now(),
    updated_at                      timestamptz    NOT NULL DEFAULT now(),

    -- ----------------------------------------------------------------- Checks
    -- Idempotencia global para evitar duplicados al subir desde offline.
    CONSTRAINT uq_recaudacion_idempotency UNIQUE (idempotency_key),
    -- Coherencia mínima de cifras (la verdad la calcula el server).
    CONSTRAINT chk_recaudacion_neta
        CHECK (recaudacion_neta = recaudacion_bruta - tasa_total_aplicada),
    CONSTRAINT chk_recaudacion_partes
        CHECK (parte_local + parte_empresa = recaudacion_neta),
    CONSTRAINT chk_recaudacion_tasa_total
        CHECK (tasa_total_aplicada = tasa_semanal_aplicada * semanas_aplicadas),
    -- Si está anulada exigimos motivo y trazabilidad.
    CONSTRAINT chk_recaudacion_anulacion
        CHECK (
            (estado = 'firme' AND motivo_anulacion IS NULL AND anulada_en IS NULL AND anulada_por IS NULL)
            OR
            (estado = 'anulada' AND motivo_anulacion IS NOT NULL AND anulada_en IS NOT NULL)
        ),
    -- Estructura mínima del desglose: deben ser arrays jsonb.
    CONSTRAINT chk_desglose_total_array
        CHECK (jsonb_typeof(desglose_total) = 'array'),
    CONSTRAINT chk_desglose_local_array
        CHECK (jsonb_typeof(desglose_local) = 'array')
);

COMMENT ON TABLE public.recaudacion IS
    'Recaudación inmutable: ninguna se borra. Las correcciones se hacen vía estado=anulada con auditoría.';

COMMENT ON COLUMN public.recaudacion.idempotency_key IS
    'UUID generado en cliente. Garantiza que un reintento de subida no duplique la recaudación.';

COMMENT ON COLUMN public.recaudacion.baseline_origen IS
    'Origen de la baseline que vio el cliente: recaudacion_anterior | cambio_placa | instalacion_base.';

COMMENT ON COLUMN public.recaudacion.conflicto IS
    'TRUE cuando la baseline enviada por el cliente ya no coincide con la real al persistir.';

CREATE INDEX IF NOT EXISTS idx_recaudacion_instalacion_fecha
    ON public.recaudacion (instalacion_id, fecha DESC);

CREATE INDEX IF NOT EXISTS idx_recaudacion_empresa_fecha
    ON public.recaudacion (empresa_id, fecha DESC);

CREATE INDEX IF NOT EXISTS idx_recaudacion_conflicto_pendiente
    ON public.recaudacion (empresa_id)
    WHERE conflicto = true AND revisado_en IS NULL;

CREATE INDEX IF NOT EXISTS idx_recaudacion_tecnico
    ON public.recaudacion (tecnico_id, fecha DESC);

-- -----------------------------------------------------------------------------
-- cambio_placa (evento independiente entre recaudaciones)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.cambio_placa (
    id                              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id                      uuid        NOT NULL REFERENCES public.empresa(id) ON DELETE RESTRICT,
    instalacion_id                  uuid        NOT NULL REFERENCES public.instalacion(id) ON DELETE RESTRICT,
    fecha                           timestamptz NOT NULL,
    usuario_id                      uuid        NOT NULL REFERENCES public.usuario(id) ON DELETE RESTRICT,
    contador_entradas_nuevo         bigint      NOT NULL DEFAULT 0
        CHECK (contador_entradas_nuevo >= 0),
    contador_salidas_nuevo          bigint      NOT NULL DEFAULT 0
        CHECK (contador_salidas_nuevo >= 0),
    motivo                          text,
    numero_serie_placa_anterior     text,
    numero_serie_placa_nueva        text,
    foto_url                        text,
    notas                           text,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.cambio_placa IS
    'Cambios de placa registrados entre recaudaciones. Sirven de baseline para la siguiente recaudación.';

CREATE INDEX IF NOT EXISTS idx_cambio_placa_instalacion_fecha
    ON public.cambio_placa (instalacion_id, fecha DESC);

-- -----------------------------------------------------------------------------
-- recaudacion_lock (bloqueo optimista entre técnicos)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.recaudacion_lock (
    instalacion_id  uuid        PRIMARY KEY REFERENCES public.instalacion(id) ON DELETE CASCADE,
    tecnico_id      uuid        NOT NULL REFERENCES public.usuario(id) ON DELETE RESTRICT,
    dispositivo_id  text,
    started_at      timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL
);

COMMENT ON TABLE public.recaudacion_lock IS
    'Lock optimista para evitar dos técnicos recaudando la misma instalación a la vez. TTL 30 min.';

CREATE INDEX IF NOT EXISTS idx_recaudacion_lock_expires
    ON public.recaudacion_lock (expires_at);

-- -----------------------------------------------------------------------------
-- lectura_no_recaudada (log mínimo cuando bruto < tasa y se cierra sin recaudar)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.lectura_no_recaudada (
    id                          uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id                  uuid           NOT NULL REFERENCES public.empresa(id) ON DELETE RESTRICT,
    instalacion_id              uuid           NOT NULL REFERENCES public.instalacion(id) ON DELETE RESTRICT,
    tecnico_id                  uuid           NOT NULL REFERENCES public.usuario(id) ON DELETE RESTRICT,
    fecha                       timestamptz    NOT NULL,
    contador_entradas_actual    bigint         NOT NULL CHECK (contador_entradas_actual >= 0),
    contador_salidas_actual     bigint         NOT NULL CHECK (contador_salidas_actual >= 0),
    bruto_estimado              numeric(10, 2) NOT NULL,
    tasa_estimada               numeric(10, 2) NOT NULL CHECK (tasa_estimada >= 0),
    notas                       text,
    created_at                  timestamptz    NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.lectura_no_recaudada IS
    'Trazabilidad de visitas en las que el bruto era inferior a la tasa y se cerró sin recaudar.';

CREATE INDEX IF NOT EXISTS idx_lectura_no_recaudada_instalacion_fecha
    ON public.lectura_no_recaudada (instalacion_id, fecha DESC);

-- -----------------------------------------------------------------------------
-- alerta (panel de avisos para admins)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.alerta (
    id                          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id                  uuid        NOT NULL REFERENCES public.empresa(id) ON DELETE CASCADE,
    tipo                        text        NOT NULL
        CHECK (tipo IN (
            'recaudacion_conflicto',
            'licencia_caducidad',
            'local_sin_recaudar',
            'recaudacion_anulada',
            'otra'
        )),
    referencia_id               uuid,
    mensaje                     text        NOT NULL,
    destinatario_usuario_id     uuid        REFERENCES public.usuario(id) ON DELETE SET NULL,
    leida                       boolean     NOT NULL DEFAULT false,
    creada_en                   timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.alerta IS
    'Avisos consolidados (conflictos, caducidades, locales sin recaudar). Alimenta el dashboard y notificaciones.';

CREATE INDEX IF NOT EXISTS idx_alerta_empresa_no_leida
    ON public.alerta (empresa_id, creada_en DESC)
    WHERE leida = false;
