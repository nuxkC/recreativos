/**
 * T-22 — Edge Function `crear-cambio-placa`.
 *
 * Registra el cambio de placa de una máquina. La siguiente recaudación
 * tomará como baseline los contadores nuevos. No genera ticket ni reparto.
 *
 * Acceso: usuarios con rol operativo (owner/admin/gestor/tecnico) sobre la
 * empresa de la instalación.
 */

import { ZodError } from "zod";

import { requireRolEnEmpresa, requireUser } from "../_shared/auth.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { CrearCambioPlacaInputSchema } from "../_shared/schemas.ts";
import { decodeBase64Image, uploadToBucket } from "../_shared/storage.ts";

const ROLES_OPERATIVO = ["owner", "admin", "gestor", "tecnico"] as const;

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
    input = CrearCambioPlacaInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase, userId } = await requireUser(req);

  // Resolver empresa_id desde la instalación
  const { data: inst, error: instError } = await supabase
    .from("instalacion")
    .select("id, empresa_id, estado")
    .eq("id", input.instalacion_id)
    .maybeSingle();

  if (instError) {
    throw makeError("internal_error", "Error consultando instalación", instError.message);
  }
  if (!inst) {
    throw makeError("not_found", "Instalación no encontrada o sin acceso");
  }
  if (inst.estado !== "activa") {
    throw makeError("validation_error", "La instalación no está activa");
  }

  await requireRolEnEmpresa(supabase, inst.empresa_id, ROLES_OPERATIVO);

  const cambioId = crypto.randomUUID();

  // Subida opcional de foto
  let fotoUrl: string | null = null;
  if (input.foto_base64) {
    const { bytes, mime } = decodeBase64Image(input.foto_base64);
    const path = `${inst.empresa_id}/${cambioId}.${mime === "image/png" ? "png" : "jpg"}`;
    await uploadToBucket(supabase, "cambios-placa", path, bytes, mime);
    fotoUrl = path;
  }

  const { data: row, error: insertError } = await supabase
    .from("cambio_placa")
    .insert({
      id: cambioId,
      empresa_id: inst.empresa_id,
      instalacion_id: input.instalacion_id,
      fecha: input.fecha,
      usuario_id: userId,
      contador_entradas_nuevo: input.contador_entradas_nuevo,
      contador_salidas_nuevo: input.contador_salidas_nuevo,
      motivo: input.motivo ?? null,
      numero_serie_placa_anterior: input.numero_serie_placa_anterior ?? null,
      numero_serie_placa_nueva: input.numero_serie_placa_nueva ?? null,
      foto_url: fotoUrl,
      notas: input.notas ?? null,
    })
    .select()
    .single();

  if (insertError) {
    throw makeError(
      "internal_error",
      "No se pudo registrar el cambio de placa",
      insertError.message,
    );
  }

  return jsonResponse(row, 201);
}));
