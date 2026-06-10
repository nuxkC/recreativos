/**
 * T-24b — Edge Function `liberar-lock`.
 *
 * Libera el lock optimista de una instalación. Solo el técnico dueño del
 * lock (o un admin) puede liberarlo. La RLS sobre `recaudacion_lock`
 * (`tecnico_id = auth.uid() OR usuario_es_admin(...)`) impone esta regla
 * a nivel de DELETE; aquí simplemente intentamos borrar y reportamos.
 */

import { ZodError } from "zod";

import { requireUser } from "../_shared/auth.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { LiberarLockInputSchema } from "../_shared/schemas.ts";

Deno.serve(withHandler(async (req: Request) => {
  if (req.method !== "POST") {
    throw makeError("validation_error", "Solo se admite POST");
  }

  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    throw makeError("validation_error", "Body no es JSON válido");
  }

  let input;
  try {
    input = LiberarLockInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase } = await requireUser(req);

  const { data, error } = await supabase
    .from("recaudacion_lock")
    .delete()
    .eq("instalacion_id", input.instalacion_id)
    .select();

  if (error) {
    throw makeError("internal_error", "No se pudo liberar el lock", error.message);
  }

  // RLS puede haber filtrado el DELETE. Si data está vacío, no había lock
  // o el caller no era su dueño: en ambos casos no es un fallo, devolvemos
  // released=false para que el cliente lo sepa.
  return jsonResponse({ released: Array.isArray(data) && data.length > 0 });
}));
