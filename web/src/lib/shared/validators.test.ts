import { describe, expect, it } from "vitest";

import {
  cifNifSchema,
  esCifNif,
  esEmailValido,
  esTelefonoEs,
  telefonoSchema,
} from "./validators";

describe("esCifNif (vectores oro)", () => {
  it.each([
    ["12345678Z", true],
    ["00000000T", true],
    [" 12345678-z ", true],
    ["12345678A", false],
    ["X1234567L", true],
    ["Y1234567X", true],
    ["X1234567A", false],
    ["A58818501", true],
    ["A58818500", false],
    ["P1234567D", true],
    ["P1234567A", false],
    ["1234", false],
  ])("esCifNif(%j) === %s", (input, expected) => {
    expect(esCifNif(input as string)).toBe(expected);
  });
});

describe("esTelefonoEs (vectores oro)", () => {
  it.each([
    ["612345678", true],
    ["912345678", true],
    ["+34 612 345 678", true],
    ["0034612345678", true],
    ["512345678", false],
    ["61234567", false],
    ["61234567a", false],
    ["1234", false],
  ])("esTelefonoEs(%j) === %s", (input, expected) => {
    expect(esTelefonoEs(input as string)).toBe(expected);
  });
});

describe("esEmailValido (vectores oro)", () => {
  it.each([
    ["user@example.com", true],
    ["first.last+tag@sub.domain.co", true],
    ["n@dominio.es", true],
    ["a@b", false],
    ["sin-arroba.com", false],
    ["@example.com", false],
    ["user@dominio", false],
    ["user@dominio.c", false],
  ])("esEmailValido(%j) === %s", (input, expected) => {
    expect(esEmailValido(input as string)).toBe(expected);
  });
});

describe("cifNifSchema / telefonoSchema (condicional a no vacío)", () => {
  it("vacío → null sin error", () => {
    expect(cifNifSchema("cifMuyLargo").parse("")).toBeNull();
    expect(telefonoSchema("telMuyLargo").parse("   ")).toBeNull();
  });

  it("válido → normalizado conservado", () => {
    expect(cifNifSchema("cifMuyLargo").parse("12345678Z")).toBe("12345678Z");
    expect(telefonoSchema("telMuyLargo").parse("612345678")).toBe("612345678");
  });

  it("inválido → error con clave i18n", () => {
    const r = cifNifSchema("cifMuyLargo").safeParse("12345678A");
    expect(r.success).toBe(false);
    if (!r.success) expect(r.error.issues[0]?.message).toBe("cifInvalido");
  });
});
