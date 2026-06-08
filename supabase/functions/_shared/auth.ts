/**
 * Helpers de autenticación para Edge Functions.
 *
 * Todas las funciones que requieren usuario autenticado deben llamar a
 * `requireUser(req)` lo antes posible para fallar rápido.
 */

import type { SupabaseClient } from "@supabase/supabase-js";

import { getUserClient } from "./db.ts";
import { makeError } from "./errors.ts";
import type { Rol } from "./types.ts";

export interface AuthenticatedContext {
  /** Cliente Supabase con el JWT del caller (respeta RLS). */
  supabase: SupabaseClient;
  /** UUID del usuario autenticado (auth.uid()). */
  userId: string;
}

/**
 * Extrae el usuario del header Authorization. Falla con `auth_required` si
 * no hay JWT válido.
 */
export async function requireUser(req: Request): Promise<AuthenticatedContext> {
  const supabase = getUserClient(req);
  const { data, error } = await supabase.auth.getUser();
  if (error || !data.user) {
    throw makeError("auth_required", "Se requiere autenticación", error?.message);
  }
  return { supabase, userId: data.user.id };
}

/**
 * Comprueba que el usuario actual pertenece a la empresa indicada y tiene
 * alguno de los roles requeridos. Devuelve el rol concreto.
 *
 * Implementado contra `empresa_usuario` directamente para no depender de
 * funciones SQL que requieran SECURITY DEFINER.
 */
export async function requireRolEnEmpresa(
  supabase: SupabaseClient,
  empresaId: string,
  rolesPermitidos: readonly Rol[],
): Promise<Rol> {
  const { data, error } = await supabase
    .from("empresa_usuario")
    .select("rol")
    .eq("empresa_id", empresaId)
    .eq("activo", true)
    .maybeSingle();

  if (error) {
    throw makeError("internal_error", "No se pudo comprobar el rol", error.message);
  }
  if (!data) {
    throw makeError("forbidden", "No perteneces a esta empresa");
  }
  const rol = data.rol as Rol;
  if (!rolesPermitidos.includes(rol)) {
    throw makeError(
      "forbidden",
      `Tu rol (${rol}) no permite esta operación`,
      { rolesPermitidos },
    );
  }
  return rol;
}
