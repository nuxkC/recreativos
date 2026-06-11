-- =============================================================================
-- Realtime para datos operativos del técnico: locks, alertas y las tablas que
-- determinan el baseline (contadores) de una recaudación.
--
-- Por qué: el técnico NO debe iniciar una recaudación con contadores viejos. El
-- baseline lo calcula el servidor (obtener_baseline / v_instalacion_actual) a
-- partir de `recaudacion`, `cambio_placa`, `instalacion` y `maquina`; cuando
-- cualquiera de ellas cambia, el cliente debe refrescar. Además el lock (T-58)
-- y las alertas se quieren ver en vivo.
--
-- Diseño:
--   * Se publican TABLAS BASE: Realtime no emite sobre vistas, así que no se
--     puede suscribir a `v_instalacion_actual`. El cliente, ante un cambio en
--     estas tablas, dispara un re-sync (recálculo server-side → SSOT) en lugar
--     de aplicar deltas; así nunca recalcula el baseline en local.
--   * Multi-tenant: postgres_changes aplica las policies RLS `*_select` ya
--     existentes con el JWT del técnico, de modo que solo recibe filas de su
--     empresa. No hacen falta policies nuevas (verificadas: recaudacion_select,
--     cambio_placa_select, recaudacion_lock_select, etc. usan
--     `usuario_pertenece_a_empresa`).
--   * REPLICA IDENTITY FULL: Realtime necesita la fila ANTIGUA completa para
--     evaluar RLS y los filtros por empresa_id en eventos UPDATE/DELETE. El
--     sobrecoste de WAL es despreciable en estas tablas de baja frecuencia.
--
-- Migración ADITIVA e idempotente (segura ante `supabase db reset`).
-- =============================================================================

DO $$
DECLARE
    t      text;
    tablas text[] := ARRAY[
        'recaudacion',
        'cambio_placa',
        'instalacion',
        'maquina',
        'recaudacion_lock',
        'alerta'
    ];
BEGIN
    -- La publicación `supabase_realtime` la crea Supabase por defecto; por si la
    -- migración corre sobre un Postgres pelado, la creamos vacía si falta.
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        CREATE PUBLICATION supabase_realtime;
    END IF;

    FOREACH t IN ARRAY tablas LOOP
        -- Añadir a la publicación solo si no está ya (idempotencia).
        IF NOT EXISTS (
            SELECT 1
              FROM pg_publication_tables
             WHERE pubname    = 'supabase_realtime'
               AND schemaname = 'public'
               AND tablename  = t
        ) THEN
            EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
        END IF;

        -- Fila antigua completa en UPDATE/DELETE (RLS + filtros en esos eventos).
        EXECUTE format('ALTER TABLE public.%I REPLICA IDENTITY FULL', t);
    END LOOP;
END $$;
