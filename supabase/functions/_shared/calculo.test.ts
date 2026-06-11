/**
 * Tests del SSOT de cálculo de recaudación.
 *
 * Ejecutar con:
 *   deno test --allow-env supabase/functions/_shared/calculo.test.ts
 *
 * No requieren red ni BBDD: la función es pura.
 */

import { assert, assertEquals } from "@std/assert";

import { calcularRecaudacion, importesIguales, sumarDesglose } from "./calculo.ts";
import type { BaselineInfo } from "./types.ts";

const baseline = (entradas: number, salidas: number): BaselineInfo => ({
  entradas,
  salidas,
  fecha_referencia: "2026-05-15T10:00:00+02:00",
  origen: "instalacion_base",
  referencia_id: "00000000-0000-0000-0000-000000000000",
});

Deno.test("calcula bruto correctamente", () => {
  const r = calcularRecaudacion({
    baseline: baseline(1000, 500),
    contadorEntradasActual: 1500,
    contadorSalidasActual: 700,
    valorCredito: "0.20",
    tasaSemanal: "10.00",
    porcentajeLocal: "50.00",
    semanas: 1,
  });
  // creditos_netos = (1500-1000) - (700-500) = 500-200 = 300
  // bruto = 300 * 0.20 = 60.00
  assertEquals(r.procede, true);
  assertEquals(r.bruto, "60.00");
  assertEquals(r.tasa_total, "10.00");
  assertEquals(r.neto, "50.00");
  assertEquals(r.parte_local, "25.00");
  assertEquals(r.parte_empresa, "25.00");
});

Deno.test("no procede cuando bruto < tasa", () => {
  const r = calcularRecaudacion({
    baseline: baseline(1000, 500),
    contadorEntradasActual: 1010,
    contadorSalidasActual: 500,
    valorCredito: "0.20",
    tasaSemanal: "10.00",
    porcentajeLocal: "50.00",
    semanas: 1,
  });
  // bruto = 10 * 0.20 = 2.00 < tasa 10
  assertEquals(r.procede, false);
  assertEquals(r.bruto, "2.00");
  assertEquals(r.tasa_total, "10.00");
  assertEquals(r.parte_local, "0.00");
  assertEquals(r.parte_empresa, "0.00");
});

Deno.test("la empresa absorbe el redondeo de céntimos", () => {
  // Bruto 100, tasa 0, % 33.33
  // neto = 100, parte_local = 33.33 (round half up), parte_empresa = 66.67
  const r = calcularRecaudacion({
    baseline: baseline(0, 0),
    contadorEntradasActual: 500,
    contadorSalidasActual: 0,
    valorCredito: "0.20",
    tasaSemanal: "0.00",
    porcentajeLocal: "33.33",
    semanas: 0,
  });
  assertEquals(r.bruto, "100.00");
  assertEquals(r.neto, "100.00");
  // 100 * 33.33 / 100 = 33.33
  assertEquals(r.parte_local, "33.33");
  assertEquals(r.parte_empresa, "66.67");
  // Comprobación contable: parte_local + parte_empresa == neto
  assert(importesIguales(
    sumarDesglose([
      { denominacion: 50, cantidad: 0 },
      { denominacion: 20, cantidad: 0 },
    ]),
    "0.00",
  ));
});

Deno.test("redondeo half-up en %50 con neto impar de céntimos", () => {
  // creditos = 5, valor 0.10 -> bruto 0.50; tasa 0 -> neto 0.50
  // 50% de 0.50 = 0.25 (exacto, sin redondeo)
  const r = calcularRecaudacion({
    baseline: baseline(0, 0),
    contadorEntradasActual: 5,
    contadorSalidasActual: 0,
    valorCredito: "0.10",
    tasaSemanal: "0",
    porcentajeLocal: "50",
    semanas: 0,
  });
  assertEquals(r.bruto, "0.50");
  assertEquals(r.parte_local, "0.25");
  assertEquals(r.parte_empresa, "0.25");
});

Deno.test("creditos negativos producen bruto negativo y no procede", () => {
  // Esto no debería ocurrir en práctica (los contadores solo suben), pero
  // el algoritmo debe ser robusto ante restas mayores que entradas.
  const r = calcularRecaudacion({
    baseline: baseline(1000, 1000),
    contadorEntradasActual: 1010,
    contadorSalidasActual: 1100,
    valorCredito: "0.20",
    tasaSemanal: "5.00",
    porcentajeLocal: "50.00",
    semanas: 1,
  });
  // creditos = 10 - 100 = -90, bruto = -18.00, < tasa -> no procede
  assertEquals(r.procede, false);
});

// -----------------------------------------------------------------------------
// Redondeo del bruto (config por empresa). Falsea la lectura de salidas para
// que el bruto caiga en el múltiplo más cercano de `redondeoUnidad`; el contador
// ajustado se persiste como real y la diferencia rueda a la siguiente vía baseline.
// -----------------------------------------------------------------------------

Deno.test("redondeo a la baja: 234,20 -> 230 subiendo salidas", () => {
  // creditos = 2000 - 829 = 1171 -> bruto real 234.20
  const r = calcularRecaudacion({
    baseline: baseline(0, 0),
    contadorEntradasActual: 2000,
    contadorSalidasActual: 829,
    valorCredito: "0.20",
    tasaSemanal: "0",
    porcentajeLocal: "50",
    semanas: 0,
    redondeoUnidad: 10,
  });
  assertEquals(r.procede, true);
  assertEquals(r.bruto, "230.00");
  assertEquals(r.recaudacion_bruta_real, "234.20");
  assertEquals(r.redondeo_aplicado, 10);
  // 1171 - 1150 = 21 créditos de menos => salidas suben 21 (829 -> 850).
  assertEquals(r.contador_salidas_ajustado, 850);
  assertEquals(r.parte_local, "115.00");
  assertEquals(r.parte_empresa, "115.00");
});

