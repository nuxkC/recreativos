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
  const flat = err.flatten();
  return Object.fromEntries(Object.entries(flat.fieldErrors).map(([k, v]) => [k, v ?? []]));
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

  const supabase = createClient();
  const { error } = await supabase
    .from("empresa")
    .update({
      nombre: parsed.data.nombre,
      cif: parsed.data.cif,
      direccion: parsed.data.direccion,
      telefono: parsed.data.telefono,
      email: parsed.data.email,
      zona_horaria: parsed.data.zonaHoraria,
      ticket_cabecera: parsed.data.ticketCabecera,
      ticket_pie: parsed.data.ticketPie,
    })
    .eq("id", activa.empresa.id);

  if (error) {
    return { ok: false, error: { code: "guardarFallido" } };
  }

  // El nombre y zona horaria afectan a la layout (selector de empresa)
  // y a las queries de dashboard, así que revalidamos el árbol completo.
  revalidatePath("/", "layout");
  return { ok: true, data: undefined };
}
