"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { requireMembresiaActiva } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";

export type ActionResult = { ok: true } | { ok: false; error: { code: string } };

const IdSchema = z.string().guid();

/**
 * Marca una alerta como leída. Cualquier miembro de la empresa puede
 * hacerlo (RLS lo permite).
 */
export async function marcarAlertaComoLeida(alertaId: string): Promise<ActionResult> {
  await requireMembresiaActiva();

  const idCheck = IdSchema.safeParse(alertaId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = await createClient();
  const { error } = await supabase.rpc("marcar_alerta_leida", {
    p_alerta_id: alertaId,
  });

  if (error) {
    return { ok: false, error: { code: "actualizarFallido" } };
  }

  revalidatePath("/dashboard");
  return { ok: true };
}
