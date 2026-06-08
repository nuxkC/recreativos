/**
 * Factories de clientes Supabase para Edge Functions.
 *
 * - `getUserClient(req)`: cliente con el JWT del caller. Respeta RLS.
 *   Úsalo para SELECT/INSERT en nombre del usuario autenticado.
 *
 * - `getServiceClient()`: cliente con `service_role`. Bypassea RLS.
 *   Reservado para acciones que requieren permisos elevados:
 *     · subir tickets PDF al bucket `tickets` (policy de INSERT solo
 *       permite service_role)
 *     · insertar alertas
 *     · escribir como otra empresa en jobs cron
 *   La service_role key NUNCA viaja al cliente.
 */

import { createClient, type SupabaseClient } from "@supabase/supabase-js";

function requireEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) {
    throw new Error(`Falta variable de entorno ${name}`);
  }
  return value;
}

export function getUserClient(req: Request): SupabaseClient {
  const authHeader = req.headers.get("Authorization") ?? "";
  return createClient(requireEnv("SUPABASE_URL"), requireEnv("SUPABASE_ANON_KEY"), {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  });
}

export function getServiceClient(): SupabaseClient {
  return createClient(requireEnv("SUPABASE_URL"), requireEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  });
}
