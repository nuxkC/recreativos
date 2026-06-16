import { parseISO, startOfMonth } from "date-fns";

import { createClient } from "@/lib/supabase/server";

import type {
  AgregadoPorLocal,
  AgregadoPorMaquina,
  InformesData,
  InformesFiltros,
  LocalOpcion,
  PuntoEvolucionMes,
  ResumenInformes,
} from "./types";

/**
 * Queries de informes. Leen exclusivamente las vistas de agregación
 * (`v_recaudaciones_por_local_mes`, `v_recaudaciones_por_maquina_mes`), que ya
 * suman las recaudaciones firme por (empresa, local/maquina, mes) en la zona
 * horaria de la empresa. La web no recalcula nada: agrupa los meses en memoria
 * y adjunta los nombres legibles. El volumen (filas mensuales) es pequeño.
 *
 * El filtro por fechas se aplica sobre el mes ya truncado por la vista.
 */

const MAX_BARRAS = 12;

interface LocalMesRow {
  local_id: string | null;
  mes_local: string;
  num_recaudaciones: number | string;
  bruto_total: string | null;
  neto_total: string | null;
  parte_local_total: string | null;
  parte_empresa_total: string | null;
}

interface MaquinaMesRow {
  maquina_id: string | null;
  mes_local: string;
  num_recaudaciones: number | string;
  bruto_total: string | null;
  neto_total: string | null;
  parte_empresa_total: string | null;
}

function num(value: string | number | null | undefined): number {
  if (value === null || value === undefined || value === "") return 0;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

/** Normaliza el `mes_local` (timestamptz) a un ISO estable `YYYY-MM-01`. */
function claveMes(raw: string): string {
  return `${String(raw).slice(0, 7)}-01`;
}

function mesEnRango(mesIso: string, desde: string | null, hasta: string | null): boolean {
  const mesFecha = parseISO(mesIso);
  if (desde && mesFecha < startOfMonth(parseISO(desde))) return false;
  if (hasta && mesFecha > startOfMonth(parseISO(hasta))) return false;
  return true;
}

/** Locales de la empresa para poblar el selector de filtro. */
export async function listarLocalesInformes(empresaId: string): Promise<LocalOpcion[]> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("local")
    .select("id, nombre")
    .eq("empresa_id", empresaId)
    .order("nombre", { ascending: true })
    .returns<LocalOpcion[]>();
  if (error) {
    throw new Error(`No se pudieron cargar los locales: ${error.message}`);
  }
  return data ?? [];
}

interface MaquinaResumen {
  id: string;
  numero_serie: string;
  modelo: string | null;
}

async function listarMaquinasResumen(empresaId: string): Promise<Map<string, string>> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("maquina")
    .select("id, numero_serie, modelo")
    .eq("empresa_id", empresaId)
    .returns<MaquinaResumen[]>();
  if (error) {
    throw new Error(`No se pudieron cargar las máquinas: ${error.message}`);
  }
  const etiquetas = new Map<string, string>();
  for (const m of data ?? []) {
    etiquetas.set(m.id, m.modelo ? `${m.numero_serie} · ${m.modelo}` : m.numero_serie);
  }
  return etiquetas;
}

/**
 * Conjunto de máquinas asociadas (actual o históricamente) a un local, vía
 * `instalacion`. Se usa para acotar el desglose por máquina cuando hay filtro
 * de local. Best-effort: las vistas agregan por máquina sin distinguir el
 * local de cada recaudación, así que esto restringe a las máquinas vinculadas
 * a ese local.
 */
async function maquinasDelLocal(empresaId: string, localId: string): Promise<Set<string>> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("instalacion")
    .select("maquina_id")
    .eq("empresa_id", empresaId)
    .eq("local_id", localId)
    .returns<Array<{ maquina_id: string }>>();
  if (error) {
    throw new Error(`No se pudieron cargar las instalaciones del local: ${error.message}`);
  }
  return new Set((data ?? []).map((row) => row.maquina_id));
}

