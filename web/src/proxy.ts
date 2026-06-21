import type { NextRequest } from "next/server";

import { updateSession } from "@/lib/supabase/middleware";

// Next 16 renombró la convención `middleware` a `proxy` (mismo punto de
// ejecución por delante del render). La lógica real —refresco de sesión
// Supabase SSR + guard de rutas— sigue en `lib/supabase/middleware`.
// `proxy` corre en runtime nodejs (no edge), compatible con @supabase/ssr.
export async function proxy(request: NextRequest) {
  return updateSession(request);
}

export const config = {
  matcher: [
    // Todas las rutas excepto los assets estáticos e imágenes optimizadas.
    "/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)",
  ],
};
