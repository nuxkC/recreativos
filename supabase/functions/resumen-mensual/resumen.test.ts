/**
 * Tests de la lógica pura del resumen mensual (T-102).
 *
 * Ejecutar con:
 *   deno test supabase/functions/resumen-mensual/resumen.test.ts
 *
 * No requieren red ni BBDD.
 */

import { assert, assertEquals } from "@std/assert";

import {
  construirAsuntoResumen,
  construirHtmlResumen,
  construirResumenLocal,
  construirTextoResumen,
  formatEuros,
  type MaquinaInfo,
  type MaquinaMesRow,
  resolverMes,
} from "./resumen.ts";

const maquinaInfo: Record<string, MaquinaInfo> = {
  "m-1": { numero_serie: "SN-002", modelo: "Cherry Master" },
  "m-2": { numero_serie: "SN-001", modelo: null },
};

const filas: MaquinaMesRow[] = [
  {
    empresa_id: "e-1",
    local_id: "l-1",
    maquina_id: "m-1",
    num_recaudaciones: 2,
    parte_local_total: "120.50",
    neto_total: "241.00",
  },
  {
    empresa_id: "e-1",
    local_id: "l-1",
    maquina_id: "m-2",
    num_recaudaciones: 3,
    parte_local_total: "79.50",
    neto_total: "159.00",
  },
];

Deno.test("resolverMes usa el mes anterior por defecto", () => {
  const r = resolverMes(new Date("2026-05-10T12:00:00Z"), "Europe/Madrid");
  assertEquals(r.mes, "2026-04");
  assertEquals(r.mesLocalStart, "2026-04-01 00:00:00");
  assertEquals(r.etiqueta, "abril de 2026");
});

Deno.test("resolverMes cruza el año en enero", () => {
  const r = resolverMes(new Date("2026-01-05T12:00:00Z"), "Europe/Madrid");
  assertEquals(r.mes, "2025-12");
  assertEquals(r.etiqueta, "diciembre de 2025");
});

Deno.test("resolverMes respeta el mes explícito", () => {
  const r = resolverMes(new Date("2026-05-10T12:00:00Z"), "Europe/Madrid", "2026-02");
  assertEquals(r.mes, "2026-02");
  assertEquals(r.mesLocalStart, "2026-02-01 00:00:00");
  assertEquals(r.etiqueta, "febrero de 2026");
});

Deno.test("formatEuros formatea es-ES con separador de miles", () => {
  assertEquals(formatEuros("1234.5"), "1.234,50 €");
  assertEquals(formatEuros("0"), "0,00 €");
  assertEquals(formatEuros("1000000.99"), "1.000.000,99 €");
  assertEquals(formatEuros("-12.3"), "-12,30 €");
});

Deno.test("construirResumenLocal suma importes con precisión y ordena máquinas", () => {
  const resumen = construirResumenLocal({
    empresaNombre: "Recreativos SL",
    localNombre: "Bar Pepe",
    titularNombre: "Pepe",
    mesEtiqueta: "abril de 2026",
    filas,
    maquinaInfoPorId: maquinaInfo,
  });

  assertEquals(resumen.numRecaudaciones, 5);
  assertEquals(resumen.parteLocalTotal, "200.00");
  assertEquals(resumen.netoTotal, "400.00");
  // Ordenadas por número de serie ascendente: SN-001 antes que SN-002.
  assertEquals(resumen.maquinas[0]?.numeroSerie, "SN-001");
  assertEquals(resumen.maquinas[1]?.numeroSerie, "SN-002");
});

Deno.test("asunto incluye empresa, mes y local", () => {
  const resumen = construirResumenLocal({
    empresaNombre: "Recreativos SL",
    localNombre: "Bar Pepe",
    titularNombre: "Pepe",
    mesEtiqueta: "abril de 2026",
    filas,
    maquinaInfoPorId: maquinaInfo,
  });
  assertEquals(
    construirAsuntoResumen(resumen),
    "[Recreativos SL] Resumen de abril de 2026 — Bar Pepe",
  );
});

Deno.test("HTML y texto incluyen total y saludo al titular", () => {
  const resumen = construirResumenLocal({
    empresaNombre: "Recreativos SL",
    localNombre: "Bar Pepe",
    titularNombre: "Pepe",
    mesEtiqueta: "abril de 2026",
    filas,
    maquinaInfoPorId: maquinaInfo,
  });

  const html = construirHtmlResumen(resumen);
  assert(html.includes("Hola Pepe,"));
  assert(html.includes("200,00 €"));
  assert(html.includes("Bar Pepe"));

  const texto = construirTextoResumen(resumen);
  assert(texto.includes("Hola Pepe,"));
  assert(texto.includes("Total a liquidar (parte del local): 200,00 €"));
  assert(texto.includes("Nº de recaudaciones: 5"));
});

Deno.test("saludo genérico cuando no hay titular", () => {
  const resumen = construirResumenLocal({
    empresaNombre: "Recreativos SL",
    localNombre: "Bar Pepe",
    titularNombre: null,
    mesEtiqueta: "abril de 2026",
    filas,
    maquinaInfoPorId: maquinaInfo,
  });
  assert(construirTextoResumen(resumen).startsWith("Hola,"));
  assert(construirHtmlResumen(resumen).includes("<p>Hola,</p>"));
});

Deno.test("escapa HTML en campos del usuario", () => {
  const resumen = construirResumenLocal({
    empresaNombre: "Recreativos SL",
    localNombre: "Bar <script>",
    titularNombre: "A&B",
    mesEtiqueta: "abril de 2026",
    filas,
    maquinaInfoPorId: maquinaInfo,
  });
  const html = construirHtmlResumen(resumen);
  assert(html.includes("Bar &lt;script&gt;"));
  assert(html.includes("Hola A&amp;B,"));
});
