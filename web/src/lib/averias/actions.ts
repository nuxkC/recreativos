"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { createClient } from "@/lib/supabase/server";

import {
  AveriaInputSchema,
  CrearAveriaInputSchema,
  RecambioInputSchema,
  ResolucionInputSchema,
} from "./schemas";

/** Mismo contrato serializable que el resto de Server Actions de CRUDs. */
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
  const out: Record<string, string[]> = {};
  for (const issue of err.issues) {
    if (issue.path.length === 0) continue;
    const key = String(issue.path[0]);
    (out[key] ??= []).push(issue.message);
  }
  return out;
}

function revalidarMaquina(maquinaId: string) {
  revalidatePath(`/maquinas/${maquinaId}`);
  revalidatePath("/maquinas");
}

// -----------------------------------------------------------------------------
// crearAveria
// -----------------------------------------------------------------------------

export async function crearAveria(
  maquinaId: string,
  _prevState: ActionResult<{ id: string }> | null,
  formData: FormData,
): Promise<ActionResult<{ id: string }>> {
  const activa = await requireRol(ROLES_GESTION);

  if (!IdSchema.safeParse(maquinaId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = CrearAveriaInputSchema.safeParse({
    categoria: formData.get("categoria") ?? "",
    descripcion: formData.get("descripcion") ?? "",
    poneMaquinaFueraServicio: formData.get("poneMaquinaFueraServicio") ?? "",
    notas: formData.get("notas") ?? "",
    afectaTolva: formData.get("afectaTolva") ?? "",
    importeTolva: formData.get("importeTolva") ?? "",
  });
  if (!parsed.success) {
    return {
      ok: false,
      error: { code: "validacion", fieldErrors: fieldErrorsFromZod(parsed.error) },
    };
  }

  const supabase = createClient();
  const { data, error } = await supabase.rpc("crear_averia", {
    p_empresa_id: activa.empresa.id,
    p_maquina_id: maquinaId,
    p_categoria: parsed.data.categoria,
    p_descripcion: parsed.data.descripcion ?? null,
    p_pone_maquina_fuera_servicio: parsed.data.poneMaquinaFueraServicio,
    p_notas: parsed.data.notas ?? null,
    // §5.6: dinero como string (numeric server-side); null si no afecta tolva.
    p_afecta_tolva: parsed.data.afectaTolva,
    p_importe_tolva: parsed.data.afectaTolva ? parsed.data.importeTolva : null,
  });

  if (error) {
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidarMaquina(maquinaId);
  return { ok: true, data: { id: data } };
}

// -----------------------------------------------------------------------------
// actualizarAveria
// -----------------------------------------------------------------------------

export async function actualizarAveria(
  averiaId: string,
  maquinaId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  await requireRol(ROLES_GESTION);

  if (!IdSchema.safeParse(averiaId).success || !IdSchema.safeParse(maquinaId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = AveriaInputSchema.safeParse({
    categoria: formData.get("categoria") ?? "",
    descripcion: formData.get("descripcion") ?? "",
    poneMaquinaFueraServicio: formData.get("poneMaquinaFueraServicio") ?? "",
    notas: formData.get("notas") ?? "",
  });
  if (!parsed.success) {
    return {
      ok: false,
      error: { code: "validacion", fieldErrors: fieldErrorsFromZod(parsed.error) },
    };
  }

  const supabase = createClient();
  const { error } = await supabase.rpc("actualizar_averia", {
    p_id: averiaId,
    p_categoria: parsed.data.categoria,
    p_descripcion: parsed.data.descripcion ?? null,
    p_pone_maquina_fuera_servicio: parsed.data.poneMaquinaFueraServicio,
    p_notas: parsed.data.notas ?? null,
  });

  if (error) {
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidarMaquina(maquinaId);
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// resolverAveria
// -----------------------------------------------------------------------------

export async function resolverAveria(
  averiaId: string,
  maquinaId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  await requireRol(ROLES_GESTION);

  if (!IdSchema.safeParse(averiaId).success || !IdSchema.safeParse(maquinaId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = ResolucionInputSchema.safeParse({
    notasResolucion: formData.get("notasResolucion") ?? "",
  });
  if (!parsed.success) {
    return {
      ok: false,
      error: { code: "validacion", fieldErrors: fieldErrorsFromZod(parsed.error) },
    };
  }

  const supabase = createClient();
  const { error } = await supabase.rpc("resolver_averia", {
    p_id: averiaId,
    p_notas_resolucion: parsed.data.notasResolucion ?? null,
  });

  if (error) {
    // 22023: la avería ya estaba resuelta (carrera con otra pestaña).
    if (error.code === "22023") {
      return { ok: false, error: { code: "yaResuelta" } };
    }
    return { ok: false, error: { code: "resolverFallido" } };
  }

  revalidarMaquina(maquinaId);
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// crearRecambio
// -----------------------------------------------------------------------------

export async function crearRecambio(
  averiaId: string,
  maquinaId: string,
  _prevState: ActionResult<{ id: string }> | null,
  formData: FormData,
): Promise<ActionResult<{ id: string }>> {
  await requireRol(ROLES_GESTION);

  if (!IdSchema.safeParse(averiaId).success || !IdSchema.safeParse(maquinaId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = RecambioInputSchema.safeParse({
    pieza: formData.get("pieza") ?? "",
    cantidad: formData.get("cantidad") ?? "",
    coste: formData.get("coste") ?? "",
    notas: formData.get("notas") ?? "",
  });
  if (!parsed.success) {
    return {
      ok: false,
      error: { code: "validacion", fieldErrors: fieldErrorsFromZod(parsed.error) },
    };
  }

  const supabase = createClient();
  const { data, error } = await supabase.rpc("crear_recambio", {
    p_averia_id: averiaId,
    p_pieza: parsed.data.pieza,
    p_cantidad: parsed.data.cantidad,
    p_coste: parsed.data.coste ?? null,
    p_notas: parsed.data.notas ?? null,
  });

  if (error) {
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidarMaquina(maquinaId);
  return { ok: true, data: { id: data } };
}

// -----------------------------------------------------------------------------
// eliminarRecambio
// -----------------------------------------------------------------------------

export async function eliminarRecambio(
  recambioId: string,
  maquinaId: string,
): Promise<ActionResult> {
  await requireRol(ROLES_GESTION);

  if (!IdSchema.safeParse(recambioId).success || !IdSchema.safeParse(maquinaId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = createClient();
  const { error } = await supabase.rpc("eliminar_recambio", { p_id: recambioId });

  if (error) {
    return { ok: false, error: { code: "borrarFallido" } };
  }

  revalidarMaquina(maquinaId);
  return { ok: true, data: undefined };
}
