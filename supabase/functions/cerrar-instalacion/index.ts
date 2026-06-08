/**
 * T-23 — Edge Function `cerrar-instalacion`.
 *
 * Cierra una instalación (estado='cerrada') con fecha_fin. Las recaudaciones
 * y cambios de placa históricos se conservan. La máquina y la licencia
 * quedan libres para nuevas instalaciones.
 *
 * Acceso: gestor, admin u owner de la empresa de la instalación.
 */

import { ZodError } from "zod";

import { requireRolEnEmpresa, requireUser } from "../_shared/auth.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { CerrarInstalacionInputSchema } from "../_shared/schemas.ts";

const ROLES_GESTION = ["owner", "admin", "gestor"] as const;

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
    input = CerrarInstalacionInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase } = await requireUser(req);

  const { data: inst, error: instError } = await supabase
    .from("instalacion")
    .select("id, empresa_id, estado, fecha_inicio")
    .eq("id", input.instalacion_id)
    .maybeSingle();

  if (instError) {
    throw makeError("internal_error", "Error consultando instalación", instError.message);
  }
  if (!inst) {
    throw makeError("not_found", "Instalación no encontrada o sin acceso");
  }
  if (inst.estado === "cerrada") {
    throw makeError("conflict", "La instalación ya está cerrada");
  }
  if (input.fecha_fin < inst.fecha_inicio) {
    throw makeError(
      "validation_error",
      "fecha_fin no puede ser anterior a fecha_inicio",
      { fecha_inicio: inst.fecha_inicio, fecha_fin: input.fecha_fin },
    );
  }

  await requireRolEnEmpresa(supabase, inst.empresa_id, ROLES_GESTION);

  // Liberamos cualquier lock activo asociado.
  await supabase.from("recaudacion_lock").delete().eq("instalacion_id", input.instalacion_id);

  const { data: row, error: updError } = await supabase
    .from("instalacion")
    .update({
      estado: "cerrada",
      fecha_fin: input.fecha_fin,
      notas: input.notas ?? null,
    })
    .eq("id", input.instalacion_id)
    .select()
    .single();

  if (updError) {
    throw makeError("internal_error", "No se pudo cerrar la instalación", updError.message);
  }

  return jsonResponse(row);
}));
