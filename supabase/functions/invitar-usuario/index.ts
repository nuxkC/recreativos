/**
 * T-27a — Edge Function `invitar-usuario`.
 *
 * Invita por email a un usuario a una empresa con un rol concreto.
 *
 * Flujo:
 *   1. Verifica que el caller es admin/owner de la empresa.
 *   2. Si el email ya existe en auth.users, lo reusamos.
 *   3. Si no, usamos `auth.admin.inviteUserByEmail` (service_role) para
 *      enviar un magic link.
 *   4. Insertamos `empresa_usuario` con el rol pedido.
 *
 * La service_role key NUNCA viaja al cliente.
 */

import { ZodError } from "zod";

import { registrarAuditoria } from "../_shared/audit.ts";
import { requireRolEnEmpresa, requireUser } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/db.ts";
import { jsonResponse, makeError } from "../_shared/errors.ts";
import { withHandler } from "../_shared/handler.ts";
import { InvitarUsuarioInputSchema } from "../_shared/schemas.ts";

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
    input = InvitarUsuarioInputSchema.parse(raw);
  } catch (err) {
    if (err instanceof ZodError) {
      throw makeError("validation_error", "Input inválido", err.issues);
    }
    throw err;
  }

  const { supabase, userId: actorId } = await requireUser(req);
  await requireRolEnEmpresa(supabase, input.empresa_id, ROLES_ADMIN);

  // El rol `owner` no se asigna por invitación: solo lo otorga el registro de
  // la empresa. Permitirlo dejaría a un admin crear owners o, vía el upsert por
  // (empresa_id, usuario_id), degradar al owner existente (escalada intra-tenant).
  if (input.rol === "owner") {
    throw makeError("forbidden", "El rol owner no se puede asignar mediante invitación");
  }

  const service = getServiceClient();

  // Buscamos al usuario por email. La API admin no expone `getUserByEmail`
  // directamente, así que usamos `listUsers` paginado y filtramos.
  const usuarioId = await resolverUsuarioPorEmail(service, input.email);

  let invitedNew = false;
  let userId: string;
  if (usuarioId) {
    userId = usuarioId;
  } else {
    const { data: invited, error: invErr } = await service.auth.admin.inviteUserByEmail(
      input.email,
    );
    if (invErr || !invited?.user) {
      throw makeError("internal_error", "No se pudo enviar la invitación", invErr?.message);
    }
    userId = invited.user.id;
    invitedNew = true;
  }

  // Aseguramos que existe un perfil mínimo en `usuario` (auth.users tiene el id
  // pero el trigger de creación de perfil podría no estar configurado todavía).
  await service
    .from("usuario")
    .upsert({
      id: userId,
      nombre_completo: input.nombre_completo ?? input.email,
    }, { onConflict: "id", ignoreDuplicates: true });

  // Creamos o reactivamos la membresía.
  const { data: membresiaPrevia } = await service
    .from("empresa_usuario")
    .select("rol, activo")
    .eq("empresa_id", input.empresa_id)
    .eq("usuario_id", userId)
    .maybeSingle();

  // No se puede tocar la membresía de un owner desde aquí (ni un admin puede
  // degradarlo). El cambio de owner es una operación aparte y deliberada.
  if (membresiaPrevia?.rol === "owner") {
    throw makeError("forbidden", "No se puede modificar la membresía del owner");
  }

  const { data: row, error: upsertError } = await service
    .from("empresa_usuario")
    .upsert({
      empresa_id: input.empresa_id,
      usuario_id: userId,
      rol: input.rol,
      activo: true,
    }, { onConflict: "empresa_id,usuario_id" })
    .select()
    .single();

  if (upsertError) {
    throw makeError("internal_error", "No se pudo asignar el rol", upsertError.message);
  }

  // Auditoría (T-202). Distingue alta/reactivación de cambio de rol.
  // `datos` no incluye el email del invitado (sin PII): solo el rol.
  const membresiaExistia = membresiaPrevia !== null && membresiaPrevia.activo === true;
  const accion = membresiaExistia && membresiaPrevia?.rol !== input.rol
    ? "rol_cambiado"
    : "usuario_invitado";
  await registrarAuditoria(service, {
    empresaId: input.empresa_id,
    actorUsuarioId: actorId,
    accion,
    entidadTabla: "empresa_usuario",
    entidadId: userId,
    datos: accion === "rol_cambiado"
      ? { rol_anterior: membresiaPrevia?.rol, rol_nuevo: input.rol }
      : { rol: input.rol, invitado_nuevo: invitedNew },
  });

  return jsonResponse({ membresia: row, invited_new: invitedNew, usuario_id: userId }, 201);
}));

async function resolverUsuarioPorEmail(
  service: ReturnType<typeof getServiceClient>,
  email: string,
): Promise<string | null> {
  const target = email.toLowerCase().trim();
  const PER_PAGE = 1000;
  for (let page = 1; page <= 10; page++) {
    const { data, error } = await service.auth.admin.listUsers({ page, perPage: PER_PAGE });
    if (error) {
      throw makeError("internal_error", "Error buscando usuario por email", error.message);
    }
    if (!data?.users?.length) return null;
    const found = data.users.find((u) => (u.email ?? "").toLowerCase().trim() === target);
    if (found) return found.id;
    if (data.users.length < PER_PAGE) return null;
  }
  return null;
}
