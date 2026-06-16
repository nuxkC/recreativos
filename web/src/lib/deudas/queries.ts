import { subDays } from "date-fns";
import Decimal from "decimal.js";

import { createClient } from "@/lib/supabase/server";

import {
  type CapitalEnLaCalle,
  type CreditoLocal,
  type CreditoLocalRow,
  type LocalSaldo,
  type LocalSaldoRow,
  type Recuperacion,
  type RecuperacionRow,
  mapCreditoLocalRow,
  mapLocalSaldoRow,
  mapRecuperacionRow,
} from "./types";

/**
 * Queries de lectura del módulo de deudas. Todas leen vistas/tablas con RLS
 * (`security_invoker`), por lo que el `eq("empresa_id", ...)` es por claridad:
 * el aislamiento real lo garantiza la política de la empresa activa.
 */

/** % de recuperación por defecto de la empresa (para mostrar el valor heredado). */
export async function obtenerPorcentajeRecuperacionEmpresa(empresaId: string): Promise<number> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("empresa")
    .select("porcentaje_recuperacion")
    .eq("id", empresaId)
    .returns<Array<{ porcentaje_recuperacion: number }>>()
    .maybeSingle();
  if (error) {
    throw new Error(`No se pudo cargar el % de recuperación de la empresa: ${error.message}`);
  }
  return data?.porcentaje_recuperacion ?? 0;
}

/** Un local con su saldo de deuda + nombre, para el listado de la sección Deudas. */
export interface LocalConSaldo extends LocalSaldo {
  nombre: string;
}

/**
 * Todos los locales con su saldo de deuda (los que más deben, primero).
 * Alimenta el índice de la sección Deudas: desde ahí se entra a cada local
 * para gestionar (préstamo/abono/condonar/%). Incluye locales sin deuda para
 * poder darles de alta un préstamo desde la propia sección.
 */
export async function listarLocalesConSaldo(empresaId: string): Promise<LocalConSaldo[]> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("v_local_saldo")
    .select("*, local:local_id (nombre)")
    .eq("empresa_id", empresaId)
    .order("saldo_total", { ascending: false })
    .returns<Array<LocalSaldoRow & { local: { nombre: string } | null }>>();
  if (error) {
    throw new Error(`No se pudieron cargar los locales con saldo: ${error.message}`);
  }
  return (data ?? []).map((row) => ({
    ...mapLocalSaldoRow(row),
    nombre: row.local?.nombre ?? "—",
  }));
}

/** Saldo de deuda agregado del local (solo deudas abiertas). */
export async function obtenerSaldoLocal(
  empresaId: string,
  localId: string,
): Promise<LocalSaldo | null> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("v_local_saldo")
    .select("*")
    .eq("empresa_id", empresaId)
    .eq("local_id", localId)
    .returns<LocalSaldoRow[]>()
    .maybeSingle();
  if (error) {
    throw new Error(`No se pudo cargar el saldo del local: ${error.message}`);
  }
  return data ? mapLocalSaldoRow(data) : null;
}

/** Deudas del local con su saldo vivo. `soloAbiertas` filtra estado='abierto'. */
export async function listarCreditosLocal(
  empresaId: string,
  localId: string,
  soloAbiertas = false,
): Promise<CreditoLocal[]> {
  const supabase = await createClient();
  let query = supabase
    .from("v_credito_local_saldo")
    .select("*")
    .eq("empresa_id", empresaId)
    .eq("local_id", localId)
    // tolva antes que préstamos (mismo orden que la imputación de recuperación;
    // 'tolva' > 'prestamo' alfabéticamente → descendente), y por antigüedad.
    .order("tipo", { ascending: false })
    .order("fecha", { ascending: true });

  if (soloAbiertas) {
    query = query.eq("estado", "abierto");
  }

  const { data, error } = await query.returns<CreditoLocalRow[]>();
  if (error) {
    throw new Error(`No se pudieron cargar las deudas del local: ${error.message}`);
  }
  return (data ?? []).map(mapCreditoLocalRow);
}

/** Libro mayor de abonos del local (más recientes primero). */
export async function listarRecuperacionesLocal(
  empresaId: string,
  localId: string,
  limite = 50,
): Promise<Recuperacion[]> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("recuperacion")
    .select(
      "id, credito_id, origen, importe, recaudacion_id, fecha, notas, credito:credito_id (tipo)",
    )
    .eq("empresa_id", empresaId)
    .eq("local_id", localId)
    .order("fecha", { ascending: false })
    .limit(limite)
    .returns<RecuperacionRow[]>();
  if (error) {
    throw new Error(`No se pudo cargar el libro mayor: ${error.message}`);
  }
  return (data ?? []).map(mapRecuperacionRow);
}

/**
 * "Capital en la calle": suma de los saldos vivos de toda la deuda abierta de
 * la empresa, desglosado en tolva/préstamo, y nº de locales con deuda.
 *
 * Se agrega client-side sobre `v_local_saldo` (una fila por local). Para el
 * orden de magnitud del MVP es suficiente; si crece, se migra a RPC SQL.
 */
export async function obtenerCapitalEnLaCalle(empresaId: string): Promise<CapitalEnLaCalle> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("v_local_saldo")
    .select("saldo_total, saldo_tolva, saldo_prestamo, num_deudas_abiertas")
    .eq("empresa_id", empresaId)
    .returns<
      Array<{
        saldo_total: string;
        saldo_tolva: string;
        saldo_prestamo: string;
        num_deudas_abiertas: number;
      }>
    >();
  if (error) {
    throw new Error(`No se pudo calcular el capital en la calle: ${error.message}`);
  }

  let total = new Decimal(0);
  let tolva = new Decimal(0);
  let prestamo = new Decimal(0);
  let numLocales = 0;
  for (const row of data ?? []) {
    total = total.plus(row.saldo_total);
    tolva = tolva.plus(row.saldo_tolva);
    prestamo = prestamo.plus(row.saldo_prestamo);
    if (Number(row.num_deudas_abiertas) > 0) numLocales += 1;
  }

  return {
    total: total.toFixed(2),
    tolva: tolva.toFixed(2),
    prestamo: prestamo.toFixed(2),
    numLocales,
  };
}

export interface ActividadDeuda {
  /** Importe recuperado (abonos) en la ventana, € money-safe (string). */
  recuperado: string;
  /** Nº de movimientos de recuperación en la ventana. */
  movimientos: number;
  /** Días de la ventana (para el copy). */
  dias: number;
}

/**
 * Actividad reciente de cobro de deuda de la empresa: importe recuperado y nº
 * de movimientos en los últimos `dias`. Es una cifra mostrada → se suma con
 * `Decimal` (money-safe), nunca con `Number`. Alimenta la tarjeta "Actividad"
 * del centro de mando de Deudas (T-239).
 */
export async function obtenerActividadDeuda(empresaId: string, dias = 30): Promise<ActividadDeuda> {
  const supabase = await createClient();
  const desde = subDays(new Date(), dias);
  const { data, error } = await supabase
    .from("recuperacion")
    .select("importe")
    .eq("empresa_id", empresaId)
    .gte("fecha", desde.toISOString())
    .returns<Array<{ importe: string }>>();
  if (error) {
    throw new Error(`No se pudo cargar la actividad de deuda: ${error.message}`);
  }
  const filas = data ?? [];
  let recuperado = new Decimal(0);
  for (const row of filas) {
    recuperado = recuperado.plus(row.importe);
  }
  return { recuperado: recuperado.toFixed(2), movimientos: filas.length, dias };
}
