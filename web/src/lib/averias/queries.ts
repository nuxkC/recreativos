import { createClient } from "@/lib/supabase/server";

import { type Averia, type AveriaRow, mapAveriaRow } from "./types";

/** Columnas de avería + recambios embebidos + nombre del local (snapshot). */
const AVERIA_SELECT = "*, recambios:averia_recambio(*), local(nombre)";

/**
 * Historial de averías de una máquina (más reciente primero), con sus
 * recambios. Atraviesa todas sus instalaciones: la avería cuelga de
 * `maquina_id`, no de la instalación. RLS ya restringe al tenant; sumamos
 * `eq("empresa_id", ...)` por claridad.
 */
export async function listarAveriasMaquina(
  empresaId: string,
  maquinaId: string,
): Promise<Averia[]> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("averia")
    .select(AVERIA_SELECT)
    .eq("empresa_id", empresaId)
    .eq("maquina_id", maquinaId)
    .order("fecha_reporte", { ascending: false })
    .returns<AveriaRow[]>();

  if (error) {
    throw new Error(`No se pudieron cargar las averías: ${error.message}`);
  }
  return (data ?? []).map(mapAveriaRow);
}

/**
 * Cuenta las averías ABIERTAS (no resueltas) por máquina de la empresa.
 * Alimenta el indicador de "averías abiertas" del listado de máquinas.
 * Devuelve un mapa maquinaId → nº de averías abiertas.
 */
export async function contarAveriasAbiertasPorMaquina(
  empresaId: string,
): Promise<Record<string, number>> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("averia")
    .select("maquina_id")
    .eq("empresa_id", empresaId)
    .neq("estado", "resuelta")
    .returns<{ maquina_id: string }[]>();

  if (error) {
    throw new Error(`No se pudieron contar las averías: ${error.message}`);
  }

  const conteo: Record<string, number> = {};
  for (const row of data ?? []) {
    conteo[row.maquina_id] = (conteo[row.maquina_id] ?? 0) + 1;
  }
  return conteo;
}
