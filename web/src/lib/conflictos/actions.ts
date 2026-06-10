"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { ROLES_ADMIN } from "@/lib/auth/roles";
import { requireRol } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";

/**
 * Resultado serializable estándar (mismo formato que el resto de CRUDs).
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

const RESOLUCIONES = ["aceptada", "sustituida", "anulada"] as const;
export type ResolucionConflicto = (typeof RESOLUCIONES)[number];

const IdSchema = z.string().uuid();

const ResolverInputSchema = z.object({
  resolucion: z.enum(RESOLUCIONES),
  notas: z
    .string()
    .trim()
    .max(2000, { message: "notasMuyLargas" })
    .transform((v) => (v.length === 0 ? null : v))
    .nullable(),
});

function fieldErrorsFromZod(err: z.ZodError): Record<string, string[]> {
  const flat = err.flatten();
  return Object.fromEntries(Object.entries(flat.fieldErrors).map(([k, v]) => [k, v ?? []]));
}

/**
 * Invoca la Edge Function `resolver-conflicto` (T-26b) con la decisión
 * tomada por el admin. Las tres opciones:
 *
 * - `aceptada`: se aceptan las cifras del cliente tal cual.
 * - `sustituida`: el sistema copia los `*_recalculado` server como
 *   oficiales.
 * - `anulada`: la recaudación pasa a estado `anulada`.
 */
export async function resolverConflicto(
  recaudacionId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  await requireRol(ROLES_ADMIN);

  const idCheck = IdSchema.safeParse(recaudacionId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = ResolverInputSchema.safeParse({
    resolucion: formData.get("resolucion") ?? "",
    notas: formData.get("notas") ?? "",
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

  const supabase = createClient();
  const { data, error } = await supabase.functions.invoke<{
    code?: string;
    message?: string;
  }>("resolver-conflicto", {
    body: {
      recaudacion_id: recaudacionId,
      resolucion: parsed.data.resolucion,
      notas: parsed.data.notas ?? undefined,
    },
  });

  if (error) {
    const code = data?.code ?? null;
    if (code === "not_found") {
      return { ok: false, error: { code: "noEncontrada" } };
    }
    if (code === "conflict") {
      return { ok: false, error: { code: "yaResuelto" } };
    }
    if (code === "forbidden" || code === "unauthorized") {
      return { ok: false, error: { code: "sinPermiso" } };
    }
    if (code === "validation_error") {
      return { ok: false, error: { code: "validacion" } };
    }
    return { ok: false, error: { code: "resolverFallido" } };
  }

  revalidatePath("/conflictos");
  revalidatePath("/recaudaciones");
  revalidatePath(`/recaudaciones/${recaudacionId}`);
  return { ok: true, data: undefined };
}
