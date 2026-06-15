"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { requireRol } from "@/lib/auth/guards";
import { ROLES_ADMIN, ROLES_GESTION } from "@/lib/auth/roles";
import { createClient } from "@/lib/supabase/server";

import { PrestamoInputSchema, RecuperacionEfectivoSchema } from "./schemas";

/** Convención compartida con el resto de CRUDs. `code` es clave i18n. */
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

/**
 * Traduce los ERRCODE que lanzan las RPCs SECURITY DEFINER a códigos i18n.
 * `42501` = sin permiso (rol/tenant); `23514` = el importe supera el saldo
 * vivo; `22023` = la deuda no está abierta o el dato es inválido.
 */
function mapDeudaError(error: { code?: string | null }): string {
  switch (error.code) {
    case "42501":
      return "sinPermiso";
    case "23514":
      return "importeSuperaSaldo";
    case "22023":
      return "operacionInvalida";
    case "no_data_found":
      return "noEncontrada";
    default:
      return "guardarFallido";
  }
}

/** Revalida la ficha del local y el dashboard (tarjeta "capital en la calle"). */
function revalidarDeuda(localId: string): void {
  revalidatePath(`/locales/${localId}`);
  revalidatePath("/dashboard");
}

// -----------------------------------------------------------------------------
// crearPrestamo
// -----------------------------------------------------------------------------

export async function crearPrestamo(
  localId: string,
  _prevState: ActionResult<{ id: string }> | null,
  formData: FormData,
): Promise<ActionResult<{ id: string }>> {
  const activa = await requireRol(ROLES_GESTION);

  if (!IdSchema.safeParse(localId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = PrestamoInputSchema.safeParse({
    principal: formData.get("principal") ?? "",
    fecha: formData.get("fecha") ?? "",
    notas: formData.get("notas") ?? "",
  });
  if (!parsed.success) {
    return {
      ok: false,
      error: { code: "validacion", fieldErrors: fieldErrorsFromZod(parsed.error) },
    };
  }

  const supabase = createClient();
  const { data, error } = await supabase.rpc("crear_prestamo", {
    p_empresa_id: activa.empresa.id,
    p_local_id: localId,
    // El principal viaja como string numérico (precisión decimal exacta).
    p_principal: parsed.data.principal,
    p_fecha: parsed.data.fecha,
    p_notas: parsed.data.notas,
  });

  if (error) {
    return { ok: false, error: { code: mapDeudaError(error) } };
  }

  revalidarDeuda(localId);
  return { ok: true, data: { id: data } };
}

// -----------------------------------------------------------------------------
// registrarRecuperacionEfectivo — abono manual a una deuda
// -----------------------------------------------------------------------------

export async function registrarRecuperacionEfectivo(
  creditoId: string,
  localId: string,
  _prevState: ActionResult<{ id: string }> | null,
  formData: FormData,
): Promise<ActionResult<{ id: string }>> {
  await requireRol(ROLES_GESTION);

  if (!IdSchema.safeParse(creditoId).success || !IdSchema.safeParse(localId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = RecuperacionEfectivoSchema.safeParse({
    importe: formData.get("importe") ?? "",
    notas: formData.get("notas") ?? "",
  });
  if (!parsed.success) {
    return {
      ok: false,
      error: { code: "validacion", fieldErrors: fieldErrorsFromZod(parsed.error) },
    };
  }

  const supabase = createClient();
  const { data, error } = await supabase.rpc("registrar_recuperacion_efectivo", {
    p_credito_id: creditoId,
    p_importe: parsed.data.importe,
    p_notas: parsed.data.notas,
  });

  if (error) {
    return { ok: false, error: { code: mapDeudaError(error) } };
  }

  revalidarDeuda(localId);
  return { ok: true, data: { id: data } };
}

// -----------------------------------------------------------------------------
// condonarCredito — perdonar una deuda abierta (acción sensible: admin)
// -----------------------------------------------------------------------------

export async function condonarCredito(creditoId: string, localId: string): Promise<ActionResult> {
  await requireRol(ROLES_ADMIN);

  if (!IdSchema.safeParse(creditoId).success || !IdSchema.safeParse(localId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = createClient();
  const { error } = await supabase.rpc("condonar_credito", {
    p_credito_id: creditoId,
    p_notas: null,
  });

  if (error) {
    return { ok: false, error: { code: mapDeudaError(error) } };
  }

  revalidarDeuda(localId);
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// setPorcentajeRecuperacionLocal — override del % del local (null = heredar)
// -----------------------------------------------------------------------------

export async function setPorcentajeRecuperacionLocal(
  localId: string,
  porcentaje: number | null,
): Promise<ActionResult> {
  await requireRol(ROLES_GESTION);

  if (!IdSchema.safeParse(localId).success) {
    return { ok: false, error: { code: "idInvalido" } };
  }
  if (
    porcentaje !== null &&
    (!Number.isInteger(porcentaje) || porcentaje < 0 || porcentaje > 100)
  ) {
    return { ok: false, error: { code: "porcentajeRecuperacionRango" } };
  }

  const supabase = createClient();
  const { error } = await supabase.rpc("set_porcentaje_recuperacion_local", {
    p_local_id: localId,
    p_porcentaje: porcentaje,
  });

  if (error) {
    return { ok: false, error: { code: mapDeudaError(error) } };
  }

  revalidarDeuda(localId);
  return { ok: true, data: undefined };
}
