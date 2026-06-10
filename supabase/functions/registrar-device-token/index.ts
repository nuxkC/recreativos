/**
 * T-101 — Edge Function `registrar-device-token`.
 *
 * La app móvil la invoca (con el JWT del usuario) para registrar o
 * actualizar el token de registro FCM de su dispositivo dentro de la
 * empresa activa. Lo consume luego `enviar-push` para resolver a qué
 * dispositivos notificar.
 *
 * Seguridad:
 *   - `usuario_id` NO se acepta del cliente: se fija a `auth.uid()`.
 *   - La pertenencia a la empresa la valida RLS (policy `device_token_insert`)
 *     y, de forma explícita, `requireRolEnEmpresa` no aplica aquí porque
 *     cualquier miembro puede registrar su token.
 *   - Upsert por `token` (único): si el mismo token reaparece para otro
 *     usuario/empresa (reinstalación, cambio de cuenta), se reasigna.
 */

import { ZodError } from "zod";

import { requireRolEnEmpresa, requireUser } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/db.ts";
import { ROLES } from "../_shared/constants.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { RegistrarDeviceTokenInputSchema } from "../_shared/schemas.ts";

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
    input = RegistrarDeviceTokenInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase, userId } = await requireUser(req);

  // Validamos pertenencia a la empresa (cualquier rol) de forma explícita,
  // porque el upsert va con service_role. El token es UNIQUE global: si el mismo
  // móvil cambia de cuenta, el token ya pertenece a OTRO usuario y reasignarlo
  // con el user client fallaba por RLS (la policy de UPDATE exige
  // usuario_id = auth.uid() de la fila previa), dejando el token apuntando al
  // usuario anterior y enviándole pushes que no le corresponden.
  await requireRolEnEmpresa(supabase, input.empresa_id, ROLES);

  // Upsert por token único con service_role: reasigna propietario y empresa.
  const { data, error } = await getServiceClient()
    .from("device_token")
    .upsert(
      {
        empresa_id: input.empresa_id,
        usuario_id: userId,
        token: input.token,
        plataforma: input.plataforma,
        updated_at: new Date().toISOString(),
      },
      { onConflict: "token" },
    )
    .select("id")
    .single();

  if (error) {
    throw makeError("internal_error", "No se pudo registrar el token", error.message);
  }

  // No loggeamos el token (es un identificador sensible del dispositivo).
  console.log(JSON.stringify({
    level: "info",
    msg: "device_token_registrado",
    device_token_id: data.id,
    plataforma: input.plataforma,
  }));

  return jsonResponse({ id: data.id });
}));
