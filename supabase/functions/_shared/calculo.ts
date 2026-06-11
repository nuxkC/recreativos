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
  /**
   * Unidad de redondeo del bruto (config por empresa; 0 = sin redondeo).
   * Si > 0, el bruto se lleva al múltiplo más cercano de esta unidad
   * falseando la lectura de salidas: ese contador ajustado se persiste como
   * real y la diferencia rueda a la siguiente recaudación vía baseline.
   */
  redondeoUnidad?: number;
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

  const brutoReal = creditos.times(valorCredito).toDecimalPlaces(CENTIMOS);
  const tasaTotal = tasaSemanal.times(input.semanas).toDecimalPlaces(CENTIMOS);

  // `procede` se decide con el bruto REAL: el dinero de verdad manda. El
  // redondeo solo cambia cómo se presenta y guarda una recaudación que ya
  // procede; nunca convierte en recaudable lo que no llega a la tasa.
  if (brutoReal.lessThan(tasaTotal)) {
    return {
      procede: false,
      bruto: brutoReal.toFixed(CENTIMOS),
      semanas: input.semanas,
      tasa_semanal: tasaSemanal.toFixed(CENTIMOS),
      tasa_total: tasaTotal.toFixed(CENTIMOS),
      neto: "0.00",
      porcentaje_local: porcentajeLocal.toFixed(CENTIMOS),
      parte_local: "0.00",
      parte_empresa: "0.00",
      valor_credito: valorCredito.toFixed(CENTIMOS),
      baseline: input.baseline,
      contador_salidas_ajustado: input.contadorSalidasActual,
      recaudacion_bruta_real: brutoReal.toFixed(CENTIMOS),
      redondeo_aplicado: 0,
    };
  }

  // Redondeo opcional del bruto (config por empresa). Falseamos la lectura de
  // salidas lo justo para que el bruto caiga en el múltiplo de `redondeoUnidad`
  // más cercano; ese contador ajustado se persiste como real y la diferencia
  // rueda a la siguiente recaudación vía baseline (obtener_baseline). No se
  // pierde ni se inventa dinero: solo se reparte en cortes "redondos".
  const redondeoUnidad = input.redondeoUnidad ?? 0;
  let bruto = brutoReal;
  let salidasAjustado = input.contadorSalidasActual;
  let redondeoAplicado = 0;

  if (redondeoUnidad > 0) {
    const unidad = new Decimal(redondeoUnidad);
    const ratio = brutoReal.dividedBy(unidad);
    let brutoObjetivo = ratio.toDecimalPlaces(0).times(unidad);
    // El redondeo nunca puede dejar el bruto por debajo de la tasa (neto < 0):
    // en la rara franja justo encima de la tasa, redondeamos hacia arriba.
    if (brutoObjetivo.lessThan(tasaTotal)) {
      brutoObjetivo = ratio.toDecimalPlaces(0, Decimal.ROUND_CEIL).times(unidad);
    }
    // El contador es entero: creditosObjetivo se redondea al crédito más
    // cercano. Con `valorCredito` divisor de la unidad (p.ej. 0,20 y 10) el
    // bruto cae exacto; si no, queda en el múltiplo de valorCredito más
    // próximo al objetivo (diferencia < valorCredito, documentada en tests).
    const creditosObjetivo = brutoObjetivo.dividedBy(valorCredito).toDecimalPlaces(0);
    bruto = creditosObjetivo.times(valorCredito).toDecimalPlaces(CENTIMOS);
    // Créditos "de menos" respecto a lo real => subir salidas esa cantidad
    // (más salidas = menos créditos netos = menos bruto), y viceversa.
    const ajuste = creditos.minus(creditosObjetivo);
    salidasAjustado = new Decimal(input.contadorSalidasActual).plus(ajuste).toNumber();
    redondeoAplicado = redondeoUnidad;
  }

  const neto = bruto.minus(tasaTotal); // ya truncado a céntimos
  const parteLocal = neto.times(porcentajeLocal).dividedBy(100).toDecimalPlaces(CENTIMOS);
  const parteEmpresa = neto.minus(parteLocal); // absorbe el redondeo de céntimos

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
    contador_salidas_ajustado: salidasAjustado,
    recaudacion_bruta_real: brutoReal.toFixed(CENTIMOS),
    redondeo_aplicado: redondeoAplicado,
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
