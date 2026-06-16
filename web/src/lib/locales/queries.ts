import { createClient } from "@/lib/supabase/server";

import { type Local, type LocalRow, mapLocalRow } from "./types";

export interface ListarLocalesFiltros {
  /** Filtro libre sobre `nombre` (case-insensitive, ilike). */
  busqueda?: string | null;
}

/**
 * Lista los locales de una empresa aplicando los filtros opcionales.
 *
 * RLS ya restringe a los locales visibles para el usuario actual; aquí
 * sumamos `eq("empresa_id", ...)` por claridad y para que la query sea
 * obvia leyéndola.
 */
export async function listarLocales(
  empresaId: string,
  filtros: ListarLocalesFiltros = {},
): Promise<Local[]> {
  const supabase = await createClient();
  let query = supabase
    .from("local")
    .select("*")
    .eq("empresa_id", empresaId)
    .order("nombre", { ascending: true });

  const busqueda = filtros.busqueda?.trim();
  if (busqueda) {
    // ilike escapando los wildcards SQL.
    const pattern = `%${busqueda.replace(/[%_]/g, (c) => `\\${c}`)}%`;
    query = query.ilike("nombre", pattern);
  }

  const { data, error } = await query.returns<LocalRow[]>();
  if (error) {
    throw new Error(`No se pudieron cargar los locales: ${error.message}`);
  }
  return (data ?? []).map(mapLocalRow);
}

/**
 * Obtiene un local por id dentro de la empresa indicada. Devuelve
 * `null` si no existe o el usuario no lo puede ver (RLS).
 */
export async function obtenerLocal(empresaId: string, localId: string): Promise<Local | null> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("local")
    .select("*")
    .eq("empresa_id", empresaId)
    .eq("id", localId)
    .returns<LocalRow[]>()
    .maybeSingle();

  if (error) {
    throw new Error(`No se pudo cargar el local: ${error.message}`);
  }
  return data ? mapLocalRow(data) : null;
}
