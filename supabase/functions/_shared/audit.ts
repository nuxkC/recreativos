/**
 * T-202 — Helper de auditoría para Edge Functions.
 *
 * Cubre los eventos que SOLO conoce la Edge Function y que no se deducen de
 * un único cambio de fila (y por tanto no los capturan los triggers SQL):
 * invitaciones y cambios de rol. El resto de eventos de dominio
 * (recaudación, cambio de placa, instalación) se auditan vía triggers en BD.
 *
 * Diseño:
 *   - `construirRegistroAuditoria()` es PURA (sin red ni entorno): construye
 *     la fila a insertar y FILTRA cualquier clave con PII. Testeable.
 *   - `registrarAuditoria()` inserta con el cliente service_role (la RLS de
 *     `audit_log` no permite INSERT a clientes). Es best-effort: nunca lanza,
 *     para no bloquear la acción de negocio ya completada.
 *
 * Convención de privacidad (conventions §Logging): `datos` NUNCA contiene
 * email, firma, observaciones ni datos del titular del local.
 */

import type { SupabaseClient } from "@supabase/supabase-js";

/** Acciones que audita el helper (las que no cubren los triggers SQL). */
export type AccionAuditoriaEdge = "usuario_invitado" | "rol_cambiado";

/** Claves prohibidas en `datos` por contener (o poder contener) PII. */
export const CLAVES_PII_PROHIBIDAS: readonly string[] = [
  "email",
  "correo",
  "firma",
  "firma_url",
  "firma_base64",
  "observaciones",
  "notas",
  "telefono",
  "titular_nombre",
  "nombre_completo",
  "cif_o_nif",
  "nif",
  "cif",
];

export interface RegistroAuditoriaInput {
  empresaId: string;
  actorUsuarioId: string | null;
  accion: AccionAuditoriaEdge;
  entidadTabla: string;
  entidadId: string | null;
  datos?: Record<string, unknown>;
}

/** Fila lista para insertar en `audit_log` (snake_case como la tabla). */
export interface RegistroAuditoriaRow {
  empresa_id: string;
  actor_usuario_id: string | null;
  accion: AccionAuditoriaEdge;
  entidad_tabla: string;
  entidad_id: string | null;
  datos: Record<string, unknown>;
}

/**
 * Construye la fila de auditoría a partir del input, eliminando claves con
 * PII y valores `undefined`. Pura y determinista.
 */
export function construirRegistroAuditoria(
  input: RegistroAuditoriaInput,
): RegistroAuditoriaRow {
  return {
    empresa_id: input.empresaId,
    actor_usuario_id: input.actorUsuarioId,
    accion: input.accion,
    entidad_tabla: input.entidadTabla,
    entidad_id: input.entidadId,
    datos: sanearDatos(input.datos),
  };
}

/**
 * Elimina claves con PII (y valores `undefined`) de `datos`. Comparación de
 * claves case-insensitive para no dejar pasar variantes en mayúsculas.
 */
export function sanearDatos(
  datos: Record<string, unknown> | undefined,
): Record<string, unknown> {
  if (!datos) return {};
  const prohibidas = new Set(CLAVES_PII_PROHIBIDAS.map((k) => k.toLowerCase()));
  const limpio: Record<string, unknown> = {};
  for (const [clave, valor] of Object.entries(datos)) {
    if (valor === undefined) continue;
    if (prohibidas.has(clave.toLowerCase())) continue;
    limpio[clave] = valor;
  }
  return limpio;
}

/**
 * Inserta un evento de auditoría. Best-effort: registra el fallo en el log
 * estructurado (sin PII) y NO lanza, para no afectar a la operación de
 * negocio que ya se completó.
 *
 * Requiere un cliente `service_role`: la RLS de `audit_log` bloquea el
 * INSERT a cualquier cliente con JWT de usuario.
 */
export async function registrarAuditoria(
  service: SupabaseClient,
  input: RegistroAuditoriaInput,
): Promise<void> {
  const row = construirRegistroAuditoria(input);
  try {
    const { error } = await service.from("audit_log").insert(row);
    if (error) {
      console.error(JSON.stringify({
        level: "error",
        msg: "audit_log_insert_failed",
        accion: row.accion,
        entidad_tabla: row.entidad_tabla,
        error: error.message,
      }));
    }
  } catch (err) {
    console.error(JSON.stringify({
      level: "error",
      msg: "audit_log_insert_threw",
      accion: row.accion,
      error: err instanceof Error ? err.message : String(err),
    }));
  }
}
