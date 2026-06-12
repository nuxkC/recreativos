import { describe, expect, it } from "vitest";

import type { Recaudacion } from "@/lib/recaudaciones/types";

import { UTF8_BOM } from "./csv";
import {
  formatFechaCsv,
  formatImporteCsv,
  nombreFicheroRecaudacionesCsv,
  recaudacionesToCsv,
  type RecaudacionesCsvLabels,
} from "./recaudaciones-csv";

const LABELS: RecaudacionesCsvLabels = {
  fecha: "Fecha",
  local: "Local",
  maquina: "Máquina",
  modelo: "Modelo",
  bruto: "Bruto",
  tasa: "Tasa total",
  neto: "Neto",
  parteLocal: "Parte local",
  parteEmpresa: "Parte empresa",
  tecnico: "Técnico",
  estado: "Estado",
  estadoFirme: "Firme",
  estadoAnulada: "Anulada",
};

const ZONA = "Europe/Madrid";

function baseRecaudacion(overrides: Partial<Recaudacion> = {}): Recaudacion {
  const base: Recaudacion = {
    id: "rec-1",
    empresaId: "emp-1",
    instalacionId: "inst-1",
    tecnicoId: "tec-1",
    fecha: "2026-01-15T10:30:00Z",
    contadorEntradasAnterior: 0,
    contadorSalidasAnterior: 0,
    contadorEntradasActual: 0,
    contadorSalidasActual: 0,
    valorCreditoAplicado: "0.20",
    recaudacionBruta: "1234.56",
    semanasAplicadas: 2,
    tasaSemanalAplicada: "10.00",
    tasaTotalAplicada: "20.00",
    recaudacionNeta: "1214.56",
    porcentajeLocalAplicado: "50.00",
    parteLocal: "607.28",
    parteEmpresa: "607.28",
    recuperadoTotal: "0.00",
    pagadoLocal: "607.28",
    recaudacionBrutaReal: null,
    contadorSalidasLeido: null,
    redondeoAplicado: null,
    desgloseTotal: [],
    desgloseLocal: [],
    firmaUrl: null,
    fotoEntradasUrl: null,
    fotoSalidasUrl: null,
    ocrEntradasValor: null,
    ocrSalidasValor: null,
    pdfUrl: null,
    observaciones: null,
    dispositivoId: null,
    idempotencyKey: "key-1",
    baselineOrigen: "instalacion_base",
    baselineId: null,
    conflicto: false,
    brutoRecalculado: null,
    netoRecalculado: null,
    parteLocalRecalculada: null,
    parteEmpresaRecalculada: null,
    revisadoPor: null,
    revisadoEn: null,
    resolucion: null,
    resolucionNotas: null,
    estado: "firme",
    motivoAnulacion: null,
    anuladaPor: null,
    anuladaEn: null,
    createdAt: "2026-01-15T10:30:00Z",
    updatedAt: "2026-01-15T10:30:00Z",
    instalacion: {
      id: "inst-1",
      licencia: { id: "lic-1", numero: "L-001" },
      maquina: { id: "maq-1", numeroSerie: "SN-123", modelo: "Cobra" },
      local: { id: "loc-1", nombre: "Bar Central" },
    },
    tecnico: { id: "tec-1", nombreCompleto: "Ana Pérez" },
  };
  return { ...base, ...overrides };
}

describe("formatImporteCsv", () => {
  it("formatea con coma decimal y dos decimales, sin símbolo", () => {
    expect(formatImporteCsv("1234.5")).toBe("1234,50");
    expect(formatImporteCsv("0")).toBe("0,00");
  });

  it("devuelve cadena vacía para nulos, vacío o no parseable", () => {
    expect(formatImporteCsv(null)).toBe("");
    expect(formatImporteCsv(undefined)).toBe("");
    expect(formatImporteCsv("")).toBe("");
    expect(formatImporteCsv("no-numero")).toBe("");
  });
});

describe("formatFechaCsv", () => {
  it("formatea en la zona horaria de la empresa", () => {
    // 10:30 UTC en Madrid (invierno, UTC+1) → 11:30.
    expect(formatFechaCsv("2026-01-15T10:30:00Z", ZONA)).toBe("15/01/2026 11:30");
  });

  it("devuelve cadena vacía si no hay fecha", () => {
    expect(formatFechaCsv(null, ZONA)).toBe("");
    expect(formatFechaCsv(undefined, ZONA)).toBe("");
  });
});

describe("nombreFicheroRecaudacionesCsv", () => {
  it("usa el rango cuando hay desde y hasta", () => {
    expect(nombreFicheroRecaudacionesCsv("2026-01-01", "2026-01-31", "2026-02-01")).toBe(
      "recaudaciones_2026-01-01_2026-01-31.csv",
    );
  });

  it("usa solo el extremo disponible", () => {
    expect(nombreFicheroRecaudacionesCsv("2026-01-01", null, "2026-02-01")).toBe(
      "recaudaciones_2026-01-01.csv",
    );
  });

  it("usa la fecha de respaldo cuando no hay rango", () => {
    expect(nombreFicheroRecaudacionesCsv(null, null, "2026-02-01")).toBe(
      "recaudaciones_2026-02-01.csv",
    );
  });
});

describe("recaudacionesToCsv", () => {
  it("genera cabecera + fila con los campos clave", () => {
    const csv = recaudacionesToCsv([baseRecaudacion()], ZONA, LABELS);
    const lines = csv.replace(UTF8_BOM, "").split("\r\n");
    expect(lines[0]).toBe(
      "Fecha;Local;Máquina;Modelo;Bruto;Tasa total;Neto;Parte local;Parte empresa;Técnico;Estado",
    );
    expect(lines[1]).toBe(
      "15/01/2026 11:30;Bar Central;SN-123;Cobra;1234,56;20,00;1214,56;607,28;607,28;Ana Pérez;Firme",
    );
  });

  it("con conjunto vacío genera solo la cabecera", () => {
    const csv = recaudacionesToCsv([], ZONA, LABELS);
    const lines = csv.replace(UTF8_BOM, "").split("\r\n");
    expect(lines).toHaveLength(1);
    expect(lines[0]).toContain("Fecha;Local;Máquina");
  });

  it("traduce el estado anulada", () => {
    const csv = recaudacionesToCsv([baseRecaudacion({ estado: "anulada" })], ZONA, LABELS);
    expect(csv).toContain(";Anulada");
  });

  it("escapa nombres con el separador y trata nulos como vacío", () => {
    const rec = baseRecaudacion({
      tecnico: { id: "t", nombreCompleto: null },
      instalacion: {
        id: "inst-1",
        licencia: null,
        maquina: { id: "m", numeroSerie: "SN-1", modelo: null },
        local: { id: "l", nombre: "Bar; el Rincón" },
      },
    });
    const csv = recaudacionesToCsv([rec], ZONA, LABELS);
    const line = csv.replace(UTF8_BOM, "").split("\r\n")[1] ?? "";
    expect(line).toContain('"Bar; el Rincón"');
    // Modelo nulo y técnico nulo → celdas vacías.
    expect(line).toContain("SN-1;;");
    expect(line.endsWith(";;Firme")).toBe(true);
  });
});
