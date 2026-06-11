-- =============================================================================
-- Cierre del lockdown de escritura: tablas restantes (usuario, device_token,
-- audit_log, resumen_mensual_envio).
--
-- Con esta migración se completa el invariante del repo: NINGÚN cliente
-- (`authenticated`/`anon`) tiene INSERT/UPDATE/DELETE sobre tablas de dominio;
-- solo SELECT. Toda escritura pasa por función (RPC SECURITY DEFINER o Edge
-- Function service_role).
--
-- Escritores legítimos de estas tablas (siguen funcionando tras el REVOKE):
--   * usuario              -> registrar_empresa_con_owner (SECURITY DEFINER),
--                             invitar-usuario (service_role). No hay edición de
--                             perfil self-service; si se añade, irá por RPC.
--   * device_token         -> registrar-device-token (service_role).
--   * audit_log            -> registrar_auditoria (SECURITY DEFINER) y triggers.
--   * resumen_mensual_envio -> job `resumen-mensual` (service_role).
--
-- La lectura sigue acotada por las policies `*_select` existentes.
-- =============================================================================

REVOKE INSERT, UPDATE, DELETE ON public.usuario               FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.device_token          FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.audit_log             FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.resumen_mensual_envio FROM authenticated, anon;

-- usuario: perfil self-service deja de poder escribirse directamente.
DROP POLICY IF EXISTS usuario_insert_self ON public.usuario;
DROP POLICY IF EXISTS usuario_update_self ON public.usuario;

-- device_token: alta/baja del token va por la Edge Function.
DROP POLICY IF EXISTS device_token_insert ON public.device_token;
DROP POLICY IF EXISTS device_token_update ON public.device_token;
DROP POLICY IF EXISTS device_token_delete ON public.device_token;

-- audit_log y resumen_mensual_envio no tenían policies de escritura (RLS ya las
-- bloqueaba); el REVOKE retira además el privilegio de tabla por defecto de
-- Supabase, para que el guardarraíl pase y la defensa no dependa solo de RLS.
