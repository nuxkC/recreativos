"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { requireMembresiaActiva } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";

export type ActionResult = { ok: true } | { ok: false; error: { code: string } };

const IdSchema = z.string().uuid();

/**
 * Marca una alerta como leída. Cualquier miembro de la empresa puede
 * hacerlo (RLS lo permite).
 */
export async function marcarAlertaComoLeida(alertaId: string): Promise<ActionResult> {
  const activa = await requireMembresiaActiva();

  const idCheck = IdSchema.safeParse(alertaId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = createClient();
  const { error } = await supabase
    .from("alerta")
    .update({ leida: true })
    .eq("id", alertaId)
    .eq("empresa_id", activa.empresa.id);

  if (error) {
    return { ok: false, error: { code: "actualizarFallido" } };
  }

  revalidatePath("/dashboard");
  return { ok: true };
}
