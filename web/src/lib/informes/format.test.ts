import { describe, expect, it } from "vitest";

import { formatEuros, formatMesCorto, formatMesLargo } from "./format";

// El espacio entre el número y "€" en es-ES es un espacio no separable (NBSP).
const NBSP = "\u00a0";

describe("formatEuros", () => {
  // Nota: el separador de miles depende de la build de ICU del entorno, por eso
  // aquí se verifican decimales, coma y símbolo sin asumir agrupación de miles.
  it("formatea en es-ES con coma decimal y símbolo de euro", () => {
    expect(formatEuros(12.34)).toBe(`12,34${NBSP}€`);
  });

  it("formatea el cero", () => {
    expect(formatEuros(0)).toBe(`0,00${NBSP}€`);
  });

  it("devuelve guion para valores no finitos", () => {
    expect(formatEuros(Number.NaN)).toBe("—");
  });
});

describe("formatMes", () => {
  it("formatea el mes corto a partir del ISO de primer día de mes", () => {
    expect(formatMesCorto("2026-01-01")).toBe("ene 26");
  });

  it("formatea el mes largo", () => {
    expect(formatMesLargo("2026-01-01")).toBe("enero de 2026");
  });
});
