import { describe, expect, it } from "vitest";

import { eurAriaLabel, splitEur } from "./format";

describe("splitEur (money-safe)", () => {
  it("agrupa miles es-ES", () => {
    expect(splitEur("1234.56")).toMatchObject({
      integer: "1.234",
      decimals: "56",
      negative: false,
    });
  });

  it("agrupa el máximo del dominio numeric(10,2) sin perder céntimos", () => {
    expect(splitEur("99999999.99")).toMatchObject({
      integer: "99.999.999",
      decimals: "99",
    });
  });

  it("extrae el signo de los negativos (descuadre/deuda en contra)", () => {
    expect(splitEur("-12.50")).toMatchObject({
      negative: true,
      integer: "12",
      decimals: "50",
    });
  });

  it("rellena siempre a 2 decimales", () => {
    expect(splitEur("5")).toMatchObject({ integer: "5", decimals: "00" });
  });

  it("no trata el cero como negativo", () => {
    expect(splitEur("0.00").negative).toBe(false);
    expect(splitEur("-0.00").negative).toBe(false);
  });

  it("marca inválido sin lanzar", () => {
    expect(splitEur("abc").invalid).toBe(true);
    expect(splitEur(null).invalid).toBe(true);
    expect(splitEur("").invalid).toBe(true);
  });
});

describe("eurAriaLabel", () => {
  it("anuncia el signo y omite el separador de miles", () => {
    expect(eurAriaLabel("-1234.56")).toBe("menos 1234,56 euros");
  });

  it("formatea positivos sin «menos»", () => {
    expect(eurAriaLabel("80.00")).toBe("80,00 euros");
  });
});
