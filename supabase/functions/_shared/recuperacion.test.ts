/**
 * Tests del SSOT de recuperación de deuda (T-214).
 *
 * Ejecutar con:
 *   deno test supabase/functions/_shared/recuperacion.test.ts
 *
 * Pura: sin red ni BBDD. Android replica estos mismos casos en Kotlin (T-215).
 */

import { assertEquals } from "@std/assert";

import { type CreditoAbierto, planificarRecuperacion } from "./recuperacion.ts";

const tolva = (id: string, saldo: string, fecha = "2026-03-01"): CreditoAbierto => ({
  id,
  tipo: "tolva",
  saldo,
  fecha,
});
const prestamo = (id: string, saldo: string, fecha: string): CreditoAbierto => ({
  id,
  tipo: "prestamo",
  saldo,
  fecha,
});

Deno.test("pct=0 no recupera nada: pagado = parte_local", () => {
  const plan = planificarRecuperacion({
    parteLocal: "100.00",
    porcentajeRecuperacion: 0,
    creditos: [tolva("t1", "100.00")],
  });
  assertEquals(plan.recuperado_total, "0.00");
  assertEquals(plan.pagado_local, "100.00");
  assertEquals(plan.asignaciones, []);
});

Deno.test("sin deudas: pagado = parte_local aunque pct>0", () => {
  const plan = planificarRecuperacion({
    parteLocal: "100.00",
    porcentajeRecuperacion: 100,
    creditos: [],
  });
  assertEquals(plan.recuperado_total, "0.00");
  assertEquals(plan.pagado_local, "100.00");
});

Deno.test("100% con deuda suficiente: el local no se lleva nada (ejemplo design.md §5.5)", () => {
  // bruto 260, tasa 60 → neto 200, % local 50 → parte_local 100; deuda 100.
  const plan = planificarRecuperacion({
    parteLocal: "100.00",
    porcentajeRecuperacion: 100,
    creditos: [tolva("t1", "100.00")],
  });
  assertEquals(plan.recuperado_total, "100.00");
  assertEquals(plan.pagado_local, "0.00");
  assertEquals(plan.asignaciones, [{ credito_id: "t1", importe: "100.00" }]);
});

Deno.test("50% retiene la mitad de la parte_local", () => {
  const plan = planificarRecuperacion({
    parteLocal: "100.00",
    porcentajeRecuperacion: 50,
    creditos: [tolva("t1", "100.00")],
  });
  assertEquals(plan.recuperado_total, "50.00");
  assertEquals(plan.pagado_local, "50.00");
});

Deno.test("se topa al saldo de la deuda (no retiene más de lo que se debe)", () => {
  const plan = planificarRecuperacion({
    parteLocal: "100.00",
    porcentajeRecuperacion: 100,
    creditos: [prestamo("p1", "30.00", "2026-01-01")],
  });
  assertEquals(plan.recuperado_total, "30.00");
  assertEquals(plan.pagado_local, "70.00");
});

Deno.test("imputa tolva primero, luego préstamo (aunque el préstamo sea más antiguo)", () => {
  const plan = planificarRecuperacion({
    parteLocal: "200.00",
    porcentajeRecuperacion: 100,
    creditos: [
      prestamo("p1", "100.00", "2026-01-01"),
      tolva("t1", "50.00", "2026-05-01"),
    ],
  });
  // objetivo 200, saldo total 150 → recuperado 150; tolva primero.
  assertEquals(plan.recuperado_total, "150.00");
  assertEquals(plan.pagado_local, "50.00");
  assertEquals(plan.asignaciones, [
    { credito_id: "t1", importe: "50.00" },
    { credito_id: "p1", importe: "100.00" },
  ]);
});

Deno.test("FIFO entre préstamos: la deuda más antigua primero", () => {
  const plan = planificarRecuperacion({
    parteLocal: "100.00",
    porcentajeRecuperacion: 100,
    creditos: [
      prestamo("nuevo", "80.00", "2026-05-01"),
      prestamo("viejo", "40.00", "2026-01-01"),
    ],
  });
  assertEquals(plan.recuperado_total, "100.00");
  assertEquals(plan.asignaciones, [
    { credito_id: "viejo", importe: "40.00" },
    { credito_id: "nuevo", importe: "60.00" },
  ]);
});

Deno.test("orden manual antepone los créditos indicados", () => {
  const plan = planificarRecuperacion({
    parteLocal: "100.00",
    porcentajeRecuperacion: 100,
    creditos: [
      prestamo("nuevo", "80.00", "2026-05-01"),
      prestamo("viejo", "40.00", "2026-01-01"),
    ],
    orden: ["nuevo"],
  });
  // 'nuevo' primero (80), luego 'viejo' (20).
  assertEquals(plan.recuperado_total, "100.00");
  assertEquals(plan.asignaciones, [
    { credito_id: "nuevo", importe: "80.00" },
    { credito_id: "viejo", importe: "20.00" },
  ]);
});
