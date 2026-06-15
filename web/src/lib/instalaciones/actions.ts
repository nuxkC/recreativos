"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";

import { ROLES_GESTION } from "@/lib/auth/roles";
import { requireRol } from "@/lib/auth/guards";
import { createClient } from "@/lib/supabase/server";
import { CerrarInstalacionInputSchema, InstalacionInputSchema } from "./schemas";

/** Convención compartida con el resto de CRUDs (T-31..T-33). */
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

function parseInstalacionForm(formData: FormData): Record<string, unknown> {
  return {
    maquinaId: formData.get("maquinaId") ?? "",
    licenciaId: formData.get("licenciaId") ?? "",
    localId: formData.get("localId") ?? "",
    fechaInicio: formData.get("fechaInicio") ?? "",
    tasaSemanal: formData.get("tasaSemanal") ?? "",
    porcentajeLocal: formData.get("porcentajeLocal") ?? "",
    tolva: formData.get("tolva") ?? "",
    estado: formData.get("estado") ?? "activa",
    notas: formData.get("notas") ?? "",
  };
}

/**
 * Mapea códigos PostgreSQL conocidos a códigos i18n.
 *
 * Los índices únicos parciales `uq_instalacion_maquina_activa` y
 * `uq_instalacion_licencia_activa` reportan `23505`. Distinguimos
 * los dos casos por el detalle del mensaje (postgrest expone el nombre
 * de la constraint en `error.details`).
 */
function mapPgErrorToCode(error: {
  code?: string | null;
  message?: string | null;
  details?: string | null;
}): { code: string; fieldErrors?: Record<string, string[]> } {
  if (error.code === "23505") {
    const haystack = `${error.message ?? ""} ${error.details ?? ""}`.toLowerCase();
    if (haystack.includes("maquina")) {
      return {
        code: "maquinaConInstalacionActiva",
        fieldErrors: { maquinaId: ["maquinaConInstalacionActiva"] },
      };
    }
    if (haystack.includes("licencia")) {
      return {
        code: "licenciaConInstalacionActiva",
        fieldErrors: { licenciaId: ["licenciaConInstalacionActiva"] },
      };
    }
    return { code: "duplicadoDesconocido" };
  }
  if (error.code === "23514") {
    // Algún CHECK violado (fecha_fin coherente, porcentaje fuera de rango...).
    return { code: "constraintViolada" };
  }
  return { code: "guardarFallido" };
}

// -----------------------------------------------------------------------------
// crearInstalacion
// -----------------------------------------------------------------------------

export async function crearInstalacion(
  _prevState: ActionResult<{ id: string }> | null,
  formData: FormData,
): Promise<ActionResult<{ id: string }>> {
  const activa = await requireRol(ROLES_GESTION);

  const parsed = InstalacionInputSchema.safeParse(parseInstalacionForm(formData));
  if (!parsed.success) {
    return {
      ok: false,
      error: {
        code: "validacion",
        fieldErrors: fieldErrorsFromZod(parsed.error),
      },
    };
  }
  // Estado al crear: siempre 'activa'. La cerradura se hace por la
  // Edge Function `cerrar-instalacion`, no por el form.
  const input = { ...parsed.data, estado: "activa" as const };

  const supabase = createClient();

  // Verificación defensiva multi-tenant: las 3 FK deben pertenecer a la
  // misma empresa que la membresía activa. RLS también lo bloquearía
  // pero un mensaje claro es mejor UX que un 403 opaco.
  const [licOk, maqOk, locOk] = await Promise.all([
    supabase
      .from("licencia")
      .select("id", { head: true, count: "exact" })
      .eq("id", input.licenciaId)
      .eq("empresa_id", activa.empresa.id),
    supabase
      .from("maquina")
      .select("id", { head: true, count: "exact" })
      .eq("id", input.maquinaId)
      .eq("empresa_id", activa.empresa.id),
    supabase
      .from("local")
      .select("id", { head: true, count: "exact" })
      .eq("id", input.localId)
      .eq("empresa_id", activa.empresa.id),
  ]);
  if (!licOk.count) {
    return {
      ok: false,
      error: {
        code: "licenciaInvalida",
        fieldErrors: { licenciaId: ["licenciaInvalida"] },
      },
    };
  }
  if (!maqOk.count) {
    return {
      ok: false,
      error: {
        code: "maquinaInvalida",
        fieldErrors: { maquinaId: ["maquinaInvalida"] },
      },
    };
  }
  if (!locOk.count) {
    return {
      ok: false,
      error: {
        code: "localInvalido",
        fieldErrors: { localId: ["localInvalido"] },
      },
    };
  }

  // La escritura directa a `instalacion` está revocada: el alta pasa por la RPC
  // SECURITY DEFINER, que valida rol+tenant y DERIVA la base de contadores de la
  // máquina (por eso no se envía).
  const { data, error } = await supabase.rpc("crear_instalacion", {
    p_empresa_id: activa.empresa.id,
    p_maquina_id: input.maquinaId,
    p_licencia_id: input.licenciaId,
    p_local_id: input.localId,
    p_fecha_inicio: input.fechaInicio,
    p_tasa_semanal: input.tasaSemanal,
    p_porcentaje_local: input.porcentajeLocal,
    p_notas: input.notas ?? null,
    // La tolva (dinero físico dejado en la máquina) viaja como string numérico.
    // El servidor crea la deuda del local por la tolva = porcentaje_local × tolva.
    p_tolva: input.tolva,
  });

  if (error) {
    return { ok: false, error: mapPgErrorToCode(error) };
  }

  revalidatePath("/instalaciones");
  // Devolvemos el id y dejamos que el cliente navegue (router.push). No usamos
  // redirect() aquí: cuando la action se invoca de forma programática (no como
  // <form action>), su NEXT_REDIRECT no llega al try/catch del cliente, que
  // entonces trata el éxito como un error inesperado.
  return { ok: true, data: { id: data } };
}

