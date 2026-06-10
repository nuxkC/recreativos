import { createClient } from "@/lib/supabase/server";

import { type CambioPlaca, type CambioPlacaRow, mapCambioPlacaRow } from "./types";

const SELECT_COLUMNS = `
  *,
  instalacion:instalacion_id (
    id,
    licencia:licencia_id (id, numero),
    maquina:maquina_id (id, numero_serie, modelo),
    local:local_id (id, nombre)
  ),
  usuario:usuario_id (id, nombre_completo)
`;

export interface ListarCambiosPlacaFiltros {
  instalacionId?: string | null;
  localId?: string | null;
  desde?: string | null;
  hasta?: string | null;
}

export async function listarCambiosPlaca(
  empresaId: string,
  filtros: ListarCambiosPlacaFiltros = {},
): Promise<CambioPlaca[]> {
  const supabase = createClient();
  let query = supabase
    .from("cambio_placa")
    .select(SELECT_COLUMNS)
    .eq("empresa_id", empresaId)
    .order("fecha", { ascending: false })
    .limit(200);

  if (filtros.instalacionId) {
    query = query.eq("instalacion_id", filtros.instalacionId);
  }
  if (filtros.localId) {
    query = query.eq("instalacion.local_id", filtros.localId);
  }
  if (filtros.desde) {
    query = query.gte("fecha", `${filtros.desde}T00:00:00Z`);
  }
  if (filtros.hasta) {
    query = query.lte("fecha", `${filtros.hasta}T23:59:59Z`);
  }

  const { data, error } = await query.returns<CambioPlacaRow[]>();
  if (error) {
    throw new Error(`No se pudieron cargar los cambios de placa: ${error.message}`);
  }
  // Cuando filtramos por local_id, postgREST devuelve filas con
  // `instalacion = null` para las que no cumplen el filtro: las
  // descartamos client-side.
  const filtered = filtros.localId
    ? (data ?? []).filter((row) => row.instalacion !== null)
    : (data ?? []);
  return filtered.map(mapCambioPlacaRow);
}

export async function obtenerCambioPlaca(
  empresaId: string,
  cambioId: string,
): Promise<CambioPlaca | null> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("cambio_placa")
    .select(SELECT_COLUMNS)
    .eq("empresa_id", empresaId)
    .eq("id", cambioId)
    .returns<CambioPlacaRow[]>()
    .maybeSingle();
  if (error) {
    throw new Error(`No se pudo cargar el cambio de placa: ${error.message}`);
  }
  return data ? mapCambioPlacaRow(data) : null;
}

/**
 * Genera una signed URL (10 min) para la foto adjunta. Las fotos viven
 * en el bucket privado `cambios-placa`.
 */
export async function obtenerSignedUrlFoto(cambio: CambioPlaca): Promise<string | null> {
  if (!cambio.fotoUrl) return null;
  const supabase = createClient();
  const { data, error } = await supabase.storage
    .from("cambios-placa")
    .createSignedUrl(cambio.fotoUrl, 600);
  if (error || !data) return null;
  return data.signedUrl;
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