export async function obtenerInformes(
  empresaId: string,
  filtros: InformesFiltros = {},
): Promise<InformesData> {
  const supabase = await createClient();
  const localId = filtros.localId ?? null;
  const desde = filtros.desde ?? null;
  const hasta = filtros.hasta ?? null;

  let localMesQuery = supabase
    .from("v_recaudaciones_por_local_mes")
    .select(
      "local_id, mes_local, num_recaudaciones, bruto_total, neto_total, parte_local_total, parte_empresa_total",
    )
    .eq("empresa_id", empresaId);
  if (localId) {
    localMesQuery = localMesQuery.eq("local_id", localId);
  }

  const [localMesRes, maquinaMesRes, localesNombre, maquinaEtiquetas, maquinasLocalSet] =
    await Promise.all([
      localMesQuery.returns<LocalMesRow[]>(),
      supabase
        .from("v_recaudaciones_por_maquina_mes")
        .select(
          "maquina_id, mes_local, num_recaudaciones, bruto_total, neto_total, parte_empresa_total",
        )
        .eq("empresa_id", empresaId)
        .returns<MaquinaMesRow[]>(),
      listarLocalesInformes(empresaId),
      listarMaquinasResumen(empresaId),
      localId ? maquinasDelLocal(empresaId, localId) : Promise.resolve(null),
    ]);

  if (localMesRes.error) {
    throw new Error(`No se pudieron cargar los agregados por local: ${localMesRes.error.message}`);
  }
  if (maquinaMesRes.error) {
    throw new Error(
      `No se pudieron cargar los agregados por máquina: ${maquinaMesRes.error.message}`,
    );
  }

  const nombrePorLocal = new Map(localesNombre.map((l) => [l.id, l.nombre]));

  // --- Evolución mensual (totales de empresa por mes) ---
  const evolucionMap = new Map<string, PuntoEvolucionMes>();
  for (const row of localMesRes.data ?? []) {
    const mes = claveMes(row.mes_local);
    if (!mesEnRango(mes, desde, hasta)) continue;
    const acc = evolucionMap.get(mes) ?? {
      mes,
      brutoTotal: 0,
      netoTotal: 0,
      parteLocalTotal: 0,
      parteEmpresaTotal: 0,
      numRecaudaciones: 0,
    };
    acc.brutoTotal += num(row.bruto_total);
    acc.netoTotal += num(row.neto_total);
    acc.parteLocalTotal += num(row.parte_local_total);
    acc.parteEmpresaTotal += num(row.parte_empresa_total);
    acc.numRecaudaciones += num(row.num_recaudaciones);
    evolucionMap.set(mes, acc);
  }
  const evolucionMensual = Array.from(evolucionMap.values()).sort((a, b) =>
    a.mes.localeCompare(b.mes),
  );

  // --- Resumen del periodo ---
  const resumen = evolucionMensual.reduce<ResumenInformes>(
    (acc, p) => ({
      brutoTotal: acc.brutoTotal + p.brutoTotal,
      netoTotal: acc.netoTotal + p.netoTotal,
      parteEmpresaTotal: acc.parteEmpresaTotal + p.parteEmpresaTotal,
      numRecaudaciones: acc.numRecaudaciones + p.numRecaudaciones,
    }),
    { brutoTotal: 0, netoTotal: 0, parteEmpresaTotal: 0, numRecaudaciones: 0 },
  );

  // --- Desglose por local ---
  const porLocalMap = new Map<string, AgregadoPorLocal>();
  for (const row of localMesRes.data ?? []) {
    if (!row.local_id) continue;
    if (!mesEnRango(claveMes(row.mes_local), desde, hasta)) continue;
    const acc = porLocalMap.get(row.local_id) ?? {
      localId: row.local_id,
      localNombre: nombrePorLocal.get(row.local_id) ?? "—",
      brutoTotal: 0,
      netoTotal: 0,
      parteEmpresaTotal: 0,
      numRecaudaciones: 0,
    };
    acc.brutoTotal += num(row.bruto_total);
    acc.netoTotal += num(row.neto_total);
    acc.parteEmpresaTotal += num(row.parte_empresa_total);
    acc.numRecaudaciones += num(row.num_recaudaciones);
    porLocalMap.set(row.local_id, acc);
  }
  const porLocal = Array.from(porLocalMap.values())
    .sort((a, b) => b.brutoTotal - a.brutoTotal)
    .slice(0, MAX_BARRAS);

  // --- Desglose por máquina ---
  const porMaquinaMap = new Map<string, AgregadoPorMaquina>();
  for (const row of maquinaMesRes.data ?? []) {
    if (!row.maquina_id) continue;
    if (maquinasLocalSet && !maquinasLocalSet.has(row.maquina_id)) continue;
    if (!mesEnRango(claveMes(row.mes_local), desde, hasta)) continue;
    const acc = porMaquinaMap.get(row.maquina_id) ?? {
      maquinaId: row.maquina_id,
      maquinaEtiqueta: maquinaEtiquetas.get(row.maquina_id) ?? "—",
      brutoTotal: 0,
      netoTotal: 0,
      parteEmpresaTotal: 0,
      numRecaudaciones: 0,
    };
    acc.brutoTotal += num(row.bruto_total);
    acc.netoTotal += num(row.neto_total);
    acc.parteEmpresaTotal += num(row.parte_empresa_total);
    acc.numRecaudaciones += num(row.num_recaudaciones);
    porMaquinaMap.set(row.maquina_id, acc);
  }
  const porMaquina = Array.from(porMaquinaMap.values())
    .sort((a, b) => b.brutoTotal - a.brutoTotal)
    .slice(0, MAX_BARRAS);

  return { resumen, evolucionMensual, porLocal, porMaquina };
}
