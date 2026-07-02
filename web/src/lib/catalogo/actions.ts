"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { requireAdminCatalogo } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";

/**
 * Resultado serializable que se devuelve al cliente. Misma convención que el
 * resto de Server Actions del back-office: `{ ok: true }` o
 * `{ ok: false, error: { code } }`, donde `code` es una clave de i18n bajo
 * `catalogoAdmin.errores.<code>`.
 */
export type ActionResult<T = void> = { ok: true; data: T } | { ok: false; error: { code: string } };

const IdSchema = z.string().guid();
const NombreSchema = z.string().trim().min(1).max(120);

/**
 * Mapea el error de PostgREST devuelto por la RPC a un `code` de i18n.
 *
 * Los códigos SQL los lanzan las RPCs de curación (PR #128):
 *   - 42501 → el usuario no es admin del catálogo (guard `es_admin_catalogo`).
 *   - 23505 → violación de unicidad: ya existe una entrada con ese nombre
 *             (renombrar chocaría con un duplicado → hay que fusionar).
 *   - 22023 → parámetro inválido: fusión imposible (mismo origen/destino,
 *             distinto fabricante…).
 */
function mapRpcError(code: string | undefined): string {
  switch (code) {
    case "42501":
      return "sinPermiso";
    case "23505":
      return "yaExiste";
    case "22023":
      return "fusionInvalida";
    default:
      return "desconocido";
  }
}

// -----------------------------------------------------------------------------
// renombrarFabricante
// -----------------------------------------------------------------------------

export async function renombrarFabricante(id: string, nombre: string): Promise<ActionResult> {
  await requireAdminCatalogo();

  if (!IdSchema.safeParse(id).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }
  const parsed = NombreSchema.safeParse(nombre);
  if (!parsed.success) {
    return { ok: false, error: { code: "nombreInvalido" } };
  }

  const supabase = await createClient();
  const { error } = await supabase.rpc("renombrar_fabricante", {
    p_id: id,
    p_nombre: parsed.data,
  });

  if (error) {
    return { ok: false, error: { code: mapRpcError(error.code) } };
  }

  revalidatePath("/catalogo");
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// renombrarModelo
// -----------------------------------------------------------------------------

export async function renombrarModelo(id: string, nombre: string): Promise<ActionResult> {
  await requireAdminCatalogo();

  if (!IdSchema.safeParse(id).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }
  const parsed = NombreSchema.safeParse(nombre);
  if (!parsed.success) {
    return { ok: false, error: { code: "nombreInvalido" } };
  }

  const supabase = await createClient();
  const { error } = await supabase.rpc("renombrar_modelo", {
    p_id: id,
    p_nombre: parsed.data,
  });

  if (error) {
    return { ok: false, error: { code: mapRpcError(error.code) } };
  }

  revalidatePath("/catalogo");
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// fusionarFabricante (destructiva: absorbe `origen` en `destino`)
// -----------------------------------------------------------------------------

export async function fusionarFabricante(
  origenId: string,
  destinoId: string,
): Promise<ActionResult> {
  await requireAdminCatalogo();

  if (!IdSchema.safeParse(origenId).success || !IdSchema.safeParse(destinoId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }
  if (origenId === destinoId) {
    return { ok: false, error: { code: "fusionInvalida" } };
  }

  const supabase = await createClient();
  const { error } = await supabase.rpc("fusionar_fabricante", {
    p_origen: origenId,
    p_destino: destinoId,
  });

  if (error) {
    return { ok: false, error: { code: mapRpcError(error.code) } };
  }

  revalidatePath("/catalogo");
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// fusionarModelo (destructiva: absorbe `origen` en `destino`)
// -----------------------------------------------------------------------------

export async function fusionarModelo(origenId: string, destinoId: string): Promise<ActionResult> {
  await requireAdminCatalogo();

  if (!IdSchema.safeParse(origenId).success || !IdSchema.safeParse(destinoId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }
  if (origenId === destinoId) {
    return { ok: false, error: { code: "fusionInvalida" } };
  }

  const supabase = await createClient();
  const { error } = await supabase.rpc("fusionar_modelo", {
    p_origen: origenId,
    p_destino: destinoId,
  });

  if (error) {
    return { ok: false, error: { code: mapRpcError(error.code) } };
  }

  revalidatePath("/catalogo");
  return { ok: true, data: undefined };
}
