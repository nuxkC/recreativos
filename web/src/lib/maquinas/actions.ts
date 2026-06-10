"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { ROLES_GESTION } from "@/lib/auth/roles";
import { requireRol } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";

import { MaquinaInputSchema } from "./schemas";

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

const IdSchema = z.string().uuid();

function fieldErrorsFromZod(err: z.ZodError): Record<string, string[]> {
  const flat = err.flatten();
  return Object.fromEntries(Object.entries(flat.fieldErrors).map(([k, v]) => [k, v ?? []]));
}

/**
 * Convierte FormData → objeto plano que el schema puede parsear.
 * Las cadenas vacías se conservan: el schema las normaliza a null o
 * lanza el error correspondiente.
 */
function parseMaquinaForm(formData: FormData): Record<string, unknown> {
  return {
    numeroSerie: formData.get("numeroSerie") ?? "",
    modelo: formData.get("modelo") ?? "",
    fabricante: formData.get("fabricante") ?? "",
    valorCredito: formData.get("valorCredito") ?? "",
    contadorEntradasInicial: formData.get("contadorEntradasInicial") ?? "",
    contadorSalidasInicial: formData.get("contadorSalidasInicial") ?? "",
    estado: formData.get("estado") ?? "almacen",
    notas: formData.get("notas") ?? "",
  };
}

// -----------------------------------------------------------------------------
// crearMaquina
// -----------------------------------------------------------------------------

export async function crearMaquina(
  _prevState: ActionResult<{ id: string }> | null,
  formData: FormData,
): Promise<ActionResult<{ id: string }>> {
  const activa = await requireRol(ROLES_GESTION);

  const parsed = MaquinaInputSchema.safeParse(parseMaquinaForm(formData));
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
  const { data, error } = await supabase
    .from("maquina")
    .insert({
      empresa_id: activa.empresa.id,
      numero_serie: parsed.data.numeroSerie,
      modelo: parsed.data.modelo,
      fabricante: parsed.data.fabricante,
      valor_credito: parsed.data.valorCredito,
      contador_entradas_inicial: parsed.data.contadorEntradasInicial,
      contador_salidas_inicial: parsed.data.contadorSalidasInicial,
      estado: parsed.data.estado,
      notas: parsed.data.notas,
    })
    .select("id")
    .single();

  if (error) {
    if (error.code === "23505") {
      return {
        ok: false,
        error: {
          code: "numeroSerieDuplicado",
          fieldErrors: { numeroSerie: ["numeroSerieDuplicado"] },
        },
      };
    }
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidatePath("/maquinas");
  // Devolvemos el id y dejamos que el cliente navegue (router.push). No usamos
  // redirect() aquí: cuando la action se invoca de forma programática (no como
  // <form action>), su NEXT_REDIRECT no llega al try/catch del cliente, que
  // entonces trata el éxito como un error inesperado.
  return { ok: true, data: { id: data.id } };
}

// -----------------------------------------------------------------------------
// actualizarMaquina
// -----------------------------------------------------------------------------

export async function actualizarMaquina(
  maquinaId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  const activa = await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(maquinaId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = MaquinaInputSchema.safeParse(parseMaquinaForm(formData));
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
    .from("maquina")
    .update({
      numero_serie: parsed.data.numeroSerie,
      modelo: parsed.data.modelo,
      fabricante: parsed.data.fabricante,
      valor_credito: parsed.data.valorCredito,
      contador_entradas_inicial: parsed.data.contadorEntradasInicial,
      contador_salidas_inicial: parsed.data.contadorSalidasInicial,
      estado: parsed.data.estado,
      notas: parsed.data.notas,
    })
    .eq("empresa_id", activa.empresa.id)
    .eq("id", maquinaId);

  if (error) {
    if (error.code === "23505") {
      return {
        ok: false,
        error: {
          code: "numeroSerieDuplicado",
          fieldErrors: { numeroSerie: ["numeroSerieDuplicado"] },
        },
      };
    }
    return { ok: false, error: { code: "guardarFallido" } };
  }

  revalidatePath("/maquinas");
  revalidatePath(`/maquinas/${maquinaId}`);
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// eliminarMaquina
// -----------------------------------------------------------------------------

export async function eliminarMaquina(maquinaId: string): Promise<ActionResult> {
  const activa = await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(maquinaId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = createClient();
  const { error } = await supabase
    .from("maquina")
    .delete()
    .eq("empresa_id", activa.empresa.id)
    .eq("id", maquinaId);

  if (error) {
    if (error.code === "23503") {
      // FK violation: la máquina está referenciada por alguna instalación.
      return { ok: false, error: { code: "maquinaEnUso" } };
    }
    return { ok: false, error: { code: "borrarFallido" } };
  }

  revalidatePath("/maquinas");
  // Igual que en crear: devolvemos el resultado y el cliente navega. Con
  // redirect() el NEXT_REDIRECT no llegaría al await del componente.
  return { ok: true, data: undefined };
}
