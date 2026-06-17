/**
 * Tests de la detección de tramos de contador solapados (T-262, "caso 8").
 *
 * Ejecutar con:
 *   deno test supabase/functions/_shared/solape.test.ts
 *
 * No requieren red ni BBDD: la función es pura.
 */

import { assertEquals } from "@std/assert";

import { detectarSolapeContador, type TramoFirme } from "./solape.ts";

const firme = (id: string, anterior: number, actual: number): TramoFirme => ({
  id,
  entradasAnterior: anterior,
  entradasActual: actual,
});

Deno.test("recaudaciones adyacentes (extremo común) NO solapan", () => {
  // [100,130] seguida de [130,160]: el caso normal y correcto.
  const ids = detectarSolapeContador(
    { entradasAnterior: 130, entradasActual: 160 },
    [firme("a", 100, 130)],
  );
  assertEquals(ids, []);
});

Deno.test("adyacente por el otro extremo tampoco solapa", () => {
  const ids = detectarSolapeContador(
    { entradasAnterior: 100, entradasActual: 130 },
    [firme("a", 130, 160)],
  );
  assertEquals(ids, []);
});

Deno.test("tramo nuevo que pisa el final de una firme solapa", () => {
  // Nueva [100,130] pisa [100,150]: subida desordenada → doble conteo de [100,130].
  const ids = detectarSolapeContador(
    { entradasAnterior: 100, entradasActual: 130 },
    [firme("a", 100, 150)],
  );
  assertEquals(ids, ["a"]);
});

Deno.test("tramo nuevo contenido dentro de una firme solapa", () => {
  const ids = detectarSolapeContador(
    { entradasAnterior: 120, entradasActual: 150 },
    [firme("a", 100, 200)],
  );
  assertEquals(ids, ["a"]);
});

Deno.test("tramo idéntico solapa", () => {
  const ids = detectarSolapeContador(
    { entradasAnterior: 100, entradasActual: 200 },
    [firme("a", 100, 200)],
  );
  assertEquals(ids, ["a"]);
});

Deno.test("tramos disjuntos (p.ej. tras reset de placa) NO solapan", () => {
  // La máquina contaba 4900→5000; tras cambio de placa arranca en 0→200.
  const ids = detectarSolapeContador(
    { entradasAnterior: 0, entradasActual: 200 },
    [firme("vieja", 4900, 5000)],
  );
  assertEquals(ids, []);
});

Deno.test("devuelve solo los ids de las firmes que realmente pisan", () => {
  const ids = detectarSolapeContador(
    { entradasAnterior: 100, entradasActual: 130 },
    [
      firme("antes", 0, 100), // adyacente: no
      firme("pisa", 90, 140), // solapa [100,130]
      firme("despues", 130, 200), // adyacente: no
      firme("lejana", 500, 600), // disjunta: no
    ],
  );
  assertEquals(ids, ["pisa"]);
});

Deno.test("sin firmes existentes no hay solape", () => {
  const ids = detectarSolapeContador(
    { entradasAnterior: 100, entradasActual: 130 },
    [],
  );
  assertEquals(ids, []);
});
