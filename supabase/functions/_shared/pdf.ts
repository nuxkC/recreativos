/**
 * T-25 — Generación server-side del PDF del ticket de recaudación.
 *
 * El PDF es la copia de archivo (A4) que se guarda en el bucket `tickets/`.
 * El ticket que se imprime físicamente lo genera el Android via ESC/POS
 * (T-62), no este PDF.
 *
 * Layout: simple, monoespaciado, con cabecera de empresa, datos del local,
 * lecturas, importes, desglose de denominaciones y firma embebida.
 */

import { PDFDocument, rgb, StandardFonts } from "pdf-lib";

import type {
  CalculoRecaudacionResult,
  DenominacionItem,
  EmpresaContext,
  InstalacionContext,
} from "./types.ts";

interface PdfInput {
  empresa: EmpresaContext;
  instalacion: InstalacionContext;
  recaudacionId: string;
  tecnicoNombre: string;
  fecha: string;
  contadoresAnterior: { entradas: number; salidas: number };
  contadoresActual: { entradas: number; salidas: number };
  resultado: CalculoRecaudacionResult;
  desgloseTotal: readonly DenominacionItem[];
  desgloseLocal: readonly DenominacionItem[];
  firmaPng?: Uint8Array;
  observaciones?: string;
  /** Datos de auditoría que sólo aparecen en el archivo PDF, no en el ticket impreso. */
  idempotencyKey: string;
  dispositivoId?: string;
}

const MARGIN = 50;
const LINE_HEIGHT = 14;

export async function generarPdfTicket(input: PdfInput): Promise<Uint8Array> {
  const pdf = await PDFDocument.create();
  const page = pdf.addPage(); // A4 por defecto: 595.28 x 841.89
  const font = await pdf.embedFont(StandardFonts.Courier);
  const fontBold = await pdf.embedFont(StandardFonts.CourierBold);

  let y = page.getHeight() - MARGIN;

  const writeLine = (text: string, opts: { bold?: boolean; size?: number } = {}) => {
    page.drawText(text, {
      x: MARGIN,
      y,
      size: opts.size ?? 10,
      font: opts.bold ? fontBold : font,
      color: rgb(0, 0, 0),
    });
    y -= LINE_HEIGHT;
  };

  const writeKv = (label: string, value: string) => {
    page.drawText(label, { x: MARGIN, y, size: 10, font, color: rgb(0.3, 0.3, 0.3) });
    page.drawText(value, { x: MARGIN + 180, y, size: 10, font: fontBold, color: rgb(0, 0, 0) });
    y -= LINE_HEIGHT;
  };

  const separator = () => {
    y -= 4;
    page.drawLine({
      start: { x: MARGIN, y },
      end: { x: page.getWidth() - MARGIN, y },
      thickness: 0.6,
      color: rgb(0.7, 0.7, 0.7),
    });
    y -= 10;
  };

  // Cabecera empresa
  writeLine(input.empresa.nombre.toUpperCase(), { bold: true, size: 14 });
  if (input.empresa.cif) writeLine(`CIF: ${input.empresa.cif}`);
  if (input.empresa.ticket_cabecera) writeLine(input.empresa.ticket_cabecera);
  separator();

  // Recaudación
  writeLine("RECAUDACIÓN", { bold: true, size: 12 });
  writeKv("Fecha:", formatDate(input.fecha, input.empresa.zona_horaria));
  writeKv("Local:", input.instalacion.local.nombre);
  if (input.instalacion.local.direccion) {
    writeKv("Dirección:", input.instalacion.local.direccion);
  }
  writeKv(
    "Máquina:",
    `${input.instalacion.maquina.numero_serie}` +
      (input.instalacion.maquina.modelo ? ` (${input.instalacion.maquina.modelo})` : ""),
  );
  writeKv("Licencia:", input.instalacion.licencia.numero);
  writeKv("Técnico:", input.tecnicoNombre);
  separator();

  // Contadores
  writeKv(
    "Cont. Entradas:",
    `${input.contadoresAnterior.entradas} → ${input.contadoresActual.entradas}`,
  );
  writeKv(
    "Cont. Salidas:",
    `${input.contadoresAnterior.salidas} → ${input.contadoresActual.salidas}`,
  );
  writeKv("Valor crédito:", `${input.resultado.valor_credito} €`);
  separator();

  // Importes
  writeKv("Bruto:", `${input.resultado.bruto} €`);
  writeKv("Semanas tasa:", String(input.resultado.semanas));
  writeKv("Tasa semanal:", `${input.resultado.tasa_semanal} €`);
  writeKv("Tasa total:", `${input.resultado.tasa_total} €`);
  writeKv("Neto:", `${input.resultado.neto} €`);
  writeKv("% Local:", `${input.resultado.porcentaje_local} %`);
  writeKv("Parte Local:", `${input.resultado.parte_local} €`);
  writeKv("Parte Empresa:", `${input.resultado.parte_empresa} €`);
  separator();

  // Desglose total
  writeLine("Desglose total:", { bold: true });
  for (const item of input.desgloseTotal) {
    writeLine(
      `  ${formatEur(item.denominacion)} x ${item.cantidad} = ${
        formatEur(item.denominacion * item.cantidad)
      }`,
    );
  }
  y -= 4;
  writeLine("Desglose parte local:", { bold: true });
  for (const item of input.desgloseLocal) {
    writeLine(
      `  ${formatEur(item.denominacion)} x ${item.cantidad} = ${
        formatEur(item.denominacion * item.cantidad)
      }`,
    );
  }
  separator();

  // Firma
  if (input.firmaPng) {
    writeLine("Firma titular:", { bold: true });
    try {
      const png = await pdf.embedPng(input.firmaPng);
      const ratio = png.width / png.height;
      const targetHeight = 60;
      const targetWidth = targetHeight * ratio;
      page.drawImage(png, {
        x: MARGIN,
        y: y - targetHeight,
        width: Math.min(targetWidth, 200),
        height: targetHeight,
      });
      y -= targetHeight + 10;
    } catch {
      writeLine("[firma no embeddable]");
    }
  }

  if (input.observaciones && input.observaciones.length > 0) {
    separator();
    writeLine("Observaciones:", { bold: true });
    writeLine(input.observaciones);
  }

  // Pie de auditoría (no aparece en ticket térmico)
  separator();
  writeLine(`Recaudación id: ${input.recaudacionId}`, { size: 7 });
  writeLine(`Idempotency: ${input.idempotencyKey}`, { size: 7 });
  if (input.dispositivoId) writeLine(`Dispositivo: ${input.dispositivoId}`, { size: 7 });

  return await pdf.save();
}

function formatEur(n: number): string {
  return `${n.toFixed(2)} €`;
}

function formatDate(iso: string, _tz: string): string {
  // Formateo simple. Podemos sofisticarlo con date-fns-tz si hace falta.
  const d = new Date(iso);
  return d.toISOString().replace("T", " ").slice(0, 19);
}
