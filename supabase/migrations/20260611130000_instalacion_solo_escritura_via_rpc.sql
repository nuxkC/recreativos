-- =============================================================================
-- Lockdown de escritura directa sobre `instalacion`.
--
-- Invariante del repo: los clientes (`authenticated`/`anon`) NUNCA escriben las
-- tablas directamente; solo SELECT. Toda escritura pasa por una función:
--   * RPC SECURITY DEFINER  -> crear/actualizar/eliminar_instalacion
--     (ver 20260611120000_instalacion_contador_base_derivado.sql)
--   * Edge Function service_role -> cierre (`cerrar-instalacion`) y boletín
--     (`generar-boletin-instalacion`), que validan rol+tenant en TS.
--
-- Esta migración cierra el agujero: revoca los privilegios de escritura y
-- elimina la policy `instalacion_modify` (queda muerta sin privilegio). La
-- lectura sigue acotada por `instalacion_select` (aislamiento por tenant).
--
-- REQUISITO: aplicarla SOLO cuando los clientes ya escriben vía RPC (Android
-- `InstalacionesRemoteDataSource`, web `lib/instalaciones/actions.ts`) y el
-- cierre usa service_role; si no, el alta/edición/cierre dejarían de funcionar.
-- =============================================================================

REVOKE INSERT, UPDATE, DELETE ON public.instalacion FROM authenticated, anon;

DROP POLICY IF EXISTS instalacion_modify ON public.instalacion;
