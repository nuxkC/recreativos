-- =============================================================================
-- T-102 — Resumen mensual por email al titular del local.
--
-- Aporta dos cosas, ambas ADITIVAS (no tocan migraciones aplicadas):
--   1. Vista `v_recaudaciones_por_local_maquina_mes`: agregado por
--      (empresa, local, máquina, mes) en la zona horaria de la empresa. La
--      Edge Function `resumen-mensual` la consume para construir el email; el
--      cálculo monetario queda centralizado en SQL con `numeric` (nunca float).
--      Las sumas se exponen como `text` para que PostgREST no las degrade a
--      float en el cliente.
--   2. Programación del cron mensual (pg_cron + pg_net) que invoca la función.
--      Se hace de forma DEFENSIVA: si las extensiones o los secretos del Vault
--      no están disponibles (p. ej. en local con `supabase db reset`), se omite
--      con un NOTICE sin abortar la migración. En ese caso, prográmala desde el
--      dashboard (ver bloque comentado al final).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Vista agregada por (empresa, local, máquina, mes local).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW public.v_recaudaciones_por_local_maquina_mes
WITH (security_invoker = true) AS
SELECT
    r.empresa_id,
    i.local_id,
    i.maquina_id,
    date_trunc('month', (r.fecha AT TIME ZONE e.zona_horaria)) AS mes_local,
    count(*)                            AS num_recaudaciones,
    sum(r.recaudacion_bruta)::text      AS bruto_total,
    sum(r.tasa_total_aplicada)::text    AS tasa_total,
    sum(r.recaudacion_neta)::text       AS neto_total,
    sum(r.parte_local)::text            AS parte_local_total,
    sum(r.parte_empresa)::text          AS parte_empresa_total
FROM public.recaudacion r
JOIN public.instalacion i ON i.id = r.instalacion_id
JOIN public.empresa e ON e.id = r.empresa_id
WHERE r.estado = 'firme'
GROUP BY r.empresa_id, i.local_id, i.maquina_id,
         date_trunc('month', (r.fecha AT TIME ZONE e.zona_horaria));

COMMENT ON VIEW public.v_recaudaciones_por_local_maquina_mes IS
    'Suma de recaudaciones firme por (empresa, local, maquina, mes) en zona horaria de la empresa. Alimenta el resumen mensual (T-102).';

-- -----------------------------------------------------------------------------
-- Programación del cron mensual (defensiva).
--
-- Día 1 de cada mes a las 06:00 (hora del servidor) -> resume el mes anterior.
-- Los secretos se leen del Vault EN TIEMPO DE EJECUCIÓN del job (no se
-- almacenan en cron.job): crea previamente los secretos `project_url` y
-- `service_role_key` en el Vault del proyecto.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_has_cron boolean;
    v_has_net  boolean;
    v_has_vault boolean;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_cron') INTO v_has_cron;
    SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_net') INTO v_has_net;

    IF NOT (v_has_cron AND v_has_net) THEN
        RAISE NOTICE 'T-102: pg_cron/pg_net no disponibles; programa "resumen-mensual" desde el dashboard.';
        RETURN;
    END IF;

    CREATE EXTENSION IF NOT EXISTS pg_cron;
    CREATE EXTENSION IF NOT EXISTS pg_net;

    -- ¿Existen los secretos del Vault? Si no, no programamos (evita un job que
    -- fallaría cada mes por falta de URL/clave).
    SELECT EXISTS (
        SELECT 1 FROM vault.decrypted_secrets WHERE name IN ('project_url', 'service_role_key')
        GROUP BY 1 HAVING count(DISTINCT name) = 2
    ) INTO v_has_vault;

    IF NOT COALESCE(v_has_vault, false) THEN
        RAISE NOTICE 'T-102: faltan secretos Vault project_url/service_role_key; programa "resumen-mensual" tras crearlos.';
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'resumen-mensual') THEN
        PERFORM cron.unschedule('resumen-mensual');
    END IF;

    PERFORM cron.schedule(
        'resumen-mensual',
        '0 6 1 * *',
        $cron$
        SELECT net.http_post(
            url := (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'project_url')
                   || '/functions/v1/resumen-mensual',
            headers := jsonb_build_object(
                'Content-Type', 'application/json',
                'Authorization', 'Bearer ' || (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'service_role_key')
            ),
            body := '{}'::jsonb
        );
        $cron$
    );

    RAISE NOTICE 'T-102: cron "resumen-mensual" programado (día 1 de cada mes, 06:00).';
EXCEPTION WHEN OTHERS THEN
    -- Nunca abortamos la migración por el scheduling: es opcional y
    -- reconfigurable desde el dashboard.
    RAISE NOTICE 'T-102: no se pudo programar el cron automáticamente (%). Prográmalo desde el dashboard.', SQLERRM;
END $$;

-- -----------------------------------------------------------------------------
-- Alternativa manual (dashboard) si el cron no se programó automáticamente:
--
--   1. Database > Extensions: habilita `pg_cron` y `pg_net`.
--   2. Crea los secretos en el Vault:
--        select vault.create_secret('https://<project-ref>.supabase.co', 'project_url');
--        select vault.create_secret('<SERVICE_ROLE_KEY>', 'service_role_key');
--   3. Programa el job (SQL Editor):
--        select cron.schedule(
--          'resumen-mensual', '0 6 1 * *',
--          $$ select net.http_post(
--               url := (select decrypted_secret from vault.decrypted_secrets where name='project_url') || '/functions/v1/resumen-mensual',
--               headers := jsonb_build_object('Content-Type','application/json',
--                 'Authorization','Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name='service_role_key')),
--               body := '{}'::jsonb) $$);
--
-- Para un mes concreto, invoca la función con body { "mes": "YYYY-MM" }.
-- =============================================================================
