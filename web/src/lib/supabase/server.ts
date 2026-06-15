import { createServerClient, type CookieOptions } from "@supabase/ssr";
import { cookies } from "next/headers";

import { env } from "@/lib/env";

/**
 * Cliente de Supabase para Server Components, Route Handlers y Server Actions.
 *
 * Lee y escribe la cookie de sesión a través de `next/headers`. En contextos
 * donde no se permite escribir (p. ej. Server Components puros), las llamadas
 * a `setAll` se silencian; el middleware se encarga de mantener la cookie viva.
 */
export function createClient() {
  const cookieStore = cookies();

  return createServerClient(env.SUPABASE_URL, env.SUPABASE_ANON_KEY, {
    cookies: {
      getAll() {
        return cookieStore.getAll();
      },
      setAll(cookiesToSet: { name: string; value: string; options: CookieOptions }[]) {
        try {
          cookiesToSet.forEach(({ name, value, options }) => {
            cookieStore.set(name, value, options);
          });
        } catch {
          // Llamada desde un Server Component: no se puede escribir cookies.
          // El middleware las refrescará en la siguiente request.
        }
      },
    },
  });
}
