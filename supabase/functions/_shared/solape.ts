/**
 * T-262 — Detección de tramos de contador solapados ("caso 8").
 *
 * Cada recaudación firme cubre un tramo del contador de entradas
 * `[contador_entradas_anterior, contador_entradas_actual]`. En condiciones
 * normales los tramos son contiguos: el `anterior` de una recaudación es el
 * `actual` (la baseline) de la anterior. Pero la cola de subida offline puede
 * subir recaudaciones DESORDENADAS: una recaudación con `fecha` posterior se
 * persiste antes que otra anterior, y `obtener_baseline()` —que elige baseline
 * solo por `fecha DESC`— le asigna una baseline que hace que su tramo PISE el de
 * otra firme. El segmento común se factura dos veces y nadie lo detecta.
 *
 * Este módulo es PURO (sin BBDD): el caller trae los tramos firmes ya leídos y
 * filtrados al mismo epoch de contador, y aquí solo se comparan rangos.
 */

/** Tramo de contador de entradas de una recaudación firme ya persistida. */
export interface TramoFirme {
  id: string;
  entradasAnterior: number;
  entradasActual: number;
}

/** Tramo de la recaudación nueva (aún sin persistir) a comprobar. */
export interface TramoNuevo {
  entradasAnterior: number;
  entradasActual: number;
}

/**
 * Dos tramos `[a1,b1]` y `[a2,b2]` solapan si `a1 < b2 && a2 < b1` (estricto):
 * tocarse en el extremo (`b1 == a2`, recaudaciones ADYACENTES) NO es solape —es
 * el caso normal y correcto—. Solo cuenta el contador de ENTRADAS: es el primario
 * (créditos jugados) y se persiste sin ajustar, mientras que las salidas se
 * redondean al guardar (`contador_salidas_ajustado`), lo que daría falsos
 * positivos al comparar el valor crudo de la nueva con el ajustado de las firmes.
 *
 * `existentes` DEBE venir filtrado al mismo epoch de contador (firmes tras el
 * último cambio de placa): un reset de placa reinicia los números y comparar
 * tramos de epochs distintos daría falsos solapes.
 *
 * @returns los ids de las recaudaciones firmes cuyo tramo pisa el nuevo (vacío si
 *   no hay solape).
 */
export function detectarSolapeContador(
  nueva: TramoNuevo,
  existentes: TramoFirme[],
): string[] {
  return existentes
    .filter((e) =>
      solapan(nueva.entradasAnterior, nueva.entradasActual, e.entradasAnterior, e.entradasActual)
    )
    .map((e) => e.id);
}

/** Solape estricto de intervalos: comparten más que un extremo. */
function solapan(a1: number, b1: number, a2: number, b2: number): boolean {
  return a1 < b2 && a2 < b1;
}
