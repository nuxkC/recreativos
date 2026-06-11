-- =============================================================================
-- Lockdown de escritura directa sobre el inventario (licencia, maquina, local).
--
-- Invariante: los clientes solo LEEN; toda escritura pasa por las RPCs
-- crear/actualizar/eliminar_<tabla> (20260611140000). Revocamos los privilegios
-- de escritura y eliminamos las policies `*_modify` (quedan muertas sin grant).
-- La lectura sigue acotada por las policies `*_select` (aislamiento por tenant).
--
-- REQUISITO: aplicarla SOLO cuando los clientes ya escriben vía RPC (web
-- licencias/locales/maquinas actions.ts, android *RemoteDataSource); si no, el
-- alta/edición/borrado de inventario dejarían de funcionar.
-- =============================================================================

REVOKE INSERT, UPDATE, DELETE ON public.licencia FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.maquina  FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.local    FROM authenticated, anon;

DROP POLICY IF EXISTS licencia_modify ON public.licencia;
DROP POLICY IF EXISTS maquina_modify  ON public.maquina;
DROP POLICY IF EXISTS local_modify    ON public.local;
