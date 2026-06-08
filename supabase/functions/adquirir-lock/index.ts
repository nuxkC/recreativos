/**
 * T-24a — Edge Function `adquirir-lock`.
 *
 * Bloqueo optimista para evitar dos técnicos recaudando la misma instalación
 * simultáneamente. TTL configurado por `LOCK_TTL_MINUTES`.
 *
 * Reglas:
 *   * Si no hay lock o está expirado → tomamos el lock.
 *   * Si hay lock activo del mismo técnico → renovamos su TTL.
 *   * Si hay lock activo de OTRO técnico → devolvemos `lock_held` con
 *     información del titular, salvo que `forzar=true`.
 */

import { ZodError } from "zod";

import { requireRolEnEmpresa, requireUser } from "../_shared/auth.ts";
import { LOCK_TTL_MINUTES } from "../_shared/constants.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { AdquirirLockInputSchema } from "../_shared/schemas.ts";

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
    input = AdquirirLockInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase, userId } = await requireUser(req);

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

  // Lock existente (si lo hay).
  const { data: existing } = await supabase
    .from("recaudacion_lock")
    .select("instalacion_id, tecnico_id, dispositivo_id, started_at, expires_at")
    .eq("instalacion_id", input.instalacion_id)
    .maybeSingle();

  const ahora = new Date();
  const expiresAt = new Date(ahora.getTime() + LOCK_TTL_MINUTES * 60 * 1000);

  const lockActivo = existing && new Date(existing.expires_at) > ahora;
  const lockDeOtro = lockActivo && existing.tecnico_id !== userId;

  if (lockDeOtro && !input.forzar) {
    throw makeError(
      "lock_held",
      "Esta instalación está siendo recaudada por otro técnico",
      {
        tecnico_id: existing.tecnico_id,
        started_at: existing.started_at,
        expires_at: existing.expires_at,
      },
    );
  }

  // upsert (reemplaza el lock existente si lo había).
  const { data: row, error: upsertError } = await supabase
    .from("recaudacion_lock")
    .upsert({
      instalacion_id: input.instalacion_id,
      tecnico_id: userId,
      dispositivo_id: input.dispositivo_id ?? null,
      started_at: ahora.toISOString(),
      expires_at: expiresAt.toISOString(),
    }, { onConflict: "instalacion_id" })
    .select()
    .single();

  if (upsertError) {
    throw makeError("internal_error", "No se pudo adquirir el lock", upsertError.message);
  }

  return jsonResponse({ lock: row, sustituido: Boolean(lockDeOtro) });
}));
