"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { ROLES_ADMIN } from "@/lib/auth/roles";
import { requireRol, requireMembresiaActiva } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";

/**
 * Resultado serializable estándar que comparten todos los CRUDs.
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

const AnularInputSchema = z.object({
  motivo: z
    .string()
    .trim()
    .min(3, { message: "motivoMuyCorto" })
    .max(500, { message: "motivoMuyLargo" }),
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
// anularRecaudacion — Edge Function `anular-recaudacion`
// -----------------------------------------------------------------------------

export async function anularRecaudacion(
  recaudacionId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  await requireRol(ROLES_ADMIN);

  const idCheck = IdSchema.safeParse(recaudacionId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = AnularInputSchema.safeParse({
    motivo: formData.get("motivo") ?? "",
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

  const supabase = await createClient();
  const { data, error } = await supabase.functions.invoke<{
    code?: string;
    message?: string;
  }>("anular-recaudacion", {
    body: {
      recaudacion_id: recaudacionId,
      motivo: parsed.data.motivo,
    },
  });

  if (error) {
    const code = data?.code ?? null;
    if (code === "conflict") {
      return { ok: false, error: { code: "yaAnulada" } };
    }
    if (code === "not_found") {
      return { ok: false, error: { code: "noEncontrada" } };
    }
    if (code === "forbidden" || code === "unauthorized") {
      return { ok: false, error: { code: "sinPermiso" } };
    }
    if (code === "validation_error") {
      return {
        ok: false,
        error: {
          code: "validacion",
          fieldErrors: { motivo: ["motivoInvalido"] },
        },
      };
    }
    return { ok: false, error: { code: "anularFallido" } };
  }

  revalidatePath("/recaudaciones");
  revalidatePath(`/recaudaciones/${recaudacionId}`);
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// obtenerSignedUrlPdf — Edge Function `reimprimir-ticket`
// -----------------------------------------------------------------------------

/**
 * Devuelve una signed URL (10 min) del PDF archivado de una recaudación.
 * Lo invocamos vía Server Action para no exponer la lógica al cliente y
 * para que la respuesta no quede cacheada en el browser.
 */
export async function obtenerSignedUrlPdf(
  recaudacionId: string,
): Promise<ActionResult<{ url: string }>> {
  // Cualquier miembro con SELECT puede descargar el PDF (RLS lo refuerza).
  await requireMembresiaActiva();

  const idCheck = IdSchema.safeParse(recaudacionId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = await createClient();
  const { data, error } = await supabase.functions.invoke<{
    pdf_signed_url?: string;
    code?: string;
    message?: string;
  }>("reimprimir-ticket", {
    body: { recaudacion_id: recaudacionId },
  });

  if (error || !data?.pdf_signed_url) {
    const code = data?.code ?? null;
    if (code === "not_found") {
      return { ok: false, error: { code: "pdfNoEncontrado" } };
    }
    return { ok: false, error: { code: "pdfFallido" } };
  }

  return { ok: true, data: { url: data.pdf_signed_url } };
}
