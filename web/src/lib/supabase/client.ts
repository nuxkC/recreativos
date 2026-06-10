"use client";

import { createBrowserClient } from "@supabase/ssr";

import { env } from "@/lib/env";

/**
 * Cliente de Supabase para el navegador.
 * Comparte sesión con la cookie gestionada por el middleware.
 */
export function createClient() {
  return createBrowserClient(env.SUPABASE_URL, env.SUPABASE_ANON_KEY);
}
