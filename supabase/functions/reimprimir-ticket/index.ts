/**
 * T-27b — Edge Function `reimprimir-ticket`.
 *
 * Devuelve una signed URL del PDF de archivo de una recaudación. La RLS
 * sobre `recaudacion` ya impide ver recaudaciones de otras empresas, así
 * que basta con que el caller pueda hacer SELECT.
 */

import { ZodError } from "zod";

import { requireUser } from "../_shared/auth.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { ReimprimirTicketInputSchema } from "../_shared/schemas.ts";
import { createSignedUrl } from "../_shared/storage.ts";

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
    input = ReimprimirTicketInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase } = await requireUser(req);

  const { data: rec, error } = await supabase
    .from("recaudacion")
    .select("id, pdf_url")
    .eq("id", input.recaudacion_id)
    .maybeSingle();

  if (error) {
    throw makeError("internal_error", "Error consultando recaudación", error.message);
  }
  if (!rec) {
    throw makeError("not_found", "Recaudación no encontrada o sin acceso");
  }
  if (!rec.pdf_url) {
    throw makeError("not_found", "Esta recaudación no tiene PDF archivado");
  }

  const url = await createSignedUrl(supabase, "tickets", rec.pdf_url);
  return jsonResponse({ pdf_signed_url: url });
}));
