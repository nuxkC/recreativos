import { createClient } from "@/lib/supabase/server";

import {
  ESTADOS_RECAUDACION,
  type EstadoRecaudacion,
  type Recaudacion,
  type RecaudacionRow,
  mapRecaudacionRow,
} from "./types";

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

export interface ListarRecaudacionesFiltros {
  /** Filtra por estado exacto, o `"conflicto"` para `conflicto=true`. */
  estado?: EstadoRecaudacion | "conflicto" | null;
  instalacionId?: string | null;
  localId?: string | null;
  /** Inclusive, formato YYYY-MM-DD. */
  desde?: string | null;
  /** Inclusive, formato YYYY-MM-DD. Se interpreta como fin del día. */
  hasta?: string | null;
}

export async function listarRecaudaciones(
  empresaId: string,
  filtros: ListarRecaudacionesFiltros = {},
): Promise<Recaudacion[]> {
  const supabase = await createClient();
  let query = supabase
    .from("recaudacion")
    .select(SELECT_COLUMNS)
    .eq("empresa_id", empresaId)
    .order("fecha", { ascending: false })
    .limit(200);

  if (filtros.estado === "conflicto") {
    query = query.eq("conflicto", true);
  } else if (filtros.estado && ESTADOS_RECAUDACION.includes(filtros.estado)) {
    query = query.eq("estado", filtros.estado);
  }
  if (filtros.instalacionId) {
    query = query.eq("instalacion_id", filtros.instalacionId);
  }
  if (filtros.localId) {
    // Filtramos por el local de la instalación. PostgREST permite filtrar
    // sobre tablas joinadas usando la sintaxis `relacion.columna`.
    query = query.eq("instalacion.local_id", filtros.localId);
  }
  if (filtros.desde) {
    query = query.gte("fecha", `${filtros.desde}T00:00:00Z`);
  }
  if (filtros.hasta) {
    query = query.lte("fecha", `${filtros.hasta}T23:59:59Z`);
  }

  const { data, error } = await query.returns<RecaudacionRow[]>();
  if (error) {
    throw new Error(`No se pudieron cargar las recaudaciones: ${error.message}`);
  }
  // Cuando filtramos por local_id postgREST sigue devolviendo recaudaciones
  // cuya instalacion no cumple el filtro pero con `instalacion = null`.
  const filtered = filtros.localId
    ? (data ?? []).filter((row) => row.instalacion !== null)
    : (data ?? []);
  return filtered.map(mapRecaudacionRow);
}

export async function obtenerRecaudacion(
  empresaId: string,
  recaudacionId: string,
): Promise<Recaudacion | null> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("recaudacion")
    .select(SELECT_COLUMNS)
    .eq("empresa_id", empresaId)
    .eq("id", recaudacionId)
    .returns<RecaudacionRow[]>()
    .maybeSingle();
  if (error) {
    throw new Error(`No se pudo cargar la recaudación: ${error.message}`);
  }
  return data ? mapRecaudacionRow(data) : null;
}

/**
 * Genera signed URLs (10 min) para previsualizar firma + fotos en la
 * página de detalle. Se hace server-side para evitar exponer la session
 * al cliente. Devuelve `null` por cada URL que no exista o no se pueda
 * firmar.
 */
export async function obtenerSignedUrlsEvidencia(recaudacion: Recaudacion): Promise<{
  firma: string | null;
  fotoEntradas: string | null;
  fotoSalidas: string | null;
}> {
  const supabase = await createClient();
  const ttl = 600;
  const sign = async (bucket: string, path: string | null): Promise<string | null> => {
    if (!path) return null;
    const { data, error } = await supabase.storage.from(bucket).createSignedUrl(path, ttl);
    if (error || !data) return null;
    return data.signedUrl;
  };

  const [firma, fotoEntradas, fotoSalidas] = await Promise.all([
    sign("firmas", recaudacion.firmaUrl),
    sign("fotos-contadores", recaudacion.fotoEntradasUrl),
    sign("fotos-contadores", recaudacion.fotoSalidasUrl),
  ]);
  return { firma, fotoEntradas, fotoSalidas };
}

/** Lista de locales para el filtro del listado. */
export async function listarLocalesResumen(
  empresaId: string,
): Promise<Array<{ id: string; nombre: string }>> {
  const supabase = await createClient();
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
