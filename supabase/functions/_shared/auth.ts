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
 * Resuelve la membresía con `empresas_del_usuario_actual()` (SECURITY DEFINER,
 * filtra por `auth.uid()` server-side). NO se consulta `empresa_usuario`
 * directamente con `.maybeSingle()`: la policy de SELECT deja ver a TODOS los
 * compañeros de empresa, así que con 2+ miembros activos `.maybeSingle()`
 * recibía varias filas y reventaba la comprobación de rol.
 */
export async function requireRolEnEmpresa(
  supabase: SupabaseClient,
  empresaId: string,
  rolesPermitidos: readonly Rol[],
): Promise<Rol> {
  const { data, error } = await supabase.rpc("empresas_del_usuario_actual");
  if (error) {
    throw makeError("internal_error", "No se pudo comprobar el rol", error.message);
  }
  const membresias = (data ?? []) as Array<{ empresa_id: string; rol: Rol }>;
  const membresia = membresias.find((m) => m.empresa_id === empresaId);
  if (!membresia) {
    throw makeError("forbidden", "No perteneces a esta empresa");
  }
  const rol = membresia.rol as Rol;
  if (!rolesPermitidos.includes(rol)) {
    throw makeError(
      "forbidden",
      `Tu rol (${rol}) no permite esta operación`,
      { rolesPermitidos },
    );
  }
  return rol;
}

/**
 * Exige que la petición venga autenticada con la `service_role` key. Para
 * funciones que SOLO debe invocar otro servicio (cron, otra Edge Function),
 * nunca un cliente con el anon key público. Sin esto, cualquiera con el anon
 * key (que viaja en la app) podría dispararlas con un id arbitrario.
 */
export function requireServiceRole(req: Request): void {
  const header = req.headers.get("Authorization") ?? "";
  const token = header.replace(/^Bearer\s+/i, "").trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!serviceKey || token !== serviceKey) {
    throw makeError("forbidden", "Esta función solo puede invocarse internamente");
  }
}
