import { createClient } from "@/lib/supabase/server";

import type { Recaudacion, RecaudacionRow } from "@/lib/recaudaciones/types";
import { mapRecaudacionRow } from "@/lib/recaudaciones/types";

const SELECT_COLUMNS = `
  *,
  instalacion:instalacion_id (
    id,
    licencia:licencia_id (id, numero),
    maquina:maquina_id (id, numero_serie, modelo),
    local:local_id (id, nombre)
  ),
  tecnico:tecnico_id (id, nombre_completo)
`;

export interface ListarConflictosFiltros {
  /**
   * Si `true` (por defecto) sólo trae los conflictos pendientes
   * (`revisado_en IS NULL`); si `false` incluye los ya resueltos para
   * histórico.
   */
  soloPendientes?: boolean;
  localId?: string | null;
}

/**
 * Lista las recaudaciones con conflicto de la empresa activa.
 *
 * Las recaudaciones con `conflicto = true` son las que el server marcó
 * cuando la baseline enviada por el cliente ya no coincidía con la real
 * al persistir (ver `crear-recaudacion`, T-21). Aquí las traemos con sus
 * cifras cliente y servidor para mostrarlas en la pantalla de Conflictos.
 */
export async function listarConflictos(
  empresaId: string,
  filtros: ListarConflictosFiltros = {},
): Promise<Recaudacion[]> {
  const soloPendientes = filtros.soloPendientes ?? true;
  const supabase = createClient();
  let query = supabase
    .from("recaudacion")
    .select(SELECT_COLUMNS)
    .eq("empresa_id", empresaId)
    .eq("conflicto", true)
    .order("fecha", { ascending: false })
    .limit(200);

  if (soloPendientes) {
    query = query.is("revisado_en", null);
  }
  if (filtros.localId) {
    query = query.eq("instalacion.local_id", filtros.localId);
  }

  const { data, error } = await query.returns<RecaudacionRow[]>();
  if (error) {
    throw new Error(`No se pudieron cargar los conflictos: ${error.message}`);
  }
  const filtered = filtros.localId
    ? (data ?? []).filter((row) => row.instalacion !== null)
    : (data ?? []);
  return filtered.map(mapRecaudacionRow);
}

export async function listarLocalesResumen(
  empresaId: string,
): Promise<Array<{ id: string; nombre: string }>> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("local")
    .select("id, nombre")
    .eq("empresa_id", empresaId)
    .order("nombre", { ascending: true })
    .returns<Array<{ id: string; nombre: string }>>();
  if (error) {
    throw new Error(`No se pudieron cargar los locales: ${error.message}`);
  }
  return data ?? [];
}
