import { describe, expect, it } from "vitest";

import { debeOfrecerCrear, etiquetaValor } from "./combobox";

const OPCIONES = [
  { value: "Cirsa", label: "Cirsa" },
  { value: "Unidesa", label: "Unidesa" },
];

describe("debeOfrecerCrear", () => {
  it("false si el texto está vacío o en blanco", () => {
    expect(debeOfrecerCrear(OPCIONES, "")).toBe(false);
    expect(debeOfrecerCrear(OPCIONES, "   ")).toBe(false);
  });

  it("false si ya existe una opción con ese label (laxo: trim + mayúsculas)", () => {
    expect(debeOfrecerCrear(OPCIONES, "cirsa")).toBe(false);
    expect(debeOfrecerCrear(OPCIONES, "  Unidesa ")).toBe(false);
  });

  it("true si lo tecleado no coincide con ninguna opción", () => {
    expect(debeOfrecerCrear(OPCIONES, "Novomatic")).toBe(true);
  });
});

describe("etiquetaValor", () => {
  it("null si no hay valor", () => {
    expect(etiquetaValor(OPCIONES, null)).toBeNull();
    expect(etiquetaValor(OPCIONES, "")).toBeNull();
  });

  it("usa el label de la opción cuando el valor está catalogado", () => {
    expect(etiquetaValor(OPCIONES, "Cirsa")).toBe("Cirsa");
  });

  it("muestra el propio valor cuando no está en la lista (tecleado/heredado)", () => {
    expect(etiquetaValor(OPCIONES, "Marca Rara SL")).toBe("Marca Rara SL");
  });
});