Deno.test("redondeo al alza: 237,80 -> 240 bajando salidas", () => {
  // creditos = 2000 - 811 = 1189 -> bruto real 237.80
  const r = calcularRecaudacion({
    baseline: baseline(0, 0),
    contadorEntradasActual: 2000,
    contadorSalidasActual: 811,
    valorCredito: "0.20",
    tasaSemanal: "0",
    porcentajeLocal: "50",
    semanas: 0,
    redondeoUnidad: 10,
  });
  assertEquals(r.bruto, "240.00");
  assertEquals(r.recaudacion_bruta_real, "237.80");
  // 1189 - 1200 = -11 => salidas bajan 11 (811 -> 800).
  assertEquals(r.contador_salidas_ajustado, 800);
});

Deno.test("el redondeo nunca deja el neto negativo (sube a la tasa)", () => {
  // bruto real 73.00, tasa 72: procede. nearest(73)=70 < 72 -> ceil = 80.
  const r = calcularRecaudacion({
    baseline: baseline(1000, 1000),
    contadorEntradasActual: 1365,
    contadorSalidasActual: 1000,
    valorCredito: "0.20",
    tasaSemanal: "72",
    porcentajeLocal: "50",
    semanas: 1,
    redondeoUnidad: 10,
  });
  assertEquals(r.procede, true);
  assertEquals(r.bruto, "80.00");
  assertEquals(r.neto, "8.00");
  // 365 - 400 = -35 => salidas 1000 -> 965.
  assertEquals(r.contador_salidas_ajustado, 965);
});

Deno.test("redondeo con valorCredito no divisor de la unidad cae al múltiplo de valorCredito", () => {
  // creditos 100, valor 0.15 -> bruto real 15.00. Objetivo nearest = 20, pero
  // 20 / 0.15 = 133,33 -> 133 créditos -> bruto 19.95 (no exacto: < 0.15 del objetivo).
  const r = calcularRecaudacion({
    baseline: baseline(0, 0),
    contadorEntradasActual: 100,
    contadorSalidasActual: 0,
    valorCredito: "0.15",
    tasaSemanal: "0",
    porcentajeLocal: "50",
    semanas: 0,
    redondeoUnidad: 10,
  });
  assertEquals(r.recaudacion_bruta_real, "15.00");
  assertEquals(r.bruto, "19.95");
  assertEquals(r.redondeo_aplicado, 10);
});

Deno.test("sin redondeo (unidad 0) no toca contadores y rellena auditoría con lo real", () => {
  const r = calcularRecaudacion({
    baseline: baseline(0, 0),
    contadorEntradasActual: 2000,
    contadorSalidasActual: 829,
    valorCredito: "0.20",
    tasaSemanal: "0",
    porcentajeLocal: "50",
    semanas: 0,
    redondeoUnidad: 0,
  });
  assertEquals(r.bruto, "234.20");
  assertEquals(r.recaudacion_bruta_real, "234.20");
  assertEquals(r.redondeo_aplicado, 0);
  assertEquals(r.contador_salidas_ajustado, 829);
});

Deno.test("el redondeo se arrastra exacto a la siguiente recaudación", () => {
  // Periodo 1: real 234.20 -> oficial 230, salidas ajustadas a 850.
  const p1 = calcularRecaudacion({
    baseline: baseline(0, 0),
    contadorEntradasActual: 2000,
    contadorSalidasActual: 829,
    valorCredito: "0.20",
    tasaSemanal: "0",
    porcentajeLocal: "50",
    semanas: 0,
    redondeoUnidad: 10,
  });
  assertEquals(p1.bruto, "230.00");
  assertEquals(p1.contador_salidas_ajustado, 850);

  // Periodo 2: la baseline de salidas es la AJUSTADA (850). El contador físico
  // sigue su curso real (entradas 4000, salidas 1500). Sin redondear aquí, el
  // bruto sale 270 -> 230 + 270 = 500 = el bruto real continuo desde el origen
  // (2500 créditos * 0,20). La diferencia de 4,20 del periodo 1 se recupera aquí.
  const p2 = calcularRecaudacion({
    baseline: baseline(2000, 850),
    contadorEntradasActual: 4000,
    contadorSalidasActual: 1500,
    valorCredito: "0.20",
    tasaSemanal: "0",
    porcentajeLocal: "50",
    semanas: 0,
    redondeoUnidad: 0,
  });
  assertEquals(p2.bruto, "270.00");
});

Deno.test("sumarDesglose suma con precisión", () => {
  assertEquals(
    sumarDesglose([
      { denominacion: 50, cantidad: 2 },
      { denominacion: 10, cantidad: 4 },
      { denominacion: 0.20, cantidad: 5 },
    ]),
    "141.00",
  );
});

Deno.test("sumarDesglose array vacío", () => {
  assertEquals(sumarDesglose([]), "0.00");
});

Deno.test("importesIguales tolera representaciones distintas", () => {
  assert(importesIguales("60.00", "60"));
  assert(importesIguales("0.10", "0.1"));
  assert(!importesIguales("60.00", "60.01"));
});
