"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";

import { ROLES_GESTION } from "@/lib/auth/roles";
import { requireRol } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";

import { LicenciaInputSchema } from "./schemas";

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
function parseLicenciaForm(formData: FormData): Record<string, unknown> {
  return {
    numero: formData.get("numero") ?? "",
    tipo: formData.get("tipo") ?? "",
    fechaExpedicion: formData.get("fechaExpedicion") ?? "",
    fechaCaducidad: formData.get("fechaCaducidad") ?? "",
    comunidadAutonoma: formData.get("comunidadAutonoma") ?? "",
    estado: formData.get("estado") ?? "activa",
    notas: formData.get("notas") ?? "",
  };
}

// -----------------------------------------------------------------------------
// crearLicencia
// -----------------------------------------------------------------------------

export async function crearLicencia(
  _prevState: ActionResult<{ id: string }> | null,
  formData: FormData,
): Promise<ActionResult<{ id: string }>> {
  const activa = await requireRol(ROLES_GESTION);

  const parsed = LicenciaInputSchema.safeParse(parseLicenciaForm(formData));
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
    .from("licencia")
    .insert({
      empresa_id: activa.empresa.id,
      numero: parsed.data.numero,
      tipo: parsed.data.tipo,
      fecha_expedicion: parsed.data.fechaExpedicion,
      fecha_caducidad: parsed.data.fechaCaducidad,
      comunidad_autonoma: parsed.data.comunidadAutonoma,
      estado: parsed.data.estado,
      notas: parsed.data.notas,
    })
    .select("id")
    .single();

  if (error) {
    if (error.code === "23505") {
      return {
        ok: false,
        error: {
          code: "numeroDuplicado",
          fieldErrors: { numero: ["numeroDuplicado"] },
        },
      };
    }
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidatePath("/licencias");
  redirect(`/licencias/${data.id}`);
}

// -----------------------------------------------------------------------------
// actualizarLicencia
// -----------------------------------------------------------------------------

export async function actualizarLicencia(
  licenciaId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  const activa = await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(licenciaId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = LicenciaInputSchema.safeParse(parseLicenciaForm(formData));
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
    .from("licencia")
    .update({
      numero: parsed.data.numero,
      tipo: parsed.data.tipo,
      fecha_expedicion: parsed.data.fechaExpedicion,
      fecha_caducidad: parsed.data.fechaCaducidad,
      comunidad_autonoma: parsed.data.comunidadAutonoma,
      estado: parsed.data.estado,
      notas: parsed.data.notas,
    })
    .eq("empresa_id", activa.empresa.id)
    .eq("id", licenciaId);

  if (error) {
    if (error.code === "23505") {
      return {
        ok: false,
        error: {
          code: "numeroDuplicado",
          fieldErrors: { numero: ["numeroDuplicado"] },
        },
      };
    }
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidatePath("/licencias");
  revalidatePath(`/licencias/${licenciaId}`);
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// eliminarLicencia
// -----------------------------------------------------------------------------

export async function eliminarLicencia(licenciaId: string): Promise<ActionResult> {
  const activa = await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(licenciaId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = createClient();
  const { error } = await supabase
    .from("licencia")
    .delete()
    .eq("empresa_id", activa.empresa.id)
    .eq("id", licenciaId);

  if (error) {
    if (error.code === "23503") {
      // FK violation: la licencia está referenciada por alguna instalación.
      return { ok: false, error: { code: "licenciaEnUso" } };
    }
    return { ok: false, error: { code: "borrarFallido" } };
  }

  revalidatePath("/licencias");
  redirect("/licencias");
}
