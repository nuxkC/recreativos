/**
 * SSOT del cálculo de RECUPERACIÓN de deuda en una recaudación (T-214).
 *
 * En cada recaudación con `parte_local > 0` y deuda pendiente, se retiene un %
 * de la parte_local del local para amortizar sus deudas (tolva y préstamos).
 * Esta función decide CUÁNTO se retiene y CÓMO se reparte entre las deudas; el
 * cálculo de la recaudación en sí (bruto/neto/reparto) vive aparte en
 * `calculo.ts` y NO se toca: la recuperación no cambia `parte_empresa`.
 *
 * Reglas (ver design.md §5.5):
 *   1. objetivo    = round(parte_local × pct / 100, 2)
 *   2. recuperado  = min(objetivo, Σ saldos)      (nunca más de lo que se debe)
 *   3. imputación  = tolva primero, luego FIFO (deuda más antigua); el técnico
 *      puede reordenar manualmente pasando `orden` (lista de credito_id).
 *   4. pagado_local = parte_local − recuperado     (lo que se lleva el local)
 *
 * Android replica esta misma lógica (espejo bit-a-bit) para el preview offline,
 * igual que `Calculo.kt` replica `calculo.ts`.
 */

import { Decimal } from "decimal.js";

Decimal.set({ rounding: Decimal.ROUND_HALF_UP, precision: 30 });

const CENTIMOS = 2;

export interface CreditoAbierto {
  id: string;
  tipo: "tolva" | "prestamo";
  /** Saldo vivo de la deuda (string para preservar precisión). */
  saldo: string;
  /** Fecha de la deuda (ISO date) para el orden FIFO. */
  fecha: string;
}

export interface AsignacionRecuperacion {
  credito_id: string;
  /** Importe imputado a esa deuda (2 decimales). */
  importe: string;
}

export interface PlanRecuperacion {
  /** Total retenido de la parte_local (2 decimales). */
  recuperado_total: string;
  /** Lo que se lleva el local = parte_local − recuperado_total (2 decimales). */
  pagado_local: string;
  /** Reparto del total entre las deudas, en el orden de imputación. */
  asignaciones: AsignacionRecuperacion[];
}

export interface PlanificarRecuperacionInput {
  parteLocal: string;
  /** % de la parte_local a retener (0..100). El caller resuelve COALESCE(local, empresa). */
  porcentajeRecuperacion: number;
  creditos: readonly CreditoAbierto[];
  /**
   * Orden manual opcional: lista de credito_id. Los listados se imputan primero
   * en ese orden; el resto sigue el orden por defecto (tolva → FIFO).
   */
  orden?: readonly string[];
}

/**
 * Calcula el plan de recuperación. Determinista y puro: mismas entradas →
 * mismas salidas (lo que permite que cliente y servidor coincidan).
 */
export function planificarRecuperacion(input: PlanificarRecuperacionInput): PlanRecuperacion {
  const parteLocal = new Decimal(input.parteLocal);
  const pct = new Decimal(input.porcentajeRecuperacion ?? 0);

  const objetivo = parteLocal.times(pct).dividedBy(100).toDecimalPlaces(CENTIMOS);
  const saldoTotal = input.creditos.reduce(
    (acc, c) => acc.plus(new Decimal(c.saldo)),
    new Decimal(0),
  );

  let restante = Decimal.min(objetivo, saldoTotal);
  if (restante.lessThan(0)) restante = new Decimal(0);

  const ordenados = ordenarCreditos(input.creditos, input.orden);

  const asignaciones: AsignacionRecuperacion[] = [];
  for (const c of ordenados) {
    if (restante.lessThanOrEqualTo(0)) break;
    const saldo = new Decimal(c.saldo);
    if (saldo.lessThanOrEqualTo(0)) continue;
    const imp = Decimal.min(saldo, restante).toDecimalPlaces(CENTIMOS);
    if (imp.lessThanOrEqualTo(0)) continue;
    asignaciones.push({ credito_id: c.id, importe: imp.toFixed(CENTIMOS) });
    restante = restante.minus(imp);
  }

  const recuperado = asignaciones
    .reduce((acc, a) => acc.plus(new Decimal(a.importe)), new Decimal(0))
    .toDecimalPlaces(CENTIMOS);
  const pagado = parteLocal.minus(recuperado).toDecimalPlaces(CENTIMOS);

  return {
    recuperado_total: recuperado.toFixed(CENTIMOS),
    pagado_local: pagado.toFixed(CENTIMOS),
    asignaciones,
  };
}

/**
 * Orden de imputación: tolva antes que préstamo; dentro de cada tipo, FIFO
 * (fecha asc, desempate por id). Si se pasa `orden`, esos ids van primero en el
 * orden indicado y el resto mantiene el orden por defecto.
 */
function ordenarCreditos(
  creditos: readonly CreditoAbierto[],
  orden?: readonly string[],
): CreditoAbierto[] {
  const base = [...creditos].sort((a, b) => {
    if (a.tipo !== b.tipo) return a.tipo === "tolva" ? -1 : 1;
    if (a.fecha !== b.fecha) return a.fecha < b.fecha ? -1 : 1;
    return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
  });

  if (!orden || orden.length === 0) return base;

  const pos = new Map<string, number>();
  orden.forEach((id, i) => pos.set(id, i));
  const rank = (id: string) => (pos.has(id) ? pos.get(id)! : Number.MAX_SAFE_INTEGER);

  // `sort` es estable: los no listados conservan el orden base (tolva → FIFO).
  return base.sort((a, b) => rank(a.id) - rank(b.id));
}
