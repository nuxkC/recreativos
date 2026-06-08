import { createClient } from "@/lib/supabase/server";

import {
  ESTADOS_INSTALACION,
  type EstadoInstalacion,
  type Instalacion,
  type InstalacionRow,
  type LicenciaResumen,
  type LocalResumen,
  type MaquinaResumen,
  mapInstalacionRow,
} from "./types";

/** Columnas seleccionadas en cada query: incluyen los joins con licencia/máquina/local. */
const SELECT_COLUMNS = `
  id, empresa_id, maquina_id, licencia_id, local_id,
  fecha_inicio, fecha_fin,
  tasa_semanal, porcentaje_local,
  contador_entradas_base, contador_salidas_base,
  estado, notas, created_at, updated_at,
  licencia:licencia_id (id, numero),
  maquina:maquina_id (id, numero_serie, modelo),
  local:local_id (id, nombre)
`;

export interface ListarInstalacionesFiltros {
  estado?: EstadoInstalacion | null;
  /** Filtrar por local_id. */
  localId?: string | null;
}

/**
 * Lista las instalaciones de una empresa. Resultados ordenados:
 * primero las activas (más recientes arriba), luego las cerradas.
 */
export async function listarInstalaciones(
  empresaId: string,
  filtros: ListarInstalacionesFiltros = {},
): Promise<Instalacion[]> {
  const supabase = createClient();
  let query = supabase
    .from("instalacion")
    .select(SELECT_COLUMNS)
    .eq("empresa_id", empresaId)
    .order("estado", { ascending: true })
    .order("fecha_inicio", { ascending: false });

  if (filtros.estado && ESTADOS_INSTALACION.includes(filtros.estado)) {
    query = query.eq("estado", filtros.estado);
  }
  if (filtros.localId) {
    query = query.eq("local_id", filtros.localId);
  }

  const { data, error } = await query.returns<InstalacionRow[]>();
  if (error) {
    throw new Error(`No se pudieron cargar las instalaciones: ${error.message}`);
  }
  return (data ?? []).map(mapInstalacionRow);
}

export async function obtenerInstalacion(
  empresaId: string,
  instalacionId: string,
): Promise<Instalacion | null> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("instalacion")
    .select(SELECT_COLUMNS)
    .eq("empresa_id", empresaId)
    .eq("id", instalacionId)
    .returns<InstalacionRow[]>()
    .maybeSingle();

  if (error) {
    throw new Error(`No se pudo cargar la instalación: ${error.message}`);
  }
  return data ? mapInstalacionRow(data) : null;
}

/**
 * Lista de licencias seleccionables al crear instalación.
 *
 * `disponiblesSolo=true` filtra a las que NO tienen una instalación
 * activa en marcha (índice único parcial uq_instalacion_licencia_activa).
 * En modo `false` se devuelven todas las licencias activas de la empresa.
 */
export async function listarLicenciasResumen(
  empresaId: string,
  disponiblesSolo: boolean,
): Promise<LicenciaResumen[]> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("licencia")
    .select("id, numero, estado, instalaciones:instalacion!licencia_id (id, estado)")
    .eq("empresa_id", empresaId)
    .order("numero", { ascending: true })
    .returns<
      Array<{
        id: string;
        numero: string;
        estado: string;
        instalaciones: Array<{ id: string; estado: string }>;
      }>
    >();

  if (error) {
    throw new Error(`No se pudieron cargar las licencias: ${error.message}`);
  }

  return (data ?? [])
    .filter((row) => {
      if (row.estado !== "activa") return false;
      if (!disponiblesSolo) return true;
      return !row.instalaciones.some((inst) => inst.estado === "activa");
    })
    .map((row) => ({ id: row.id, numero: row.numero }));
}

/**
 * Lista de máquinas seleccionables al crear instalación. Mismo criterio
 * que `listarLicenciasResumen`: si `disponiblesSolo`, excluye las que
 * tienen una instalación activa en marcha.
 */
export async function listarMaquinasResumen(
  empresaId: string,
  disponiblesSolo: boolean,
): Promise<MaquinaResumen[]> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("maquina")
    .select("id, numero_serie, modelo, estado, instalaciones:instalacion!maquina_id (id, estado)")
    .eq("empresa_id", empresaId)
    .order("numero_serie", { ascending: true })
    .returns<
      Array<{
        id: string;
        numero_serie: string;
        modelo: string | null;
        estado: string;
        instalaciones: Array<{ id: string; estado: string }>;
      }>
    >();

  if (error) {
    throw new Error(`No se pudieron cargar las máquinas: ${error.message}`);
  }

  return (data ?? [])
    .filter((row) => {
      // Una máquina dada de baja o averiada también deja de estar disponible
      // para nuevas instalaciones.
      if (row.estado === "baja") return false;
      if (!disponiblesSolo) return true;
      return !row.instalaciones.some((inst) => inst.estado === "activa");
    })
    .map((row) => ({
      id: row.id,
      numeroSerie: row.numero_serie,
      modelo: row.modelo,
    }));
}

/** Lista todos los locales de la empresa (no hay restricción de unicidad). */
export async function listarLocalesResumen(empresaId: string): Promise<LocalResumen[]> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("local")
    .select("id, nombre")
    .eq("empresa_id", empresaId)
    .order("nombre", { ascending: true })
    .returns<LocalResumen[]>();

  if (error) {
    throw new Error(`No se pudieron cargar los locales: ${error.message}`);
  }
  return data ?? [];
}
