import { createClient } from "@/lib/supabase/server";

import {
  ESTADOS_LICENCIA,
  type EstadoLicencia,
  type Licencia,
  type LicenciaRow,
  mapLicenciaRow,
} from "./types";

export interface ListarLicenciasFiltros {
  /** Filtro libre sobre `numero` (case-insensitive, ilike). */
  busqueda?: string | null;
  /** Filtro exacto por estado, o `null` para todos. */
  estado?: EstadoLicencia | null;
}

/**
 * Lista las licencias de una empresa aplicando los filtros opcionales.
 *
 * RLS ya restringe a las licencias visibles para el usuario actual; aquí
 * sumamos `eq("empresa_id", ...)` por claridad y para que la query sea
 * obvia leyéndola.
 */
export async function listarLicencias(
  empresaId: string,
  filtros: ListarLicenciasFiltros = {},
): Promise<Licencia[]> {
  const supabase = await createClient();
  let query = supabase
    .from("licencia")
    .select("*")
    .eq("empresa_id", empresaId)
    .order("numero", { ascending: true });

  const busqueda = filtros.busqueda?.trim();
  if (busqueda) {
    // ilike escapando los wildcards SQL.
    const pattern = `%${busqueda.replace(/[%_]/g, (c) => `\\${c}`)}%`;
    query = query.ilike("numero", pattern);
  }

  if (filtros.estado && ESTADOS_LICENCIA.includes(filtros.estado)) {
    query = query.eq("estado", filtros.estado);
  }

  const { data, error } = await query.returns<LicenciaRow[]>();
  if (error) {
    throw new Error(`No se pudieron cargar las licencias: ${error.message}`);
  }
  return (data ?? []).map(mapLicenciaRow);
}

/**
 * Obtiene una licencia por id dentro de la empresa indicada. Devuelve
 * `null` si no existe o el usuario no la puede ver (RLS).
 */
export async function obtenerLicencia(
  empresaId: string,
  licenciaId: string,
): Promise<Licencia | null> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("licencia")
    .select("*")
    .eq("empresa_id", empresaId)
    .eq("id", licenciaId)
    .returns<LicenciaRow[]>()
    .maybeSingle();

  if (error) {
    throw new Error(`No se pudo cargar la licencia: ${error.message}`);
  }
  return data ? mapLicenciaRow(data) : null;
}
