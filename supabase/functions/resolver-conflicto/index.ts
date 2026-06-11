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

  // UPDATE condicionado a `revisado_en IS NULL` para cerrar la ventana TOCTOU:
  // dos admins resolviendo a la vez pasan ambos la comprobación previa, pero
  // solo el primer UPDATE afecta una fila; el segundo no toca nada.
  // La escritura directa a `recaudacion` está revocada para clientes: se
  // persiste con service_role (RLS bypass). El rol+tenant ya se validó arriba.
  const { data: row, error: updError } = await getServiceClient()
    .from("recaudacion")
    .update(update)
    .eq("id", input.recaudacion_id)
    .is("revisado_en", null)
    .select()
    .maybeSingle();

  if (updError) {
    throw makeError("internal_error", "No se pudo resolver el conflicto", updError.message);
  }
  if (!row) {
    throw makeError("conflict", "El conflicto ya fue resuelto por otra operación");
  }

  // Marca la alerta asociada como leída (la creó `crear-recaudacion`).
  const service = getServiceClient();
  await service
    .from("alerta")
    .update({ leida: true })
    .eq("tipo", "recaudacion_conflicto")
    .eq("referencia_id", input.recaudacion_id)
    .eq("leida", false);

  // T-101 — fire-and-forget: avisa al técnico por PUSH (canal principal).
  // T-71  — fire-and-forget: avisa también por EMAIL (complemento/fallback).
  // Cualquier fallo en ambos canales (proveedor caído, sin credenciales,
  // técnico sin email/dispositivo) se loguea pero NO aborta la respuesta
  // del conflicto, que ya se ha resuelto en BD.
  notificarTecnicoSilenciosamente(input.recaudacion_id, "enviar-push");
  notificarTecnicoSilenciosamente(input.recaudacion_id, "enviar-email-tecnico");

  return jsonResponse(row);
}));

/**
 * Invoca una Edge Function de notificación (`enviar-push` o
 * `enviar-email-tecnico`) sin esperar la respuesta. Se aísla del flujo
 * principal para que un fallo de notificación nunca bloquee la resolución
 * del conflicto.
 *
 * Importante: usamos `fetch` directo a la URL pública de la función en
 * lugar de `supabase.functions.invoke` para evitar requerir el cliente.
 * Se autentica con la `service_role` key (no la anon pública): el endpoint
 * de funciones está expuesto a internet, así que `enviar-push` /
 * `enviar-email-tecnico` exigen service_role para no ser invocables por
 * cualquiera con el anon key con un recaudacion_id arbitrario.
 */
function notificarTecnicoSilenciosamente(recaudacionId: string, funcion: string): void {
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceKey) {
    console.warn(JSON.stringify({
      level: "warn",
      msg: "no_se_pudo_invocar_notificacion",
      funcion,
      reason: "faltan_envs",
      recaudacion_id: recaudacionId,
    }));
    return;
  }
  const url = `${supabaseUrl}/functions/v1/${funcion}`;
  fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${serviceKey}`,
    },
    body: JSON.stringify({ recaudacion_id: recaudacionId }),
  }).catch((err) => {
    console.error(JSON.stringify({
      level: "error",
      msg: "notificacion_invoke_failed",
      funcion,
      recaudacion_id: recaudacionId,
      error: err instanceof Error ? err.message : String(err),
    }));
  });
}
