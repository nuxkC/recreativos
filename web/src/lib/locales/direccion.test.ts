import { describe, expect, it } from "vitest";

import { formatearDireccion, tieneDireccionEstructurada } from "./direccion";
import type { Local } from "./types";

function local(parcial: Partial<Local>): Local {
  return {
    id: "l1",
    nombre: "Bar Pepe",
    direccion: null,
    comunidadAutonoma: null,
    provinciaCodigo: null,
    municipioCodigo: null,
    calle: null,
    codigoPostal: null,
    cifONif: null,
    titularNombre: null,
    telefono: null,
    email: null,
    notas: null,
    porcentajeRecuperacion: null,
    cadenciaSemanas: null,
    fechaInicioRecaudacion: null,
    operarioId: null,
    updatedAt: "2026-07-03T00:00:00Z",
    ...parcial,
  } as Local;
}

describe("tieneDireccionEstructurada", () => {
  it("es false sin ningún campo estructurado", () => {
    expect(tieneDireccionEstructurada(local({ direccion: "Calle Vieja 1" }))).toBe(false);
  });
  it("es true con cualquier campo estructurado", () => {
    expect(tieneDireccionEstructurada(local({ codigoPostal: "28001" }))).toBe(true);
    expect(tieneDireccionEstructurada(local({ comunidadAutonoma: "Madrid" }))).toBe(true);
  });
});

describe("formatearDireccion", () => {
  it("cae al texto libre si no hay estructura", () => {
    expect(formatearDireccion(local({ direccion: "Calle Vieja 1" }))).toBe("Calle Vieja 1");
  });

  it("devuelve null si no hay nada", () => {
    expect(formatearDireccion(local({}))).toBeNull();
  });

  it("con nombres resueltos: calle, CP municipio, provincia", () => {
    const l = local({
      calle: "Rambla 1",
      codigoPostal: "08002",
      municipioCodigo: "08019",
      provinciaCodigo: "08",
      comunidadAutonoma: "Cataluña",
    });
    expect(formatearDireccion(l, { municipio: "Barcelona", provincia: "Barcelona" })).toBe(
      "Rambla 1, 08002 Barcelona",
    );
  });

  it("no repite provincia igual al municipio", () => {
    const l = local({ calle: "Gran Vía 1", codigoPostal: "28013" });
    expect(formatearDireccion(l, { municipio: "Madrid", provincia: "Madrid" })).toBe(
      "Gran Vía 1, 28013 Madrid",
    );
  });

  it("sin nombres resueltos usa la comunidad autónoma", () => {
    const l = local({ calle: "Rambla 1", codigoPostal: "08002", comunidadAutonoma: "Cataluña" });
    expect(formatearDireccion(l)).toBe("Rambla 1, 08002, Cataluña");
  });

  it("estructura parcial (solo municipio resuelto)", () => {
    const l = local({ municipioCodigo: "46250", comunidadAutonoma: "Comunidad Valenciana" });
    expect(formatearDireccion(l, { municipio: "Valencia", provincia: "Valencia" })).toBe(
      "Valencia",
    );
  });
});
