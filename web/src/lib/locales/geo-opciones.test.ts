import { describe, expect, it } from "vitest";

import {
  opcionesMunicipio,
  opcionesProvincia,
  type MunicipioOpcion,
  type ProvinciaOpcion,
} from "./geo-opciones";

const PROVINCIAS: ProvinciaOpcion[] = [
  { codigo: "08", nombre: "Barcelona", comunidadAutonoma: "Cataluña" },
  { codigo: "17", nombre: "Girona", comunidadAutonoma: "Cataluña" },
  { codigo: "28", nombre: "Madrid", comunidadAutonoma: "Madrid" },
];

const MUNICIPIOS: MunicipioOpcion[] = [
  { codigo: "08019", nombre: "Barcelona", provinciaCodigo: "08" },
  { codigo: "08101", nombre: "Sabadell", provinciaCodigo: "08" },
  { codigo: "28079", nombre: "Madrid", provinciaCodigo: "28" },
];

describe("opcionesProvincia", () => {
  it("devuelve las provincias de la CCAA elegida como {value=código, label=nombre}", () => {
    expect(opcionesProvincia(PROVINCIAS, "Cataluña")).toEqual([
      { value: "08", label: "Barcelona" },
      { value: "17", label: "Girona" },
    ]);
  });

  it("filtra fuera las provincias de otras CCAA", () => {
    expect(opcionesProvincia(PROVINCIAS, "Madrid")).toEqual([{ value: "28", label: "Madrid" }]);
  });

  it("devuelve lista vacía sin CCAA elegida", () => {
    expect(opcionesProvincia(PROVINCIAS, null)).toEqual([]);
    expect(opcionesProvincia(PROVINCIAS, "")).toEqual([]);
  });
});

describe("opcionesMunicipio", () => {
  it("devuelve los municipios de la provincia elegida como {value=código, label=nombre}", () => {
    expect(opcionesMunicipio(MUNICIPIOS, "08")).toEqual([
      { value: "08019", label: "Barcelona" },
      { value: "08101", label: "Sabadell" },
    ]);
  });

  it("filtra fuera los municipios de otras provincias", () => {
    expect(opcionesMunicipio(MUNICIPIOS, "28")).toEqual([{ value: "28079", label: "Madrid" }]);
  });

  it("devuelve lista vacía sin provincia elegida", () => {
    expect(opcionesMunicipio(MUNICIPIOS, null)).toEqual([]);
    expect(opcionesMunicipio(MUNICIPIOS, "")).toEqual([]);
  });
});
