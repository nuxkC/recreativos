/**
 * Tests del cuerpo PURO de la notificación push (T-101).
 *
 * Ejecutar con:
 *   deno test supabase/functions/enviar-push/mensaje.test.ts
 */

import { assertEquals, assertStringIncludes } from "@std/assert";

import { construirCuerpoPush, textoResolucion } from "./mensaje.ts";
import type { RecaudacionPushRow } from "./mensaje.ts";

function row(overrides: Partial<RecaudacionPushRow> = {}): RecaudacionPushRow {
  return {
    id: "rec-1",
    empresa_id: "emp-1",
    tecnico_id: "tec-1",
    estado: "firme",
    resolucion: "aceptada",
    instalacion: {
      maquina: { numero_serie: "SN-42" },
      local: { nombre: "Bar Pepe" },
    },
    ...overrides,
  };
}

Deno.test("incluye local y máquina en el cuerpo", () => {
  const cuerpo = construirCuerpoPush(row());
  assertEquals(cuerpo.title, "Conflicto resuelto");
  assertStringIncludes(cuerpo.body, "Bar Pepe");
  assertStringIncludes(cuerpo.body, "SN-42");
});

Deno.test("data lleva tipo y recaudacion_id para el deep-link", () => {
  const cuerpo = construirCuerpoPush(row({ id: "rec-99", resolucion: "sustituida" }));
  assertEquals(cuerpo.data.tipo, "recaudacion_conflicto");
  assertEquals(cuerpo.data.recaudacion_id, "rec-99");
  assertEquals(cuerpo.data.resolucion, "sustituida");
});

Deno.test("tolera instalación/local/máquina ausentes sin romper", () => {
  const cuerpo = construirCuerpoPush(row({ instalacion: null }));
  assertStringIncludes(cuerpo.body, "una recaudación");
  // Sin máquina no debe colar el sufijo "(máquina ...)".
  assertEquals(cuerpo.body.includes("(máquina"), false);
});

Deno.test("data.resolucion es string vacío cuando la resolución es null", () => {
  const cuerpo = construirCuerpoPush(row({ resolucion: null }));
  assertEquals(cuerpo.data.resolucion, "");
});

Deno.test("textoResolucion cubre todas las resoluciones", () => {
  assertEquals(textoResolucion("aceptada"), "se aceptaron tus importes");
  assertStringIncludes(textoResolucion("sustituida"), "recalculados");
  assertStringIncludes(textoResolucion("anulada"), "anulada");
  assertStringIncludes(textoResolucion(null), "detalle");
  assertStringIncludes(textoResolucion("otra-cosa"), "detalle");
});
