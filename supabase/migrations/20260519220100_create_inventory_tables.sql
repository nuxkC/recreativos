-- =============================================================================
-- T-11 — Tablas de inventario: licencia, maquina, local, instalacion.
--
-- Decisiones (ver .kiro/specs/recre/design.md §3.4-3.7):
--   * Todas las tablas llevan empresa_id con DELETE RESTRICT: la empresa no
--     puede borrarse mientras tenga inventario asociado (forzamos cierre/baja
--     explícita).
--   * UNIQUE (empresa_id, numero) en `licencia` y UNIQUE (empresa_id,
--     numero_serie) en `maquina`: el número solo es único dentro de cada
--     tenant.
--   * Índices únicos PARCIALES sobre `instalacion`: una máquina y una
--     licencia solo pueden estar en una instalación `activa` a la vez. Las
--     instalaciones cerradas se conservan para histórico.
--   * `valor_credito` con CHECK > 0: típicamente 0.10 o 0.20 €.
--   * `tasa_semanal` >= 0; la tasa fiscal se introduce por instalación en T-05
--     y se aplica semanalmente.
--   * `porcentaje_local` 0..100: porcentaje del NETO que se queda el local.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- licencia
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.licencia (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id          uuid        NOT NULL REFERENCES public.empresa(id) ON DELETE RESTRICT,
    numero              text        NOT NULL,
    tipo                text,
    fecha_expedicion    date,
    fecha_caducidad     date,
    comunidad_autonoma  text,
    estado              text        NOT NULL DEFAULT 'activa'
        CHECK (estado IN ('activa', 'suspendida', 'caducada', 'baja')),
    notas               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, numero)
);

COMMENT ON TABLE public.licencia IS
    'Licencias oficiales de explotación. Una licencia se asocia a UNA instalación activa a la vez.';

CREATE INDEX IF NOT EXISTS idx_licencia_empresa_estado
    ON public.licencia (empresa_id, estado);

CREATE INDEX IF NOT EXISTS idx_licencia_caducidad
    ON public.licencia (fecha_caducidad)
    WHERE estado = 'activa' AND fecha_caducidad IS NOT NULL;

-- -----------------------------------------------------------------------------
-- maquina
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.maquina (
    id                          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id                  uuid          NOT NULL REFERENCES public.empresa(id) ON DELETE RESTRICT,
    numero_serie                text          NOT NULL,
    modelo                      text,
    fabricante                  text,
    valor_credito               numeric(4, 2) NOT NULL CHECK (valor_credito > 0),
    contador_entradas_inicial   bigint        NOT NULL DEFAULT 0 CHECK (contador_entradas_inicial >= 0),
    contador_salidas_inicial    bigint        NOT NULL DEFAULT 0 CHECK (contador_salidas_inicial >= 0),
    estado                      text          NOT NULL DEFAULT 'almacen'
        CHECK (estado IN ('almacen', 'instalada', 'averiada', 'baja')),
    notas                       text,
    created_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, numero_serie)
);

COMMENT ON TABLE public.maquina IS
    'Máquinas recreativas del inventario. Cada máquina pertenece a una sola empresa.';

COMMENT ON COLUMN public.maquina.valor_credito IS
    'Valor en € de cada crédito de la máquina (típicamente 0,10 o 0,20).';

COMMENT ON COLUMN public.maquina.contador_entradas_inicial IS
    'Lectura del contador de entradas al dar de alta la máquina (0 si es nueva).';

COMMENT ON COLUMN public.maquina.contador_salidas_inicial IS
    'Lectura del contador de salidas al dar de alta la máquina (0 si es nueva).';

CREATE INDEX IF NOT EXISTS idx_maquina_empresa_estado
    ON public.maquina (empresa_id, estado);

-- -----------------------------------------------------------------------------
-- local
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.local (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id      uuid        NOT NULL REFERENCES public.empresa(id) ON DELETE RESTRICT,
    nombre          text        NOT NULL,
    direccion       text,
    cif_o_nif       text,
    titular_nombre  text,
    telefono        text,
    email           text,
    notas           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.local IS
    'Establecimientos donde se instalan las máquinas (bares, salones, etc.). Un local puede tener varias máquinas.';

CREATE INDEX IF NOT EXISTS idx_local_empresa
    ON public.local (empresa_id);

-- -----------------------------------------------------------------------------
-- instalacion (asociación máquina + licencia + local + condiciones económicas)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.instalacion (
    id                       uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id               uuid          NOT NULL REFERENCES public.empresa(id) ON DELETE RESTRICT,
    maquina_id               uuid          NOT NULL REFERENCES public.maquina(id) ON DELETE RESTRICT,
    licencia_id              uuid          NOT NULL REFERENCES public.licencia(id) ON DELETE RESTRICT,
    local_id                 uuid          NOT NULL REFERENCES public.local(id) ON DELETE RESTRICT,
    fecha_inicio             date          NOT NULL,
    fecha_fin                date,
    tasa_semanal             numeric(8, 2) NOT NULL CHECK (tasa_semanal >= 0),
    porcentaje_local         numeric(5, 2) NOT NULL CHECK (porcentaje_local BETWEEN 0 AND 100),
    contador_entradas_base   bigint        NOT NULL CHECK (contador_entradas_base >= 0),
    contador_salidas_base    bigint        NOT NULL CHECK (contador_salidas_base >= 0),
    estado                   text          NOT NULL DEFAULT 'activa'
        CHECK (estado IN ('activa', 'cerrada')),
    notas                    text,
    created_at               timestamptz   NOT NULL DEFAULT now(),
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    -- Coherencia entre fechas y entre tenants.
    CONSTRAINT chk_instalacion_fechas
        CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    CONSTRAINT chk_instalacion_estado_fecha_fin
        CHECK (
            (estado = 'activa'  AND fecha_fin IS NULL)
            OR (estado = 'cerrada' AND fecha_fin IS NOT NULL)
        )
);

COMMENT ON TABLE public.instalacion IS
    'Asociación máquina–licencia–local con sus condiciones económicas. Una máquina y una licencia solo pueden estar en una instalación activa a la vez.';

COMMENT ON COLUMN public.instalacion.tasa_semanal IS
    'Importe en € de tasa fiscal aplicable cada semana ISO de calendario.';

COMMENT ON COLUMN public.instalacion.porcentaje_local IS
    'Porcentaje del importe NETO (bruto - tasa) que se queda el titular del local.';

-- Una máquina solo puede estar en una instalación activa.
CREATE UNIQUE INDEX IF NOT EXISTS uq_instalacion_maquina_activa
    ON public.instalacion (maquina_id)
    WHERE estado = 'activa';

-- Una licencia solo puede estar en una instalación activa.
CREATE UNIQUE INDEX IF NOT EXISTS uq_instalacion_licencia_activa
    ON public.instalacion (licencia_id)
    WHERE estado = 'activa';

-- Búsquedas frecuentes en la app del técnico.
CREATE INDEX IF NOT EXISTS idx_instalacion_local_estado
    ON public.instalacion (local_id, estado);

CREATE INDEX IF NOT EXISTS idx_instalacion_empresa_estado
    ON public.instalacion (empresa_id, estado);

-- Defensa multi-tenant: empresa_id de los FK debe coincidir con el de la
-- instalación. Lo verificaremos a nivel aplicación / Edge Function (T-21);
-- Postgres no permite FK compuestos cruzados sin denormalizar empresa_id en
-- todas las tablas relacionadas.
