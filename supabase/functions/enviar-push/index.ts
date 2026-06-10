/**
 * T-101 — Edge Function `enviar-push`.
 *
 * Notifica por push (FCM) al técnico cuando un administrador resuelve un
 * conflicto sobre una recaudación que él subió. La invoca
 * `resolver-conflicto` en modo fire-and-forget tras aplicar la resolución;
 * cualquier fallo aquí NO debe abortar la resolución del conflicto.
 *
 * Push es el canal PRINCIPAL (sustituye al email para este evento); el
 * email (`enviar-email-tecnico`, T-71) se mantiene como complemento/fallback
 * y se sigue disparando en paralelo desde `resolver-conflicto`.
 *
 * Implementación:
 *   1. Service-role client busca la recaudación + `tecnico_id` + `empresa_id`
 *      + datos de cabecera (local, máquina) para el cuerpo de la notificación.
 *   2. Lee los `device_token` del técnico en esa empresa.
 *   3. Envía vía `_shared/push.ts` a cada token. Si faltan credenciales FCM,
 *      hace skip seguro (`push_skipped`), igual que el email.
 *
 * No loggea PII (sin nombres del titular, sin tokens).
 */

import { ZodError } from "zod";

import { requireServiceRole } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/db.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { construirCuerpoPush, type RecaudacionPushRow } from "./mensaje.ts";
import { EnviarPushInputSchema } from "../_shared/schemas.ts";
import { enviarPush, pushConfigurado } from "../_shared/push.ts";

interface DeviceTokenRow {
  token: string;
}

Deno.serve(withHandler(async (req: Request) => {
  if (req.method !== "POST") {
    throw makeError("validation_error", "Solo se admite POST");
  }

  // Función interna: solo invocable con service_role (la dispara
  // `resolver-conflicto`). Sin esto, cualquiera con el anon key público podría
  // notificar al técnico de una recaudación arbitraria (spam / fuga cross-tenant).
  requireServiceRole(req);

  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    throw makeError("validation_error", "Body no es JSON válido");
  }

  let input;
  try {
    input = EnviarPushInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const service = getServiceClient();

  const { data: rec, error: recError } = await service
    .from("recaudacion")
    .select(
      `id, empresa_id, tecnico_id, estado, resolucion,
       instalacion:instalacion_id (
         maquina:maquina_id ( numero_serie ),
         local:local_id ( nombre )
       )`,
    )
    .eq("id", input.recaudacion_id)
    .maybeSingle<RecaudacionPushRow>();

  if (recError) {
    throw makeError("internal_error", "Error consultando recaudación", recError.message);
  }
  if (!rec) {
    throw makeError("not_found", "Recaudación no encontrada");
  }

  // Skip seguro temprano si no hay credenciales: evitamos consultar tokens.
  if (!pushConfigurado()) {
    console.log(JSON.stringify({
      level: "info",
      msg: "push_skipped_no_fcm_credentials",
      recaudacion_id: rec.id,
    }));
    return jsonResponse({ ok: true, skipped: true, code: "push_skipped" });
  }

  const { data: tokens, error: tokensError } = await service
    .from("device_token")
    .select("token")
    .eq("empresa_id", rec.empresa_id)
    .eq("usuario_id", rec.tecnico_id)
    .returns<DeviceTokenRow[]>();

  if (tokensError) {
    throw makeError("internal_error", "Error consultando tokens", tokensError.message);
  }
  if (!tokens || tokens.length === 0) {
    console.log(JSON.stringify({
      level: "info",
      msg: "push_sin_dispositivos",
      recaudacion_id: rec.id,
    }));
    return jsonResponse({ ok: true, code: "sin_dispositivos", enviados: 0 });
  }

  const { title, body, data } = construirCuerpoPush(rec);

  let enviados = 0;
  let fallidos = 0;
  for (const { token } of tokens) {
    const result = await enviarPush({ token, title, body, data });
    if (result.status === "sent") {
      enviados += 1;
    } else if (result.status === "failed") {
      fallidos += 1;
      console.error(JSON.stringify({
        level: "error",
        msg: "push_provider_failed",
        status: result.httpStatus,
        recaudacion_id: rec.id,
      }));
    }
  }

  console.log(JSON.stringify({
    level: "info",
    msg: "push_enviado",
    recaudacion_id: rec.id,
    enviados,
    fallidos,
  }));

  return jsonResponse({ ok: true, enviados, fallidos });
}));
