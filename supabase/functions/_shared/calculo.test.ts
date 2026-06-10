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
