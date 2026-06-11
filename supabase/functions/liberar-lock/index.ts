/**
 * T-24b — Edge Function `liberar-lock`.
 *
 * Libera el lock optimista de una instalación. Solo el técnico dueño del lock
 * (o un admin/owner de la empresa) puede liberarlo. La escritura directa a
 * `recaudacion_lock` está REVOCADA para clientes: el DELETE se hace con
 * service_role replicando en TS la regla que antes imponía la RLS
 * (`tecnico_id = auth.uid() OR usuario_es_admin(...)`).
 */

import { ZodError } from "zod";

import { requireRolEnEmpresa, requireUser } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/db.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { LiberarLockInputSchema } from "../_shared/schemas.ts";

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
    input = LiberarLockInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase, userId } = await requireUser(req);

  // La instalación acota el tenant: la RLS de SELECT solo deja ver las de la
  // propia empresa. Si no aparece, no existe o el caller no tiene acceso.
  const { data: inst, error: instError } = await supabase
    .from("instalacion")
    .select("empresa_id")
    .eq("id", input.instalacion_id)
    .maybeSingle();
  if (instError) {
    throw makeError("internal_error", "Error consultando instalación", instError.message);
  }
  if (!inst) {
    throw makeError("not_found", "Instalación no encontrada o sin acceso");
  }

  // Replica la policy `recaudacion_lock_delete`: dueño del lock O admin/owner.
  const rol = await requireRolEnEmpresa(supabase, inst.empresa_id, ROLES_OPERATIVO);
  const esAdmin = rol === "owner" || rol === "admin";

  let query = getServiceClient()
    .from("recaudacion_lock")
    .delete()
    .eq("instalacion_id", input.instalacion_id);
  if (!esAdmin) {
    query = query.eq("tecnico_id", userId);
  }
  const { data, error } = await query.select();

  if (error) {
    throw makeError("internal_error", "No se pudo liberar el lock", error.message);
  }

  // Si data está vacío, no había lock o no era del caller: no es un fallo,
  // devolvemos released=false para que el cliente lo sepa.
  return jsonResponse({ released: Array.isArray(data) && data.length > 0 });
}));
