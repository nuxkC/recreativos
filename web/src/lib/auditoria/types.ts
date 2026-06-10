/**
 * Tipos de la feature `auditoria` (T-202).
 *
 * Espejo de `public.audit_log` en
 * `supabase/migrations/20260522000000_create_audit_log.sql`.
 *
 * Read-only desde la web: la bitácora la pueblan los triggers SQL y el
 * helper `_shared/audit.ts` de las Edge Functions. La web sólo lista y
 * filtra. La RLS limita el SELECT a owner/admin de la empresa.
 */

export const ACCIONES_AUDITORIA = [
  "recaudacion_creada",
  "recaudacion_anulada",
  "conflicto_detectado",
  "conflicto_resuelto",
  "cambio_placa_creado",
  "instalacion_creada",
  "instalacion_cerrada",
  "usuario_invitado",
  "rol_cambiado",
] as const;

export type AccionAuditoria = (typeof ACCIONES_AUDITORIA)[number];

/** Tablas de entidad sobre las que se registran eventos. */
export const ENTIDADES_AUDITORIA = [
  "recaudacion",
  "cambio_placa",
  "instalacion",
  "empresa_usuario",
] as const;

export type EntidadAuditoria = (typeof ENTIDADES_AUDITORIA)[number];

export function isAccionAuditoria(value: string): value is AccionAuditoria {
  return (ACCIONES_AUDITORIA as readonly string[]).includes(value);
}

export function isEntidadAuditoria(value: string): value is EntidadAuditoria {
  return (ENTIDADES_AUDITORIA as readonly string[]).includes(value);
}

export interface EventoAuditoria {
  id: string;
  empresaId: string;
  actorUsuarioId: string | null;
  accion: AccionAuditoria;
  entidadTabla: string;
  entidadId: string | null;
  datos: Record<string, unknown>;
  createdAt: string;
  /** Nombre del actor resuelto aparte (sin FK en BBDD). Null si sistema/desconocido. */
  actorNombre: string | null;
}

export interface EventoAuditoriaRow {
  id: string;
  empresa_id: string;
  actor_usuario_id: string | null;
  accion: string;
  entidad_tabla: string;
  entidad_id: string | null;
  datos: Record<string, unknown> | null;
  created_at: string;
}

export function mapEventoAuditoriaRow(
  row: EventoAuditoriaRow,
  actorNombre: string | null,
): EventoAuditoria {
  return {
    id: row.id,
    empresaId: row.empresa_id,
    actorUsuarioId: row.actor_usuario_id,
    accion: isAccionAuditoria(row.accion) ? row.accion : "recaudacion_creada",
    entidadTabla: row.entidad_tabla,
    entidadId: row.entidad_id,
    datos: row.datos ?? {},
    createdAt: row.created_at,
    actorNombre,
  };
}
