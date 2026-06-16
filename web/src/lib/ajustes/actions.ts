"use server";

import { revalidatePath } from "next/cache";
import { type ZodError } from "zod";

import { ROLES_ADMIN } from "@/lib/auth/roles";
import { requireRol } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";

import { EmpresaAjustesSchema } from "./schemas";

export type ActionResult<T = void> =
  | { ok: true; data: T }
  | {
      ok: false;
      error: {
        code: string;
        fieldErrors?: Record<string, string[]>;
      };
    };

function fieldErrorsFromZod(err: ZodError): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  for (const issue of err.issues) {
    if (issue.path.length === 0) continue;
    const key = String(issue.path[0]);
    (out[key] ??= []).push(issue.message);
  }
  return out;
}

function parseAjustesForm(formData: FormData): Record<string, unknown> {
  return {
    nombre: formData.get("nombre") ?? "",
    cif: formData.get("cif") ?? "",
    direccion: formData.get("direccion") ?? "",
    telefono: formData.get("telefono") ?? "",
    email: formData.get("email") ?? "",
    zonaHoraria: formData.get("zonaHoraria") ?? "Europe/Madrid",
    ticketCabecera: formData.get("ticketCabecera") ?? "",
    ticketPie: formData.get("ticketPie") ?? "",
    redondeoRecaudacion: formData.get("redondeoRecaudacion") ?? "0",
    porcentajeRecuperacion: formData.get("porcentajeRecuperacion") ?? "0",
  };
}

export async function actualizarAjustesEmpresa(
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  const activa = await requireRol(ROLES_ADMIN);

  const parsed = EmpresaAjustesSchema.safeParse(parseAjustesForm(formData));
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
  const { error } = await supabase.rpc("actualizar_ajustes_empresa", {
    p_empresa_id: activa.empresa.id,
    p_nombre: parsed.data.nombre,
    p_cif: parsed.data.cif,
    p_direccion: parsed.data.direccion,
    p_telefono: parsed.data.telefono,
    p_email: parsed.data.email,
    p_zona_horaria: parsed.data.zonaHoraria,
    p_ticket_cabecera: parsed.data.ticketCabecera,
    p_ticket_pie: parsed.data.ticketPie,
    p_redondeo_recaudacion: parsed.data.redondeoRecaudacion,
    p_porcentaje_recuperacion: parsed.data.porcentajeRecuperacion,
  });

  if (error) {
    return { ok: false, error: { code: "guardarFallido" } };
  }

  // El nombre y zona horaria afectan a la layout (selector de empresa)
  // y a las queries de dashboard, así que revalidamos el árbol completo.
  revalidatePath("/", "layout");
  return { ok: true, data: undefined };
}
