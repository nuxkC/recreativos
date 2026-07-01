"use server";

import { revalidatePath } from "next/cache";
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

const IdSchema = z.string().guid();

function fieldErrorsFromZod(err: z.ZodError): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  for (const issue of err.issues) {
    if (issue.path.length === 0) continue;
    const key = String(issue.path[0]);
    (out[key] ??= []).push(issue.message);
  }
  return out;
}

/**
 * Convierte FormData → objeto plano que el schema puede parsear.
 * Las cadenas vacías se conservan: el schema las normaliza a null.
 */
function parseLicenciaForm(formData: FormData): Record<string, unknown> {
  return {
    numero: formData.get("numero") ?? "",
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

  const supabase = await createClient();
  const { data, error } = await supabase.rpc("crear_licencia", {
    p_empresa_id: activa.empresa.id,
    p_numero: parsed.data.numero,
    // El contrato de la RPC aún exige p_tipo (3.ª posición); el campo se eliminó
    // del formulario, así que enviamos siempre null hasta que la RPC lo retire.
    p_tipo: null,
    p_fecha_expedicion: parsed.data.fechaExpedicion,
    p_fecha_caducidad: parsed.data.fechaCaducidad,
    p_comunidad_autonoma: parsed.data.comunidadAutonoma,
    p_estado: parsed.data.estado,
    p_notas: parsed.data.notas ?? null,
  });

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
  // Devolvemos el id y dejamos que el cliente navegue (router.push). No usamos
  // redirect() aquí: cuando la action se invoca de forma programática (no como
  // <form action>), su NEXT_REDIRECT no llega al try/catch del cliente, que
  // entonces trata el éxito como un error inesperado.
  return { ok: true, data: { id: data } };
}

// -----------------------------------------------------------------------------
// actualizarLicencia
// -----------------------------------------------------------------------------

export async function actualizarLicencia(
  licenciaId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  await requireRol(ROLES_GESTION);

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

  const supabase = await createClient();
  const { error } = await supabase.rpc("actualizar_licencia", {
    p_id: licenciaId,
    p_numero: parsed.data.numero,
    // El contrato de la RPC aún exige p_tipo (3.ª posición); el campo se eliminó
    // del formulario, así que enviamos siempre null hasta que la RPC lo retire.
    p_tipo: null,
    p_fecha_expedicion: parsed.data.fechaExpedicion,
    p_fecha_caducidad: parsed.data.fechaCaducidad,
    p_comunidad_autonoma: parsed.data.comunidadAutonoma,
    p_estado: parsed.data.estado,
    p_notas: parsed.data.notas ?? null,
  });

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
  await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(licenciaId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = await createClient();
  const { error } = await supabase.rpc("eliminar_licencia", {
    p_id: licenciaId,
  });

  if (error) {
    if (error.code === "23503") {
      // FK violation: la licencia está referenciada por alguna instalación.
      return { ok: false, error: { code: "licenciaEnUso" } };
    }
    return { ok: false, error: { code: "borrarFallido" } };
  }

  revalidatePath("/licencias");
  // Igual que en crear: devolvemos el resultado y el cliente navega. Con
  // redirect() el NEXT_REDIRECT no llegaría al await del componente.
  return { ok: true, data: undefined };
}