// -----------------------------------------------------------------------------
// actualizarInstalacion (no toca FKs ni `estado`; el cierre va por Edge)
// -----------------------------------------------------------------------------

export async function actualizarInstalacion(
  instalacionId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(instalacionId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = InstalacionInputSchema.safeParse(parseInstalacionForm(formData));
  if (!parsed.success) {
    return {
      ok: false,
      error: {
        code: "validacion",
        fieldErrors: fieldErrorsFromZod(parsed.error),
      },
    };
  }
  const input = parsed.data;

  const supabase = createClient();
  // Edición vía RPC SECURITY DEFINER. Las FKs y la base de contadores son
  // inmutables: para reasignar máquina/licencia/local hay que cerrar y crear
  // una nueva instalación (mantiene la historia y la baseline coherentes).
  const { error } = await supabase.rpc("actualizar_instalacion", {
    p_id: instalacionId,
    p_fecha_inicio: input.fechaInicio,
    p_tasa_semanal: input.tasaSemanal,
    p_porcentaje_local: input.porcentajeLocal,
    p_notas: input.notas ?? null,
  });

  if (error) {
    return { ok: false, error: mapPgErrorToCode(error) };
  }

  revalidatePath("/instalaciones");
  revalidatePath(`/instalaciones/${instalacionId}`);
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// eliminarInstalacion
// -----------------------------------------------------------------------------

export async function eliminarInstalacion(instalacionId: string): Promise<ActionResult> {
  await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(instalacionId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = createClient();
  const { error } = await supabase.rpc("eliminar_instalacion", {
    p_id: instalacionId,
  });

  if (error) {
    if (error.code === "23503") {
      // Hay recaudaciones, cambios de placa o lecturas que la referencian.
      return { ok: false, error: { code: "instalacionEnUso" } };
    }
    return { ok: false, error: { code: "borrarFallido" } };
  }

  revalidatePath("/instalaciones");
  // Igual que en crear: devolvemos el resultado y el cliente navega. Con
  // redirect() el NEXT_REDIRECT no llegaría al await del componente.
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// cerrarInstalacion — invoca la Edge Function `cerrar-instalacion`
// -----------------------------------------------------------------------------

export async function cerrarInstalacion(
  instalacionId: string,
  _prevState: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(instalacionId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const parsed = CerrarInstalacionInputSchema.safeParse({
    fechaFin: formData.get("fechaFin") ?? "",
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
  }>("cerrar-instalacion", {
    body: {
      instalacion_id: instalacionId,
      fecha_fin: parsed.data.fechaFin,
      notas: parsed.data.notas ?? undefined,
    },
  });

  if (error) {
    // La Edge Function devuelve { error: { code, message } } como JSON.
    // El SDK envuelve ese body en `data` cuando el status no es 2xx.
    const code = data?.code ?? null;
    if (code === "validation_error") {
      return { ok: false, error: { code: "fechaFinInvalida" } };
    }
    if (code === "conflict") {
      return { ok: false, error: { code: "yaCerrada" } };
    }
    if (code === "not_found") {
      return { ok: false, error: { code: "instalacionNoEncontrada" } };
    }
    if (code === "forbidden" || code === "unauthorized") {
      return { ok: false, error: { code: "sinPermiso" } };
    }
    return { ok: false, error: { code: "cerrarFallido" } };
  }

  revalidatePath("/instalaciones");
  revalidatePath(`/instalaciones/${instalacionId}`);
  return { ok: true, data: undefined };
}

// -----------------------------------------------------------------------------
// obtenerSignedUrlBoletin — Edge Function `generar-boletin-instalacion`
// -----------------------------------------------------------------------------

/**
 * Genera (si hace falta) y devuelve una signed URL (10 min) del boletín
 * digital de instalación. La Edge Function es idempotente: si el boletín ya
 * existe lo reutiliza, salvo que se pida `forzar` la regeneración.
 *
 * Lo invocamos vía Server Action para no exponer la lógica al cliente y para
 * que la respuesta no quede cacheada en el browser.
 */
export async function obtenerSignedUrlBoletin(
  instalacionId: string,
  forzar = false,
): Promise<ActionResult<{ url: string }>> {
  await requireRol(ROLES_GESTION);

  const idCheck = IdSchema.safeParse(instalacionId);
  if (!idCheck.success) {
    return { ok: false, error: { code: "idInvalido" } };
  }

  const supabase = createClient();
  const { data, error } = await supabase.functions.invoke<{
    boletin_signed_url?: string;
    regenerado?: boolean;
    code?: string;
    message?: string;
  }>("generar-boletin-instalacion", {
    body: { instalacion_id: instalacionId, forzar },
  });

  if (error || !data?.boletin_signed_url) {
    const code = data?.code ?? null;
    if (code === "not_found") {
      return { ok: false, error: { code: "instalacionNoEncontrada" } };
    }
    if (code === "forbidden" || code === "unauthorized") {
      return { ok: false, error: { code: "sinPermiso" } };
    }
    return { ok: false, error: { code: "boletinFallido" } };
  }

  revalidatePath(`/instalaciones/${instalacionId}`);
  return { ok: true, data: { url: data.boletin_signed_url } };
}
