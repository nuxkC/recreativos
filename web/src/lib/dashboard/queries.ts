import { addDays, differenceInCalendarMonths, endOfMonth, startOfMonth, subMonths } from "date-fns";

import { createClient } from "@/lib/supabase/server";

/**
 * Queries de agregación para el dashboard. Pensadas para empresas
 * pequeñas/medianas (≲ 500 instalaciones, ≲ 2000 recaudaciones/mes):
 * sumamos client-side sobre los rangos pertinentes en lugar de pedir
 * RPCs de agregación. Cuando el dataset crezca migramos a vistas
 * materializadas o RPC SQL.
 */

export interface RecaudacionMes {
  /** Suma de `recaudacion_bruta` (firme) del mes en €. */
  bruto: number;
  /** Suma de `recaudacion_neta` (firme) del mes en €. */
  neto: number;
  /** Suma de `parte_empresa` (firme) del mes en €. */
  parteEmpresa: number;
  /** Cantidad de recaudaciones firmes del mes. */
  recuento: number;
}

const ZERO_MES: RecaudacionMes = { bruto: 0, neto: 0, parteEmpresa: 0, recuento: 0 };

async function obtenerSumaRecaudacionesMes(
  empresaId: string,
  inicio: Date,
  finExclusivo: Date,
): Promise<RecaudacionMes> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("recaudacion")
    .select("recaudacion_bruta, recaudacion_neta, parte_empresa")
    .eq("empresa_id", empresaId)
    .eq("estado", "firme")
    .gte("fecha", inicio.toISOString())
    .lt("fecha", finExclusivo.toISOString())
    .returns<
      Array<{
        recaudacion_bruta: string;
        recaudacion_neta: string;
        parte_empresa: string;
      }>
    >();

  if (error) {
    throw new Error(`No se pudo agregar la recaudación: ${error.message}`);
  }
  const filas = data ?? [];
  return filas.reduce<RecaudacionMes>(
    (acc, row) => ({
      bruto: acc.bruto + Number(row.recaudacion_bruta),
      neto: acc.neto + Number(row.recaudacion_neta),
      parteEmpresa: acc.parteEmpresa + Number(row.parte_empresa),
      recuento: acc.recuento + 1,
    }),
    ZERO_MES,
  );
}

export interface ResumenRecaudacion {
  mesActual: RecaudacionMes;
  mesAnterior: RecaudacionMes;
  variacionBruto: number | null;
}

export async function obtenerResumenRecaudacion(
  empresaId: string,
  ahora: Date = new Date(),
): Promise<ResumenRecaudacion> {
  const inicioActual = startOfMonth(ahora);
  const inicioSiguiente = addDays(endOfMonth(ahora), 1);
  const inicioAnterior = startOfMonth(subMonths(ahora, 1));
  const inicioMesActual = inicioActual;

  const [mesActual, mesAnterior] = await Promise.all([
    obtenerSumaRecaudacionesMes(empresaId, inicioActual, inicioSiguiente),
    obtenerSumaRecaudacionesMes(empresaId, inicioAnterior, inicioMesActual),
  ]);

  const variacionBruto =
    mesAnterior.bruto > 0 ? (mesActual.bruto - mesAnterior.bruto) / mesAnterior.bruto : null;

  return { mesActual, mesAnterior, variacionBruto };
}

/**
 * Serie de recaudación bruta firme de los últimos `meses` (índice 0 = mes más
 * antiguo de la ventana, último = mes actual). SOLO alimenta el sparkline del
 * dashboard (coordenadas de pintura, T-8), nunca una cifra mostrada: por eso
 * suma con `Number` como el resto de agregados del dashboard. Una única query
 * sobre la ventana, bucketizada por mes en memoria.
 */
