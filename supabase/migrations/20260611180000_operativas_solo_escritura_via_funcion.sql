-- =============================================================================
-- Lockdown de escritura directa sobre las tablas operativas: recaudacion,
-- cambio_placa, recaudacion_lock, lectura_no_recaudada.
--
-- Invariante: los clientes solo LEEN; toda escritura pasa por una Edge Function
-- con service_role (que valida rol+tenant en TS y puentea RLS):
--   * recaudacion        -> crear-recaudacion (INSERT), anular-recaudacion y
--                           resolver-conflicto (UPDATE). DELETE: nadie.
--   * cambio_placa       -> crear-cambio-placa (INSERT).
--   * recaudacion_lock   -> adquirir-lock (UPSERT), liberar-lock y
--                           cerrar-instalacion (DELETE).
--   * lectura_no_recaudada -> sin escritor actual; si se implementa, irá por
--                           Edge Function service_role (nunca INSERT directo).
--
-- Las Edge Functions ya migraron sus escrituras a service_role; aquí revocamos
-- los privilegios de escritura directa y eliminamos las policies de escritura.
-- La lectura sigue acotada por las policies `*_select`.
--
-- REQUISITO: aplicarla SOLO con las Edge Functions ya desplegadas usando
-- service_role para escribir estas tablas.
-- =============================================================================

REVOKE INSERT, UPDATE, DELETE ON public.recaudacion          FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.cambio_placa         FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.recaudacion_lock     FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.lectura_no_recaudada FROM authenticated, anon;

-- recaudacion
DROP POLICY IF EXISTS recaudacion_insert       ON public.recaudacion;
DROP POLICY IF EXISTS recaudacion_admin_update ON public.recaudacion;

-- cambio_placa
DROP POLICY IF EXISTS cambio_placa_insert       ON public.cambio_placa;
DROP POLICY IF EXISTS cambio_placa_admin_modify ON public.cambio_placa;
DROP POLICY IF EXISTS cambio_placa_admin_delete ON public.cambio_placa;

-- recaudacion_lock
DROP POLICY IF EXISTS recaudacion_lock_insert ON public.recaudacion_lock;
DROP POLICY IF EXISTS recaudacion_lock_update ON public.recaudacion_lock;
DROP POLICY IF EXISTS recaudacion_lock_delete ON public.recaudacion_lock;

-- lectura_no_recaudada
DROP POLICY IF EXISTS lectura_no_recaudada_insert       ON public.lectura_no_recaudada;
DROP POLICY IF EXISTS lectura_no_recaudada_admin_modify ON public.lectura_no_recaudada;
