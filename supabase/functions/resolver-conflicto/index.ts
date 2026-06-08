/**
 * T-26b — Edge Function `resolver-conflicto`.
 *
 * Aplica la resolución de un conflicto de recaudación:
 *   * `aceptada`   — se mantienen los importes oficiales (los del cliente).
 *   * `sustituida` — los importes oficiales se sustituyen por los
 *                    `*_recalculado` que el server guardó al detectar el
 *                    conflicto. Implica un descuadre físico de caja a
 *                    gestionar manualmente con el técnico.
 *   * `anulada`    — la recaudación se anula completamente.
 *
 * Solo `owner` y `admin` pueden resolver. Marca también la alerta
 * `recaudacion_conflicto` correspondiente como leída.
 */

import { ZodError } from "zod";

import { requireRolEnEmpresa, requireUser } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/db.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { ResolverConflictoInputSchema } from "../_shared/schemas.ts";

const ROLES_ADMIN = ["owner", "admin"] as const;

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
    input = ResolverConflictoInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase, userId } = await requireUser(req);

  const { data: rec, error: recError } = await supabase
    .from("recaudacion")
    .select(
      `id, empresa_id, estado, conflicto, revisado_en,
       bruto_recalculado, neto_recalculado,
       parte_local_recalculada, parte_empresa_recalculada`,
    )
    .eq("id", input.recaudacion_id)
    .maybeSingle();

  if (recError) {
    throw makeError("internal_error", "Error consultando recaudación", recError.message);
  }
  if (!rec) {
    throw makeError("not_found", "Recaudación no encontrada o sin acceso");
  }
  if (!rec.conflicto) {
    throw makeError("conflict", "Esta recaudación no está marcada como conflicto");
  }
  if (rec.revisado_en) {
    throw makeError("conflict", "El conflicto ya fue resuelto");
  }

  await requireRolEnEmpresa(supabase, rec.empresa_id, ROLES_ADMIN);

  const ahora = new Date().toISOString();
  const update: Record<string, unknown> = {
    revisado_por: userId,
    revisado_en: ahora,
    resolucion: input.resolucion,
    resolucion_notas: input.notas ?? null,
  };

  switch (input.resolucion) {
    case "aceptada":
      // No se tocan los importes oficiales.
      break;
    case "sustituida":
      if (
        rec.bruto_recalculado === null ||
        rec.neto_recalculado === null ||
        rec.parte_local_recalculada === null ||
        rec.parte_empresa_recalculada === null
      ) {
        throw makeError(
          "validation_error",
          "No hay importes recalculados disponibles para sustituir",
        );
      }
      update.recaudacion_bruta = rec.bruto_recalculado;
      update.recaudacion_neta = rec.neto_recalculado;
      update.parte_local = rec.parte_local_recalculada;
      update.parte_empresa = rec.parte_empresa_recalculada;
      break;
    case "anulada":
      update.estado = "anulada";
      update.motivo_anulacion = input.notas ?? "Anulación tras revisión de conflicto";
      update.anulada_por = userId;
      update.anulada_en = ahora;
      break;
  }

  const { data: row, error: updError } = await supabase
    .from("recaudacion")
    .update(update)
    .eq("id", input.recaudacion_id)
    .select()
    .single();

  if (updError) {
    throw makeError("internal_error", "No se pudo resolver el conflicto", updError.message);
  }

  // Marca la alerta asociada como leída (la creó `crear-recaudacion`).
  const service = getServiceClient();
  await service
    .from("alerta")
    .update({ leida: true })
    .eq("tipo", "recaudacion_conflicto")
    .eq("referencia_id", input.recaudacion_id)
    .eq("leida", false);

  // T-71 — fire-and-forget: avisa al técnico por email. Cualquier fallo
  // (proveedor caído, técnico sin email, RESEND_API_KEY no configurado)
  // se loguea pero NO aborta la respuesta del conflicto, que ya se ha
  // resuelto en BD.
  notificarTecnicoSilenciosamente(input.recaudacion_id);

  return jsonResponse(row);
}));

/**
 * Invoca `enviar-email-tecnico` (T-71) sin esperar la respuesta. Se
 * aísla del flujo principal para que un fallo de email nunca bloquee la
 * resolución del conflicto.
 *
 * Importante: usamos `fetch` directo a la URL pública de la función en
 * lugar de `supabase.functions.invoke` para evitar requerir el cliente
 * y el header de auth — la función destino corre con service_role
 * internamente y la URL ya está protegida por la red de Supabase.
 */
function notificarTecnicoSilenciosamente(recaudacionId: string): void {
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!supabaseUrl || !anonKey) {
    console.warn(JSON.stringify({
      level: "warn",
      msg: "no_se_pudo_invocar_enviar_email_tecnico",
      reason: "faltan_envs",
      recaudacion_id: recaudacionId,
    }));
    return;
  }
  const url = `${supabaseUrl}/functions/v1/enviar-email-tecnico`;
  fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${anonKey}`,
    },
    body: JSON.stringify({ recaudacion_id: recaudacionId }),
  }).catch((err) => {
    console.error(JSON.stringify({
      level: "error",
      msg: "enviar_email_tecnico_invoke_failed",
      recaudacion_id: recaudacionId,
      error: err instanceof Error ? err.message : String(err),
    }));
  });
}
