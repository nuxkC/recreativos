-- =============================================================================
-- Realtime universal: publica el resto de tablas operacionales para que CUALQUIER
-- pantalla de la app pueda refrescarse en vivo ante cambios del servidor.
--
-- 20260611200000 ya publicó las del baseline de recaudación (recaudacion,
-- cambio_placa, instalacion, maquina, recaudacion_lock, alerta). Faltaban las
-- que alimentan el resto de pantallas:
--   * local                 → home/agenda, gestión de locales, deudas.
--   * licencia              → gestión de licencias, instalaciones.
--   * credito_local         → saldos de deuda (v_credito_local_saldo).
--   * recuperacion          → ledger de deudas, saldos.
--   * averia, averia_recambio → historial de averías de una máquina.
--   * lectura_no_recaudada  → "atendido" de la agenda (v_agenda_operario).
--   * empresa_usuario       → cambios de rol/acceso (membresías).
--
-- Realtime NO emite sobre VISTAS: el cliente escucha estas tablas BASE y, según
-- el caso, dispara un re-sync (datos en Room) o un refetch directo (vistas como
-- v_agenda_operario / v_recaudacion_historica / v_credito_local_saldo). La RLS
-- (P2) sigue filtrando por JWT: cada técnico solo recibe filas de su empresa.
--
-- REPLICA IDENTITY FULL: Realtime necesita la fila ANTIGUA completa para aplicar
-- RLS/filtros en UPDATE/DELETE. Migración ADITIVA e idempotente (segura ante
-- `supabase db reset`).
-- =============================================================================

DO $$
DECLARE
    t      text;
    tablas text[] := ARRAY[
        'local',
        'licencia',
        'credito_local',
        'recuperacion',
        'averia',
        'averia_recambio',
        'lectura_no_recaudada',
        'empresa_usuario'
    ];
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        CREATE PUBLICATION supabase_realtime;
    END IF;

    FOREACH t IN ARRAY tablas LOOP
        IF NOT EXISTS (
            SELECT 1
              FROM pg_publication_tables
             WHERE pubname    = 'supabase_realtime'
               AND schemaname = 'public'
               AND tablename  = t
        ) THEN
            EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
        END IF;

        EXECUTE format('ALTER TABLE public.%I REPLICA IDENTITY FULL', t);
    END LOOP;
END $$;