export async function obtenerSerieRecaudacionMensual(
  empresaId: string,
  meses = 6,
  ahora: Date = new Date(),
): Promise<number[]> {
  const supabase = createClient();
  const inicio = startOfMonth(subMonths(ahora, meses - 1));
  const finExclusivo = addDays(endOfMonth(ahora), 1);

  const { data, error } = await supabase
    .from("recaudacion")
    .select("fecha, recaudacion_bruta")
    .eq("empresa_id", empresaId)
    .eq("estado", "firme")
    .gte("fecha", inicio.toISOString())
    .lt("fecha", finExclusivo.toISOString())
    .returns<Array<{ fecha: string; recaudacion_bruta: string }>>();

  if (error) {
    throw new Error(`No se pudo obtener la serie de recaudación: ${error.message}`);
  }

  const serie = new Array<number>(meses).fill(0);
  for (const row of data ?? []) {
    const idx = differenceInCalendarMonths(new Date(row.fecha), inicio);
    if (idx >= 0 && idx < meses) {
      serie[idx] = (serie[idx] ?? 0) + Number(row.recaudacion_bruta);
    }
  }
  return serie;
}

export interface RecuentoMaquinas {
  total: number;
  instaladas: number;
  almacen: number;
  averiadas: number;
  baja: number;
}

export async function contarMaquinasPorEstado(empresaId: string): Promise<RecuentoMaquinas> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("maquina")
    .select("estado")
    .eq("empresa_id", empresaId)
    .returns<Array<{ estado: string }>>();
  if (error) {
    throw new Error(`No se pudieron contar las máquinas: ${error.message}`);
  }
  const recuento: RecuentoMaquinas = {
    total: 0,
    instaladas: 0,
    almacen: 0,
    averiadas: 0,
    baja: 0,
  };
  for (const row of data ?? []) {
    recuento.total += 1;
    if (row.estado === "instalada") recuento.instaladas += 1;
    else if (row.estado === "almacen") recuento.almacen += 1;
    else if (row.estado === "averiada") recuento.averiadas += 1;
    else if (row.estado === "baja") recuento.baja += 1;
  }
  return recuento;
}

export async function contarConflictosPendientes(empresaId: string): Promise<number> {
  const supabase = createClient();
  const { count, error } = await supabase
    .from("recaudacion")
    .select("id", { head: true, count: "exact" })
    .eq("empresa_id", empresaId)
    .eq("conflicto", true)
    .is("revisado_en", null);
  if (error) {
    throw new Error(`No se pudieron contar los conflictos: ${error.message}`);
  }
  return count ?? 0;
}

export interface LicenciaPorCaducar {
  id: string;
  numero: string;
  fechaCaducidad: string;
  diasRestantes: number;
}

export async function listarLicenciasProximasACaducar(
  empresaId: string,
  diasAdelante = 30,
): Promise<LicenciaPorCaducar[]> {
  const supabase = createClient();
  const hoy = new Date();
  const limite = addDays(hoy, diasAdelante);
  const { data, error } = await supabase
    .from("licencia")
    .select("id, numero, fecha_caducidad")
    .eq("empresa_id", empresaId)
    .eq("estado", "activa")
    .not("fecha_caducidad", "is", null)
    .lte("fecha_caducidad", limite.toISOString().slice(0, 10))
    .order("fecha_caducidad", { ascending: true })
    .limit(20)
    .returns<
      Array<{
        id: string;
        numero: string;
        fecha_caducidad: string;
      }>
    >();
  if (error) {
    throw new Error(`No se pudieron cargar las licencias por caducar: ${error.message}`);
  }
  const hoyMs = hoy.setHours(0, 0, 0, 0);
  return (data ?? []).map((row) => {
    const cadMs = new Date(row.fecha_caducidad).setHours(0, 0, 0, 0);
    const diasRestantes = Math.round((cadMs - hoyMs) / (1000 * 60 * 60 * 24));
    return {
      id: row.id,
      numero: row.numero,
      fechaCaducidad: row.fecha_caducidad,
      diasRestantes,
    };
  });
}

export interface InstalacionSinRecaudar {
  id: string;
  maquinaNumeroSerie: string | null;
  localNombre: string | null;
  ultimaRecaudacion: string | null;
  diasSinRecaudar: number;
}

