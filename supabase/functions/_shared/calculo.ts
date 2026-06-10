/**
 * Single Source of Truth del cálculo de recaudación.
 *
 * Esta función se llama desde `calcular-recaudacion` (preview) y desde
 * `crear-recaudacion` (persistencia). Web y Android NUNCA recalculan
 * cifras: consumen el resultado de estas Edge Functions.
 *
 * Reglas (ver `.kiro/specs/recre/design.md §5`):
 *   1. créditos_netos = (Δentradas) − (Δsalidas)
 *   2. bruto = créditos_netos × valor_credito
 *   3. tasa_total = semanas_iso × tasa_semanal
 *   4. si bruto < tasa_total → no procede
 *   5. neto = bruto − tasa_total
 *   6. parte_local = round_half_up(neto × % / 100, 2)
 *   7. parte_empresa = neto − parte_local  (la empresa absorbe el redondeo)
 */

import { Decimal } from "decimal.js";

import type { BaselineInfo, CalculoRecaudacionResult, DenominacionItem } from "./types.ts";

// Configuración global de Decimal: redondeo HALF_UP, suficiente precisión.
Decimal.set({ rounding: Decimal.ROUND_HALF_UP, precision: 30 });

const CENTIMOS = 2;

export interface CalcularInput {
  baseline: BaselineInfo;
  contadorEntradasActual: number;
  contadorSalidasActual: number;
  /** Recibido como string para no perder precisión. */
  valorCredito: string;
  tasaSemanal: string;
  porcentajeLocal: string;
  semanas: number;
}

/**
 * Calcula la recaudación a partir de la baseline y los contadores actuales.
 * Devuelve los importes serializados como string para preservar precisión
 * al cruzar la frontera HTTP.
 */
export function calcularRecaudacion(input: CalcularInput): CalculoRecaudacionResult {
  const valorCredito = new Decimal(input.valorCredito);
  const tasaSemanal = new Decimal(input.tasaSemanal);
  const porcentajeLocal = new Decimal(input.porcentajeLocal);

  const entradasDiff = new Decimal(input.contadorEntradasActual).minus(input.baseline.entradas);
  const salidasDiff = new Decimal(input.contadorSalidasActual).minus(input.baseline.salidas);
  const creditos = entradasDiff.minus(salidasDiff);

  const bruto = creditos.times(valorCredito).toDecimalPlaces(CENTIMOS);
  const tasaTotal = tasaSemanal.times(input.semanas).toDecimalPlaces(CENTIMOS);

  if (bruto.lessThan(tasaTotal)) {
    return {
      procede: false,
      bruto: bruto.toFixed(CENTIMOS),
      semanas: input.semanas,
      tasa_semanal: tasaSemanal.toFixed(CENTIMOS),
      tasa_total: tasaTotal.toFixed(CENTIMOS),
      neto: "0.00",
      porcentaje_local: porcentajeLocal.toFixed(CENTIMOS),
      parte_local: "0.00",
      parte_empresa: "0.00",
      valor_credito: valorCredito.toFixed(CENTIMOS),
      baseline: input.baseline,
    };
  }

  const neto = bruto.minus(tasaTotal); // ya truncado a céntimos
  const parteLocal = neto.times(porcentajeLocal).dividedBy(100).toDecimalPlaces(CENTIMOS);
  const parteEmpresa = neto.minus(parteLocal); // absorbe el redondeo

  return {
    procede: true,
    bruto: bruto.toFixed(CENTIMOS),
    semanas: input.semanas,
    tasa_semanal: tasaSemanal.toFixed(CENTIMOS),
    tasa_total: tasaTotal.toFixed(CENTIMOS),
    neto: neto.toFixed(CENTIMOS),
    porcentaje_local: porcentajeLocal.toFixed(CENTIMOS),
    parte_local: parteLocal.toFixed(CENTIMOS),
    parte_empresa: parteEmpresa.toFixed(CENTIMOS),
    valor_credito: valorCredito.toFixed(CENTIMOS),
    baseline: input.baseline,
  };
}

/**
 * Suma el valor económico de un desglose de denominaciones.
 * Usa Decimal para evitar errores de coma flotante.
 */
export function sumarDesglose(desglose: readonly DenominacionItem[]): string {
  let total = new Decimal(0);
  for (const item of desglose) {
    total = total.plus(new Decimal(item.denominacion).times(item.cantidad));
  }
  return total.toDecimalPlaces(CENTIMOS).toFixed(CENTIMOS);
}

/** Compara dos importes en string con tolerancia a representación. */
export function importesIguales(a: string, b: string): boolean {
  return new Decimal(a).equals(new Decimal(b));
}
