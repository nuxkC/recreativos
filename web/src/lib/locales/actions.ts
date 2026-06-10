"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { ROLES_GESTION } from "@/lib/auth/roles";
import { requireRol } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";

import { LocalInputSchema } from "./schemas";

/**
 * Resultado serializable que se devuelve al cliente. Convención común
 * para todas las Server Actions de CRUDs: `{ ok: true }` o
 * `{ ok: false, error: { code, fieldErrors? } }`. El `code` es siempre
 * una clave de i18n bajo `errors.<code>`.
 */
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

function fieldErrorsFromZod(err: z.ZodError): Record<string, string[]> {
  const flat = err.flatten();
  return Object.fromEntries(Object.entries(flat.fieldErrors).map(([k, v]) => [k, v ?? []]));
}

/**
 * Convierte FormData → objeto plano que el schema puede parsear.
 * Las cadenas vacías se conservan: el schema las normaliza a null.
 */
function parseLocalForm(formData: FormData): Record<string, unknown> {
  return {
    nombre: formData.get("nombre") ?? "",
    direccion: formData.get("direccion") ?? "",
    cifONif: formData.get("cifONif") ?? "",
    titularNombre: formData.get("titularNombre") ?? "",
    telefono: formData.get("telefono") ?? "",
    email: formData.get("email") ?? "",
    notas: formData.get("notas") ?? "",
  };
}

// -----------------------------------------------------------------------------
// crearLocal
// -----------------------------------------------------------------------------

export async function crearLocal(
  _prevState: ActionResult<{ id: string }> | null,
  formData: FormData,
): Promise<ActionResult<{ id: string }>> {
  const activa = await requireRol(ROLES_GESTION);

  const parsed = LocalInputSchema.safeParse(parseLocalForm(formData));
  if (!parsed.success) {
    return {
      ok: false,
      error: {
        code: "validacion",
        fieldErrors: fieldErrorsFromZod(parsed.error),
      },
    };
  }

  const supabase = createClient();
  const { data, error } = await supabase
    .from("local")
    .insert({
      empresa_id: activa.empresa.id,
      nombre: parsed.data.nombre,
      direccion: parsed.data.direccion,
      cif_o_nif: parsed.data.cifONif,
      titular_nombre: parsed.data.titularNombre,
      telefono: parsed.data.telefono,
      email: parsed.data.email,
      notas: parsed.data.notas,
    })
    .select("id")
    .single();

  if (error) {
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidatePath("/locales");
  // Devolvemos el id y dejamos que el cliente navegue (router.push). No usamos
  // redirect() aquí: cuando la action se invoca de forma programática (no como
  // <form action>), su NEXT_REDIRECT no llega al try/catch del cliente, que
  // entonces trata el éxito como un error inesperado.
  return { ok: true, data: { id: data.id } };
}

// -----------------------------------------------------------------------------
// actualizarLocal
// -----------------------------------------------------------------------------

export async function actualizarLocal(
  localId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  const activa = await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(localId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = LocalInputSchema.safeParse(parseLocalForm(formData));
  if (!parsed.success) {
    return {
      ok: false,
      error: {
        code: "validacion",
        fieldErrors: fieldErrorsFromZod(parsed.error),
      },
    };
  }

  const supabase = createClient();
  const { error } = await supabase
    .from("local")
    .update({
      nombre: parsed.data.nombre,
      direccion: parsed.data.direccion,
      cif_o_nif: parsed.data.cifONif,
      titular_nombre: parsed.data.titularNombre,
      telefono: parsed.data.telefono,
      email: parsed.data.email,
      notas: parsed.data.notas,
    })
    .eq("empresa_id", activa.empresa.id)
    .eq("id", localId);

  if (error) {
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidatePath("/locales");
  revalidatePath(`/locales/${localId}`);
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// eliminarLocal
// -----------------------------------------------------------------------------

export async function eliminarLocal(localId: string): Promise<ActionResult> {
  const activa = await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(localId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = createClient();
  const { error } = await supabase
    .from("local")
    .delete()
    .eq("empresa_id", activa.empresa.id)
    .eq("id", localId);

  if (error) {
    if (error.code === "23503") {
      // FK violation: el local está referenciado por alguna instalación.
      return { ok: false, error: { code: "localEnUso" } };
    }
    return { ok: false, error: { code: "borrarFallido" } };
  }

  revalidatePath("/locales");
  // Igual que en crear: devolvemos el resultado y el cliente navega. Con
  // redirect() el NEXT_REDIRECT no llegaría al await del componente.
  return { ok: true, data: undefined };
}
