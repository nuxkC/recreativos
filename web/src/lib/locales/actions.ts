"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { ROLES_GESTION } from "@/lib/auth/roles";
import { requireRol } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";

import { CalendarioLocalInputSchema, LocalInputSchema } from "./schemas";

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
function parseLocalForm(formData: FormData): Record<string, unknown> {
  return {
    nombre: formData.get("nombre") ?? "",
    comunidadAutonoma: formData.get("comunidadAutonoma") ?? "",
    provinciaCodigo: formData.get("provinciaCodigo") ?? "",
    municipioCodigo: formData.get("municipioCodigo") ?? "",
    calle: formData.get("calle") ?? "",
    codigoPostal: formData.get("codigoPostal") ?? "",
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

  const supabase = await createClient();
  const { data, error } = await supabase.rpc("crear_local", {
    p_empresa_id: activa.empresa.id,
    p_nombre: parsed.data.nombre,
    p_cif_o_nif: parsed.data.cifONif,
    p_titular_nombre: parsed.data.titularNombre,
    p_telefono: parsed.data.telefono,
    p_email: parsed.data.email,
    p_notas: parsed.data.notas ?? null,
    p_comunidad_autonoma: parsed.data.comunidadAutonoma,
    p_provincia_codigo: parsed.data.provinciaCodigo,
    p_municipio_codigo: parsed.data.municipioCodigo,
    p_calle: parsed.data.calle,
    p_codigo_postal: parsed.data.codigoPostal,
  });

  if (error) {
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidatePath("/locales");
  // Devolvemos el id y dejamos que el cliente navegue (router.push). No usamos
  // redirect() aquí: cuando la action se invoca de forma programática (no como
  // <form action>), su NEXT_REDIRECT no llega al try/catch del cliente, que
  // entonces trata el éxito como un error inesperado.
  return { ok: true, data: { id: data } };
}

// -----------------------------------------------------------------------------
// actualizarLocal
// -----------------------------------------------------------------------------

export async function actualizarLocal(
  localId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  await requireRol(ROLES_GESTION);

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

  const supabase = await createClient();
  const { error } = await supabase.rpc("actualizar_local", {
    p_id: localId,
    p_nombre: parsed.data.nombre,
    p_cif_o_nif: parsed.data.cifONif,
    p_titular_nombre: parsed.data.titularNombre,
    p_telefono: parsed.data.telefono,
    p_email: parsed.data.email,
    p_notas: parsed.data.notas ?? null,
    p_comunidad_autonoma: parsed.data.comunidadAutonoma,
    p_provincia_codigo: parsed.data.provinciaCodigo,
    p_municipio_codigo: parsed.data.municipioCodigo,
    p_calle: parsed.data.calle,
    p_codigo_postal: parsed.data.codigoPostal,
  });

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
  await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(localId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = await createClient();
  const { error } = await supabase.rpc("eliminar_local", {
    p_id: localId,
  });

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

// -----------------------------------------------------------------------------
// actualizarCalendarioLocal — calendario de recaudación + operario (Planificación P1)
// -----------------------------------------------------------------------------

function parseCalendarioForm(formData: FormData): Record<string, unknown> {
  return {
    localId: formData.get("localId") ?? "",
    cadenciaSemanas: formData.get("cadenciaSemanas") ?? "",
    fechaInicioRecaudacion: formData.get("fechaInicioRecaudacion") ?? "",
    operarioId: formData.get("operarioId") ?? "",
  };
}

export async function actualizarCalendarioLocal(
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  await requireRol(ROLES_GESTION);

  const parsed = CalendarioLocalInputSchema.safeParse(parseCalendarioForm(formData));
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
  // La RPC valida rol gestor + tenant + operario operativo activo + coherencia.
  const { error } = await supabase.rpc("actualizar_calendario_local", {
    p_local_id: parsed.data.localId,
    p_cadencia_semanas: parsed.data.cadenciaSemanas,
    p_fecha_inicio_recaudacion: parsed.data.fechaInicioRecaudacion,
    p_operario_id: parsed.data.operarioId,
  });

  if (error) {
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidatePath("/locales");
  revalidatePath(`/locales/${parsed.data.localId}`);
  revalidatePath("/operarios");
  return { ok: true, data: undefined };
}