/**
 * Devuelve instalaciones activas cuya última recaudación firme tiene más
 * de `diasUmbral` días (o que nunca han tenido recaudación).
 *
 * Implementación: en lugar de hacer un GROUP BY/MAX vía RPC, traemos las
 * instalaciones activas, las recaudaciones firmes recientes (≤ 1000) y
 * agrupamos en memoria. Para el orden de magnitud del MVP es más que
 * suficiente.
 */
export async function listarInstalacionesSinRecaudar(
  empresaId: string,
  diasUmbral = 14,
): Promise<InstalacionSinRecaudar[]> {
  const supabase = createClient();
  const [instRes, recRes] = await Promise.all([
    supabase
      .from("instalacion")
      .select(
        `id,
         maquina:maquina_id (id, numero_serie),
         local:local_id (id, nombre)`,
      )
      .eq("empresa_id", empresaId)
      .eq("estado", "activa")
      .returns<
        Array<{
          id: string;
          maquina: { id: string; numero_serie: string } | null;
          local: { id: string; nombre: string } | null;
        }>
      >(),
    supabase
      .from("recaudacion")
      .select("instalacion_id, fecha")
      .eq("empresa_id", empresaId)
      .eq("estado", "firme")
      .order("fecha", { ascending: false })
      .limit(1000)
      .returns<Array<{ instalacion_id: string; fecha: string }>>(),
  ]);

  if (instRes.error) {
    throw new Error(`No se pudieron cargar las instalaciones: ${instRes.error.message}`);
  }
  if (recRes.error) {
    throw new Error(`No se pudieron cargar las recaudaciones: ${recRes.error.message}`);
  }

  const ultimas = new Map<string, string>();
  for (const r of recRes.data ?? []) {
    if (!ultimas.has(r.instalacion_id)) {
      ultimas.set(r.instalacion_id, r.fecha);
    }
  }

  const ahoraMs = Date.now();
  const umbralMs = diasUmbral * 24 * 60 * 60 * 1000;

  return (instRes.data ?? [])
    .map<InstalacionSinRecaudar | null>((inst) => {
      const ultima = ultimas.get(inst.id) ?? null;
      const ultimaMs = ultima ? new Date(ultima).getTime() : 0;
      const diasSinRecaudar = ultima
        ? Math.floor((ahoraMs - ultimaMs) / (1000 * 60 * 60 * 24))
        : Number.MAX_SAFE_INTEGER;
      if (ahoraMs - ultimaMs <= umbralMs) return null;
      return {
        id: inst.id,
        maquinaNumeroSerie: inst.maquina?.numero_serie ?? null,
        localNombre: inst.local?.nombre ?? null,
        ultimaRecaudacion: ultima,
        diasSinRecaudar,
      };
    })
    .filter((x): x is InstalacionSinRecaudar => x !== null)
    .sort((a, b) => b.diasSinRecaudar - a.diasSinRecaudar)
    .slice(0, 20);
}

export interface AlertaPendiente {
  id: string;
  tipo: string;
  mensaje: string;
  referenciaId: string | null;
  creadaEn: string;
}

export async function listarAlertasPendientes(
  empresaId: string,
  limite = 10,
): Promise<AlertaPendiente[]> {
  const supabase = createClient();
  const { data, error } = await supabase
    .from("alerta")
    .select("id, tipo, mensaje, referencia_id, creada_en")
    .eq("empresa_id", empresaId)
    .eq("leida", false)
    .order("creada_en", { ascending: false })
    .limit(limite)
    .returns<
      Array<{
        id: string;
        tipo: string;
        mensaje: string;
        referencia_id: string | null;
        creada_en: string;
      }>
    >();
  if (error) {
    throw new Error(`No se pudieron cargar las alertas: ${error.message}`);
  }
  return (data ?? []).map((a) => ({
    id: a.id,
    tipo: a.tipo,
    mensaje: a.mensaje,
    referenciaId: a.referencia_id,
    creadaEn: a.creada_en,
  }));
}
