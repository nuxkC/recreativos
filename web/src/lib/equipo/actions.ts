"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { ROLES, type Rol } from "@/lib/auth/roles";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_ADMIN } from "@/lib/auth/roles";
import { createClient } from "@/lib/supabase/server";

export type ActionResult<T = void> =
  | { ok: true; data: T }
  | {
      ok: false;
      error: {
        code: string;
        fieldErrors?: Record<string, string[]>;
      };
    };

const IdSchema = z.string().uuid();

const InvitarInputSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, { message: "emailRequerido" })
    .email({ message: "emailInvalido" }),
  rol: z.enum(ROLES, { message: "rolInvalido" }),
  nombreCompleto: z
    .string()
    .trim()
    .max(150, { message: "nombreMuyLargo" })
    .transform((v) => (v.length === 0 ? null : v))
    .nullable(),
});

const CambiarRolSchema = z.object({
  rol: z.enum(ROLES, { message: "rolInvalido" }),
});

function fieldErrorsFromZod(err: z.ZodError): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  for (const issue of err.issues) {
    if (issue.path.length === 0) continue;
    const key = String(issue.path[0]);
    (out[key] ??= []).push(issue.message);
  }
  return out;
}

// -----------------------------------------------------------------------------
// invitarMiembro — Edge Function `invitar-usuario`
// -----------------------------------------------------------------------------

export async function invitarMiembro(
  _prevState: ActionResult<{ usuarioId: string }> | null,
  formData: FormData,
): Promise<ActionResult<{ usuarioId: string }>> {
  const activa = await requireRol(ROLES_ADMIN);

  const parsed = InvitarInputSchema.safeParse({
    email: formData.get("email") ?? "",
    rol: formData.get("rol") ?? "",
    nombreCompleto: formData.get("nombreCompleto") ?? "",
  });
  if (!parsed.success) {
    return {
      ok: false,
      error: {
        code: "validacion",
        fieldErrors: fieldErrorsFromZod(parsed.error),
      },
    };
  }

  // Solo el owner puede invitar a otro owner.
  if (parsed.data.rol === "owner" && activa.rol !== "owner") {
    return {
      ok: false,
      error: {
        code: "soloOwnerPuedeInvitarOwner",
        fieldErrors: { rol: ["soloOwnerPuedeInvitarOwner"] },
      },
    };
  }

  const supabase = await createClient();
  const { data, error } = await supabase.functions.invoke<{
    code?: string;
    usuario_id?: string;
    invited_new?: boolean;
  }>("invitar-usuario", {
    body: {
      empresa_id: activa.empresa.id,
      email: parsed.data.email,
      rol: parsed.data.rol,
      nombre_completo: parsed.data.nombreCompleto ?? undefined,
    },
  });

  if (error) {
    const code = data?.code ?? null;
    if (code === "validation_error") {
      return { ok: false, error: { code: "validacion" } };
    }
    if (code === "forbidden" || code === "unauthorized") {
      return { ok: false, error: { code: "sinPermiso" } };
    }
    return { ok: false, error: { code: "invitarFallido" } };
  }

  revalidatePath("/equipo");
  return { ok: true, data: { usuarioId: data?.usuario_id ?? "" } };
}

// -----------------------------------------------------------------------------
// cambiarRol — UPDATE empresa_usuario
// -----------------------------------------------------------------------------

export async function cambiarRol(
  usuarioId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  const activa = await requireRol(ROLES_ADMIN);

  const idCheck = IdSchema.safeParse(usuarioId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = CambiarRolSchema.safeParse({ rol: formData.get("rol") ?? "" });
  if (!parsed.success) {
    return {
      ok: false,
      error: {
        code: "validacion",
        fieldErrors: fieldErrorsFromZod(parsed.error),
      },
    };
  }
  const nuevoRol: Rol = parsed.data.rol;

  // Reglas de negocio:
  // - Nadie cambia su propio rol (evitar quedarse sin admin por error).
  // - Solo owner puede asignar/quitar el rol owner.
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (user?.id === usuarioId) {
    return { ok: false, error: { code: "noPuedesCambiarTuRol" } };
  }
  // Solo el owner puede asignar el rol owner.
  if (nuevoRol === "owner" && activa.rol !== "owner") {
    return { ok: false, error: { code: "soloOwnerAsignaOwner" } };
  }

  // Si el target es owner y yo no soy owner, tampoco puedo tocarle.
  const { data: target, error: targetError } = await supabase
    .from("empresa_usuario")
    .select("rol")
    .eq("empresa_id", activa.empresa.id)
    .eq("usuario_id", usuarioId)
    .returns<{ rol: string }[]>()
    .maybeSingle();
  if (targetError) {
    return { ok: false, error: { code: "lecturaFallida" } };
  }
  if (target?.rol === "owner" && activa.rol !== "owner") {
    return { ok: false, error: { code: "soloOwnerEditaOwner" } };
  }

  const { error } = await supabase.rpc("cambiar_rol_miembro", {
    p_empresa_id: activa.empresa.id,
    p_usuario_id: usuarioId,
    p_rol: nuevoRol,
  });

  if (error) {
    return { ok: false, error: { code: "actualizarFallido" } };
  }

  revalidatePath("/equipo");
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// cambiarActivo — UPDATE empresa_usuario.activo
// -----------------------------------------------------------------------------

export async function cambiarActivo(usuarioId: string, activo: boolean): Promise<ActionResult> {
  const activa = await requireRol(ROLES_ADMIN);

  const idCheck = IdSchema.safeParse(usuarioId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (user?.id === usuarioId) {
    return { ok: false, error: { code: "noPuedesDesactivarteSolo" } };
  }

  const { data: target, error: targetError } = await supabase
    .from("empresa_usuario")
    .select("rol")
    .eq("empresa_id", activa.empresa.id)
    .eq("usuario_id", usuarioId)
    .returns<{ rol: string }[]>()
    .maybeSingle();
  if (targetError) {
    return { ok: false, error: { code: "lecturaFallida" } };
  }
  if (target?.rol === "owner" && activa.rol !== "owner") {
    return { ok: false, error: { code: "soloOwnerEditaOwner" } };
  }

  const { error } = await supabase.rpc("cambiar_estado_miembro", {
    p_empresa_id: activa.empresa.id,
    p_usuario_id: usuarioId,
    p_activo: activo,
  });

  if (error) {
    return { ok: false, error: { code: "actualizarFallido" } };
  }

  revalidatePath("/equipo");
  return { ok: true, data: undefined };
}
