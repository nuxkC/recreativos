/**
 * T-25 — Generación server-side del PDF del ticket de recaudación.
 *
 * El PDF es la copia de archivo (A4) que se guarda en el bucket `tickets/`.
 * El ticket que se imprime físicamente lo genera el Android via ESC/POS
 * (T-62), no este PDF.
 *
 * Layout: simple, monoespaciado, con cabecera de empresa, datos del local,
 * lecturas, importes, desglose de denominaciones y firma embebida.
 *
 * Las primitivas de composición viven en `pdf-helpers.ts` (compartidas con
 * el boletín de instalación, T-203).
 */

import { crearPdfWriter } from "./pdf-helpers.ts";
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
  /** Importe retenido de la parte_local para amortizar deuda (T-214); "0.00" si nada. */
  recuperado?: string;
  /** Lo que se lleva el local tras la recuperación (= parte_local − recuperado). */
  pagadoLocal?: string;
  desgloseTotal: readonly DenominacionItem[];
  desgloseLocal: readonly DenominacionItem[];
  firmaPng?: Uint8Array;
  observaciones?: string;
  /** Datos de auditoría que sólo aparecen en el archivo PDF, no en el ticket impreso. */
  idempotencyKey: string;
  dispositivoId?: string;
}

export async function generarPdfTicket(input: PdfInput): Promise<Uint8Array> {
  const w = await crearPdfWriter("courier");

  // Cabecera empresa
  w.line(input.empresa.nombre.toUpperCase(), { bold: true, size: 14 });
  if (input.empresa.cif) w.line(`CIF: ${input.empresa.cif}`);
  if (input.empresa.ticket_cabecera) w.line(input.empresa.ticket_cabecera);
  w.separator();

  // Recaudación
  w.line("RECAUDACIÓN", { bold: true, size: 12 });
  w.kv("Fecha:", formatDate(input.fecha, input.empresa.zona_horaria));
  w.kv("Local:", input.instalacion.local.nombre);
  if (input.instalacion.local.direccion) {
    w.kv("Dirección:", input.instalacion.local.direccion);
  }
  w.kv(
    "Máquina:",
    `${input.instalacion.maquina.numero_serie}` +
      (input.instalacion.maquina.modelo ? ` (${input.instalacion.maquina.modelo})` : ""),
  );
  w.kv("Licencia:", input.instalacion.licencia.numero);
  w.kv("Técnico:", input.tecnicoNombre);
  w.separator();

  // Contadores
  w.kv(
    "Cont. Entradas:",
    `${input.contadoresAnterior.entradas} -> ${input.contadoresActual.entradas}`,
  );
  w.kv(
    "Cont. Salidas:",
    `${input.contadoresAnterior.salidas} -> ${input.contadoresActual.salidas}`,
  );
  w.kv("Valor crédito:", `${input.resultado.valor_credito} €`);
  w.separator();

  // Importes
  w.kv("Bruto:", `${input.resultado.bruto} €`);
  w.kv("Semanas tasa:", String(input.resultado.semanas));
  w.kv("Tasa semanal:", `${input.resultado.tasa_semanal} €`);
  w.kv("Tasa total:", `${input.resultado.tasa_total} €`);
  w.kv("Neto:", `${input.resultado.neto} €`);
  w.kv("% Local:", `${input.resultado.porcentaje_local} %`);
  w.kv("Parte Local:", `${input.resultado.parte_local} €`);
  const recuperado = input.recuperado ?? "0.00";
  if (Number(recuperado) > 0) {
    w.kv("Recuperado deuda:", `-${recuperado} €`);
    w.kv("Entregado al local:", `${input.pagadoLocal ?? input.resultado.parte_local} €`);
  }
  w.kv("Parte Empresa:", `${input.resultado.parte_empresa} €`);
  w.separator();

  // Desglose total
  w.line("Desglose total:", { bold: true });
  for (const item of input.desgloseTotal) {
    w.line(
      `  ${formatEur(item.denominacion)} x ${item.cantidad} = ${
        formatEur(item.denominacion * item.cantidad)
      }`,
    );
  }
  w.gap(4);
  w.line("Desglose parte local:", { bold: true });
  for (const item of input.desgloseLocal) {
    w.line(
      `  ${formatEur(item.denominacion)} x ${item.cantidad} = ${
        formatEur(item.denominacion * item.cantidad)
      }`,
    );
  }
  w.separator();

  // Firma
  if (input.firmaPng) {
    w.line("Firma titular:", { bold: true });
    const ok = await w.drawPng(input.firmaPng, 60, 200);
    if (!ok) w.line("[firma no embeddable]");
  }

  if (input.observaciones && input.observaciones.length > 0) {
    w.separator();
    w.line("Observaciones:", { bold: true });
    w.line(input.observaciones);
  }

  // Pie de auditoría (no aparece en ticket térmico)
  w.separator();
  w.line(`Recaudación id: ${input.recaudacionId}`, { size: 7 });
  w.line(`Idempotency: ${input.idempotencyKey}`, { size: 7 });
  if (input.dispositivoId) w.line(`Dispositivo: ${input.dispositivoId}`, { size: 7 });

  return await w.pdf.save();
}

function formatEur(n: number): string {
  return `${n.toFixed(2)} €`;
}

function formatDate(iso: string, _tz: string): string {
  // Formateo simple. Podemos sofisticarlo con date-fns-tz si hace falta.
  const d = new Date(iso);
  return d.toISOString().replace("T", " ").slice(0, 19);
}
