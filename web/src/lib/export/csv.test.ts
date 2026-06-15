import { describe, expect, it } from "vitest";

import { escapeCsvField, toCsv, UTF8_BOM } from "./csv";

describe("escapeCsvField", () => {
  it("deja los valores simples sin entrecomillar", () => {
    expect(escapeCsvField("hola", ";")).toBe("hola");
    expect(escapeCsvField("1234,56", ";")).toBe("1234,56");
  });

  it("entrecomilla cuando contiene el separador", () => {
    expect(escapeCsvField("a;b", ";")).toBe('"a;b"');
  });

  it("no entrecomilla la coma cuando el separador es punto y coma", () => {
    // Caso clave para importes es-ES: la coma decimal no debe romper columnas.
    expect(escapeCsvField("1.234,56", ";")).toBe("1.234,56");
  });

  it("duplica las comillas internas y entrecomilla el campo", () => {
    expect(escapeCsvField('di "hola"', ";")).toBe('"di ""hola"""');
  });

  it("entrecomilla cuando hay saltos de línea", () => {
    expect(escapeCsvField("linea1\nlinea2", ";")).toBe('"linea1\nlinea2"');
    expect(escapeCsvField("linea1\r\nlinea2", ";")).toBe('"linea1\r\nlinea2"');
  });
});

describe("toCsv", () => {
  it("antepone BOM UTF-8 por defecto y usa CRLF", () => {
    const csv = toCsv(["a", "b"], [["1", "2"]]);
    expect(csv.startsWith(UTF8_BOM)).toBe(true);
    expect(csv).toBe(`${UTF8_BOM}a;b\r\n1;2`);
  });

  it("permite desactivar el BOM", () => {
    const csv = toCsv(["a"], [["1"]], { bom: false });
    expect(csv.startsWith(UTF8_BOM)).toBe(false);
    expect(csv).toBe("a\r\n1");
  });

  it("con filas vacías devuelve solo la cabecera", () => {
    const csv = toCsv(["fecha", "bruto"], [], { bom: false });
    expect(csv).toBe("fecha;bruto");
  });

  it("trata null y undefined como celda vacía", () => {
    const csv = toCsv(["a", "b", "c"], [["x", null, undefined]], { bom: false });
    expect(csv).toBe("a;b;c\r\nx;;");
  });

  it("escapa separadores, comillas y saltos de línea en las celdas", () => {
    const csv = toCsv(["nombre", "nota"], [["Bar; el Rincón", 'dijo "ok"\nadiós']], { bom: false });
    expect(csv).toBe('nombre;nota\r\n"Bar; el Rincón";"dijo ""ok""\nadiós"');
  });

  it("respeta un separador personalizado", () => {
    const csv = toCsv(["a", "b"], [["1", "2"]], { bom: false, separator: "," });
    expect(csv).toBe("a,b\r\n1,2");
  });
});
