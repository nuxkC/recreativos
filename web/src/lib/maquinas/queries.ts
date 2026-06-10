import { createClient } from "@/lib/supabase/server";

import {
  ESTADOS_MAQUINA,
  type EstadoMaquina,
  type Maquina,
  type MaquinaRow,
  mapMaquinaRow,
} from "./types";

export interface ListarMaquinasFiltros {
  /** Filtro libre sobre `numero_serie` (case-insensitive, ilike). */
  busqueda?: string | null;
  /** Filtro exacto por estado, o `null` para todos. */
  estado?: EstadoMaquina | null;
}

/**
 * Lista las máquinas de una empresa aplicando los filtros opcionales.
 *
 * RLS ya restringe a las máquinas visibles para el usuario actual; aquí
 * sumamos `eq("empresa_id", ...)` por claridad y para que la query sea
 * obvia leyéndola.
 */
export async function listarMaquinas(
  empresaId: string,
  filtros: ListarMaquinasFiltros = {},
): Promise<Maquina[]> {
  const supabase = createClient();
  let query = supabase
    .from("maquina")
    .select("*")
    .eq("empresa_id", empresaId)
    .order("numero_serie", { ascending: true });

  const busqueda = filtros.busqueda?.trim();
  if (busqueda) {
    // ilike escapando los wildcards SQL.
    const pattern = `%${busqueda.replace(/[%_]/g, (c) => `\\${c}`)}%`;
    query = query.ilike("numero_serie", pattern);
  }

  if (filtros.estado && ESTADOS_MAQUINA.includes(filtros.estado)) {
    query = query.eq("estado", filtros.estado);
  }

  const { data, error } = await query.returns<MaquinaRow[]>();
  if (error) {
    throw new Error(`No se pudieron cargar las máquinas: ${error.message}`);
  }
  return (data ?? []).map(mapMaquinaRow);
}

/**
 * Obtiene una máquina por id dentro de la empresa indicada. Devuelve
 * `null` si no existe o el usuario no la puede ver (RLS).
 */
export async function obtenerMaquina(
  empresaId: string,
  maquinaId: string,
): Promise<Maquina | null> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("maquina")
    .select("*")
    .eq("empresa_id", empresaId)
    .eq("id", maquinaId)
    .returns<MaquinaRow[]>()
    .maybeSingle();

  if (error) {
    throw new Error(`No se pudo cargar la máquina: ${error.message}`);
  }
  return data ? mapMaquinaRow(data) : null;
}
