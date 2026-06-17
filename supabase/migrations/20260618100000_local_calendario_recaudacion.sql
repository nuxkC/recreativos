-- =============================================================================
-- Planificación de recaudación — P1.1: calendario de recaudación por local.
--
-- Añade a `local` la configuración de "cada cuántas semanas y desde qué fecha"
-- se recauda, y qué operario lo lleva. Migración aditiva e inmutable. NO cambia
-- la lógica de cálculo (eso es P2/P3): aquí solo se PERSISTE la configuración.
--
--   * cadencia_semanas         smallint  > 0  — cada N semanas se recauda.
--   * fecha_inicio_recaudacion  date           — punto de partida del calendario.
--   * operario_id               uuid           — quién lleva el local (usuario).
--
-- Coherencia: cadencia y fecha van JUNTAS (ambas o ninguna). Sin las dos no hay
-- calendario derivable — el "¿toca?" de P3 necesita la fecha de inicio F y la
-- cadencia C para generar las fechas teóricas (F + k·C·7 días). El operario es
-- independiente: un local puede tener operario sin calendario y viceversa.
-- Spec: docs/superpowers/specs/2026-06-18-planificacion-recaudacion-design.md §4.1.
-- =============================================================================

ALTER TABLE public.local
    ADD COLUMN cadencia_semanas         smallint,
    ADD COLUMN fecha_inicio_recaudacion date,
    ADD COLUMN operario_id              uuid REFERENCES public.usuario(id) ON DELETE SET NULL;

-- La cadencia, si está fijada, es estrictamente positiva (cada >=1 semanas).
ALTER TABLE public.local
    ADD CONSTRAINT local_cadencia_positiva
        CHECK (cadencia_semanas IS NULL OR cadencia_semanas > 0);

-- Calendario coherente: o se fijan cadencia + fecha, o ninguna. Una sola no
-- describe un calendario.
ALTER TABLE public.local
    ADD CONSTRAINT local_calendario_coherente
        CHECK ((cadencia_semanas IS NULL) = (fecha_inicio_recaudacion IS NULL));

COMMENT ON COLUMN public.local.cadencia_semanas IS
    'Cada cuántas semanas se recauda este local (>0). NULL = sin planificar. Va de la mano con fecha_inicio_recaudacion (constraint local_calendario_coherente).';
COMMENT ON COLUMN public.local.fecha_inicio_recaudacion IS
    'Fecha de partida del calendario; las fechas teóricas son F + k·cadencia·7 días. NULL = sin planificar.';
COMMENT ON COLUMN public.local.operario_id IS
    'Operario (miembro operativo de la empresa) que lleva este local. NULL = sin asignar. Independiente del calendario. ON DELETE SET NULL: borrar el usuario deja el local sin asignar, no bloquea.';

-- Índice parcial: las consultas de "rutas por operario" (P1 vista operarios, P3
-- agenda) filtran local WHERE operario_id = X; los NULL (sin asignar) no se
-- buscan por operario, así que el índice los excluye.
CREATE INDEX idx_local_operario ON public.local (operario_id)
    WHERE operario_id IS NOT NULL;
