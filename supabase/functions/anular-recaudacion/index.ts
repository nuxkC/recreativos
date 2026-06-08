/**
 * T-26a — Edge Function `anular-recaudacion`.
 *
 * Anula una recaudación. La fila se conserva (no se borra), se marca con
 * estado='anulada', motivo y autor. La siguiente recaudación de la misma
 * instalación recalculará la baseline ignorando las anuladas (la función
 * SQL `obtener_baseline` ya filtra por estado='firme').
 *
 * Acceso: solo `owner` o `admin` de la empresa de la recaudación.
 */

import { ZodError } from "zod";

import { requireRolEnEmpresa, requireUser } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/db.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { AnularRecaudacionInputSchema } from "../_shared/schemas.ts";

const ROLES_ANULACION = ["owner", "admin"] as const;

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
    input = AnularRecaudacionInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase, userId } = await requireUser(req);

  const { data: rec, error: recError } = await supabase
    .from("recaudacion")
    .select("id, empresa_id, estado")
    .eq("id", input.recaudacion_id)
    .maybeSingle();

  if (recError) {
    throw makeError("internal_error", "Error consultando recaudación", recError.message);
  }
  if (!rec) {
    throw makeError("not_found", "Recaudación no encontrada o sin acceso");
  }
  if (rec.estado === "anulada") {
    throw makeError("conflict", "La recaudación ya está anulada");
  }

  await requireRolEnEmpresa(supabase, rec.empresa_id, ROLES_ANULACION);

  const { data: row, error: updError } = await supabase
    .from("recaudacion")
    .update({
      estado: "anulada",
      motivo_anulacion: input.motivo,
      anulada_por: userId,
      anulada_en: new Date().toISOString(),
    })
    .eq("id", input.recaudacion_id)
    .select()
    .single();

  if (updError) {
    throw makeError("internal_error", "No se pudo anular la recaudación", updError.message);
  }

  // Alerta para el equipo. El service_role bypasea la RLS de `alerta`
  // (que solo permite INSERT a service_role).
  const service = getServiceClient();
  await service.from("alerta").insert({
    empresa_id: rec.empresa_id,
    tipo: "recaudacion_anulada",
    referencia_id: rec.id,
    mensaje: `Recaudación anulada: ${input.motivo}`,
  });

  return jsonResponse(row);
}));
