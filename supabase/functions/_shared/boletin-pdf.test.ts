/**
 * Tests de la lógica pura del boletín de instalación (T-203).
 *
 * Ejecutar con:
 *   deno test supabase/functions/_shared/boletin-pdf.test.ts
 *
 * No requieren red ni BBDD: el mapeo y los formateadores son puros.
 */

import { assertEquals } from "@std/assert";

import { type BoletinInstalacionContext, construirDatosBoletin } from "./boletin-pdf.ts";
import { formatDecimalEs, formatEurosEs, formatPorcentajeEs } from "./pdf-helpers.ts";

const ctx = (over: Partial<BoletinInstalacionContext> = {}): BoletinInstalacionContext => ({
  instalacion: {
    id: "11111111-1111-1111-1111-111111111111",
    fecha_inicio: "2026-05-19",
    tasa_semanal: "12.50",
    porcentaje_local: "40.00",
    contador_entradas_base: 1000,
    contador_salidas_base: 250,
    estado: "activa",
    ...over.instalacion,
  },
  empresa: {
    nombre: "Recreativos Demo",
    cif: "B12345678",
    direccion: "Calle Mayor 1",
    zona_horaria: "Europe/Madrid",
    ...over.empresa,
  },
  local: {
    nombre: "Bar Pepe",
    direccion: "Av. del Sol 3",
    titular_nombre: "José García",
    cif_o_nif: "12345678Z",
    ...over.local,
  },
  maquina: {
    numero_serie: "SN-0001",
    modelo: "Cirsa Winner",
    fabricante: "Cirsa",
    valor_credito: "0.20",
    ...over.maquina,
  },
  licencia: {
    numero: "LIC-2026-001",
    ...over.licencia,
  },
});

Deno.test("construirDatosBoletin mapea y formatea los campos clave", () => {
  const d = construirDatosBoletin(ctx());
  assertEquals(d.instalacionId, "11111111-1111-1111-1111-111111111111");
  assertEquals(d.empresaNombre, "Recreativos Demo");
  assertEquals(d.empresaCif, "B12345678");
  assertEquals(d.localNombre, "Bar Pepe");
  assertEquals(d.titularNombre, "José García");
  assertEquals(d.titularDocumento, "12345678Z");
  assertEquals(d.numeroSerie, "SN-0001");
  assertEquals(d.modelo, "Cirsa Winner");
  assertEquals(d.fabricante, "Cirsa");
  assertEquals(d.licenciaNumero, "LIC-2026-001");
  assertEquals(d.fechaInstalacion, "19/05/2026");
  assertEquals(d.contadorEntradasBase, 1000);
  assertEquals(d.contadorSalidasBase, 250);
  assertEquals(d.valorCredito, "0,20 €");
  assertEquals(d.tasaSemanal, "12,50 €");
  assertEquals(d.porcentajeLocal, "40,00 %");
});

Deno.test("construirDatosBoletin conserva nulos opcionales", () => {
  const d = construirDatosBoletin(
    ctx({
      maquina: {
        numero_serie: "SN-9",
        modelo: null,
        fabricante: null,
        valor_credito: "0.10",
      },
      local: {
        nombre: "Local sin titular",
        direccion: null,
        titular_nombre: null,
        cif_o_nif: null,
      },
    }),
  );
  assertEquals(d.modelo, null);
  assertEquals(d.fabricante, null);
  assertEquals(d.localDireccion, null);
  assertEquals(d.titularNombre, null);
  assertEquals(d.titularDocumento, null);
});

Deno.test("construirDatosBoletin acepta fecha_inicio en formato datetime", () => {
  const d = construirDatosBoletin(
    ctx({
      instalacion: {
        id: "x",
        fecha_inicio: "2026-01-09T08:30:00Z",
        tasa_semanal: "0",
        porcentaje_local: "0",
        contador_entradas_base: 0,
        contador_salidas_base: 0,
        estado: "activa",
      },
    }),
  );
  assertEquals(d.fechaInstalacion, "09/01/2026");
});

Deno.test("formatDecimalEs agrupa miles y fija 2 decimales", () => {
  assertEquals(formatDecimalEs("1234.5"), "1.234,50");
  assertEquals(formatDecimalEs("1234567.89"), "1.234.567,89");
  assertEquals(formatDecimalEs("0"), "0,00");
  assertEquals(formatDecimalEs("0.1"), "0,10");
  assertEquals(formatDecimalEs("100"), "100,00");
  assertEquals(formatDecimalEs("-12.5"), "-12,50");
});

Deno.test("formatEurosEs y formatPorcentajeEs añaden sufijo", () => {
  assertEquals(formatEurosEs("1500.00"), "1.500,00 €");
  assertEquals(formatPorcentajeEs("33.33"), "33,33 %");
});
