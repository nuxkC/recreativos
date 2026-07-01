import { describe, expect, it } from "vitest";

import { idFabricantePorNombre, opcionesFabricante, opcionesModelo } from "./opciones";

const FABRICANTES = [
  { id: "fab-cirsa", nombre: "Cirsa" },
  { id: "fab-unidesa", nombre: "Unidesa" },
];
const MODELOS = [
  { id: "m1", nombre: "Diplomat", fabricanteId: "fab-cirsa" },
  { id: "m2", nombre: "Super", fabricanteId: "fab-cirsa" },
  { id: "m3", nombre: "Gallo", fabricanteId: "fab-unidesa" },
];

describe("opcionesFabricante", () => {
  it("mapea cada fabricante a { value: nombre, label: nombre }", () => {
    expect(opcionesFabricante(FABRICANTES)).toEqual([
      { value: "Cirsa", label: "Cirsa" },
      { value: "Unidesa", label: "Unidesa" },
    ]);
  });

  it("lista vacía → []", () => {
    expect(opcionesFabricante([])).toEqual([]);
  });
});

describe("idFabricantePorNombre", () => {
  it("resuelve por nombre exacto ignorando mayúsculas y espacios", () => {
    expect(idFabricantePorNombre(FABRICANTES, "  cirsa ")).toBe("fab-cirsa");
    expect(idFabricantePorNombre(FABRICANTES, "UNIDESA")).toBe("fab-unidesa");
  });

  it("null para fabricante nuevo (no está en catálogo), vacío o null", () => {
    expect(idFabricantePorNombre(FABRICANTES, "Nuevo SL")).toBeNull();
    expect(idFabricantePorNombre(FABRICANTES, "")).toBeNull();
    expect(idFabricantePorNombre(FABRICANTES, null)).toBeNull();
  });
});

describe("opcionesModelo", () => {
  it("solo los modelos del fabricante seleccionado, como opciones", () => {
    expect(opcionesModelo(MODELOS, FABRICANTES, "Cirsa")).toEqual([
      { value: "Diplomat", label: "Diplomat" },
      { value: "Super", label: "Super" },
    ]);
    expect(opcionesModelo(MODELOS, FABRICANTES, "Unidesa")).toEqual([
      { value: "Gallo", label: "Gallo" },
    ]);
  });

  it("[] si el fabricante es nuevo (no catalogado), vacío o null", () => {
    expect(opcionesModelo(MODELOS, FABRICANTES, "Nuevo SL")).toEqual([]);
    expect(opcionesModelo(MODELOS, FABRICANTES, "")).toEqual([]);
    expect(opcionesModelo(MODELOS, FABRICANTES, null)).toEqual([]);
  });
});
