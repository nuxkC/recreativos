import { createClient } from "@/lib/supabase/server";

import {
  type AccionAuditoria,
  type EntidadAuditoria,
  type EventoAuditoria,
  type EventoAuditoriaRow,
  mapEventoAuditoriaRow,
} from "./types";

export interface ListarEventosAuditoriaFiltros {
  accion?: AccionAuditoria | null;
  entidad?: EntidadAuditoria | null;
  desde?: string | null;
  hasta?: string | null;
}

/**
 * Lista los eventos de auditoría de la empresa (más recientes primero).
 *
 * El actor (`actor_usuario_id`) no tiene FK en BBDD a propósito, así que
 * resolvemos los nombres con una segunda consulta a `usuario` y los unimos
 * en memoria. La RLS de `usuario` permite ver a los compañeros de empresa.
 */
export async function listarEventosAuditoria(
  empresaId: string,
  filtros: ListarEventosAuditoriaFiltros = {},
): Promise<EventoAuditoria[]> {
  const supabase = createClient();
  let query = supabase
    .from("audit_log")
    .select("*")
    .eq("empresa_id", empresaId)
    .order("created_at", { ascending: false })
    .limit(300);

  if (filtros.accion) {
    query = query.eq("accion", filtros.accion);
  }
  if (filtros.entidad) {
    query = query.eq("entidad_tabla", filtros.entidad);
  }
  if (filtros.desde) {
    query = query.gte("created_at", `${filtros.desde}T00:00:00Z`);
  }
  if (filtros.hasta) {
    query = query.lte("created_at", `${filtros.hasta}T23:59:59Z`);
  }

  const { data, error } = await query.returns<EventoAuditoriaRow[]>();
  if (error) {
    throw new Error(`No se pudieron cargar los eventos de auditoría: ${error.message}`);
  }
  const rows = data ?? [];

  const nombrePorId = await resolverNombresActores(rows);
  return rows.map((row) =>
    mapEventoAuditoriaRow(
      row,
      row.actor_usuario_id ? (nombrePorId.get(row.actor_usuario_id) ?? null) : null,
    ),
  );
}

async function resolverNombresActores(rows: EventoAuditoriaRow[]): Promise<Map<string, string>> {
  const ids = Array.from(
    new Set(rows.map((row) => row.actor_usuario_id).filter((id): id is string => id !== null)),
  );
  const nombrePorId = new Map<string, string>();
  if (ids.length === 0) {
    return nombrePorId;
  }

  const supabase = createClient();
  const { data, error } = await supabase
    .from("usuario")
    .select("id, nombre_completo")
    .in("id", ids)
    .returns<Array<{ id: string; nombre_completo: string | null }>>();
  // Best-effort: si falla la resolución de nombres mostramos el id sin más.
  if (error || !data) {
    return nombrePorId;
  }
  for (const usuario of data) {
    if (usuario.nombre_completo) {
      nombrePorId.set(usuario.id, usuario.nombre_completo);
    }
  }
  return nombrePorId;
}
