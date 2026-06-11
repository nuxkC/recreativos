-- =============================================================================
-- Lockdown de escritura directa sobre empresa, empresa_usuario y alerta.
--
-- Invariante: los clientes solo LEEN; toda escritura pasa por función:
--   * empresa         -> actualizar_ajustes_empresa (RPC). El alta/baja la hace
--                        registrar_empresa_con_owner (SECURITY DEFINER).
--   * empresa_usuario -> cambiar_rol_miembro / cambiar_estado_miembro (RPC);
--                        el alta de miembros, vía Edge `invitar-usuario`
--                        (service_role) y registrar_empresa_con_owner.
--   * alerta          -> marcar_alerta_leida / marcar_alertas_leidas_empresa
--                        (RPC); el INSERT y demás columnas, service_role/triggers.
--
-- Revocamos los privilegios de escritura y eliminamos las policies de
-- modificación (quedan muertas sin grant). La lectura sigue acotada por las
-- policies `*_select`. El trigger proteger_columnas_alerta se mantiene como
-- defensa adicional (las RPCs solo tocan `leida`, así que lo cumplen).
--
-- REQUISITO: aplicarla SOLO cuando los clientes ya escriben vía RPC (web
-- ajustes/equipo/dashboard actions.ts, android AlertasRemoteDataSource).
-- =============================================================================

REVOKE INSERT, UPDATE, DELETE ON public.empresa         FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.empresa_usuario FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.alerta          FROM authenticated, anon;

DROP POLICY IF EXISTS empresa_update          ON public.empresa;
DROP POLICY IF EXISTS empresa_usuario_modify  ON public.empresa_usuario;
DROP POLICY IF EXISTS alerta_update           ON public.alerta;
DROP POLICY IF EXISTS alerta_admin_delete     ON public.alerta;
