/**
 * T-203 — Generación server-side del boletín digital de instalación.
 *
 * El boletín es el documento (PDF A4) que certifica la instalación de una
 * máquina recreativa en un local. Se genera por instalación (alta) y se
 * archiva en el bucket privado `boletines`. Recoge: datos de empresa, local,
 * máquina, licencia, fecha de instalación, contadores base (baseline),
 * tasa y reparto vigentes, valor del crédito y, si está disponible, una firma.
 *
 * La función de composición reutiliza las primitivas de `pdf-helpers.ts`,
 * compartidas con el ticket de recaudación (`pdf.ts`).
 */

import { crearPdfWriter, formatEurosEs, formatPorcentajeEs } from "./pdf-helpers.ts";

/** Contexto agregado de una instalación necesario para el boletín. */
export interface BoletinInstalacionContext {
  instalacion: {
    id: string;
    fecha_inicio: string;
    tasa_semanal: string;
    porcentaje_local: string;
    contador_entradas_base: number;
    contador_salidas_base: number;
    estado: string;
  };
  empresa: {
    nombre: string;
    cif: string | null;
    direccion?: string | null;
    zona_horaria: string;
  };
  local: {
    nombre: string;
    direccion: string | null;
    titular_nombre: string | null;
    cif_o_nif: string | null;
  };
  maquina: {
    numero_serie: string;
    modelo: string | null;
    fabricante: string | null;
    valor_credito: string;
  };
  licencia: {
    numero: string;
  };
}

/**
 * Datos ya normalizados/formateados que se vuelcan al PDF. Separar este
 * mapeo de la composición permite testarlo sin generar el PDF.
 */
export interface BoletinDatos {
  instalacionId: string;
  empresaNombre: string;
  empresaCif: string | null;
  empresaDireccion: string | null;
  localNombre: string;
  localDireccion: string | null;
  titularNombre: string | null;
  titularDocumento: string | null;
  numeroSerie: string;
  modelo: string | null;
  fabricante: string | null;
  licenciaNumero: string;
  fechaInstalacion: string;
  contadorEntradasBase: number;
  contadorSalidasBase: number;
  valorCredito: string;
  tasaSemanal: string;
  porcentajeLocal: string;
}

const SIN_DATO = "—";

/** Construye los datos del boletín a partir del contexto de la instalación. */
export function construirDatosBoletin(ctx: BoletinInstalacionContext): BoletinDatos {
  return {
    instalacionId: ctx.instalacion.id,
    empresaNombre: ctx.empresa.nombre,
    empresaCif: ctx.empresa.cif,
    empresaDireccion: ctx.empresa.direccion ?? null,
    localNombre: ctx.local.nombre,
    localDireccion: ctx.local.direccion,
    titularNombre: ctx.local.titular_nombre,
    titularDocumento: ctx.local.cif_o_nif,
    numeroSerie: ctx.maquina.numero_serie,
    modelo: ctx.maquina.modelo,
    fabricante: ctx.maquina.fabricante,
    licenciaNumero: ctx.licencia.numero,
    fechaInstalacion: formatFechaEs(ctx.instalacion.fecha_inicio, ctx.empresa.zona_horaria),
    contadorEntradasBase: ctx.instalacion.contador_entradas_base,
    contadorSalidasBase: ctx.instalacion.contador_salidas_base,
    valorCredito: formatEurosEs(ctx.maquina.valor_credito),
    tasaSemanal: formatEurosEs(ctx.instalacion.tasa_semanal),
    porcentajeLocal: formatPorcentajeEs(ctx.instalacion.porcentaje_local),
  };
}

interface BoletinPdfInput {
  ctx: BoletinInstalacionContext;
  /** Fecha de emisión del documento (ISO). */
  emitidoEn: string;
  /** Logo de la empresa (PNG) opcional, para la cabecera. */
  logoPng?: Uint8Array;
  /** Firma (PNG) opcional, si estuviera disponible. */
  firmaPng?: Uint8Array;
}

/** Genera el PDF del boletín de instalación. */
export async function generarPdfBoletin(input: BoletinPdfInput): Promise<Uint8Array> {
  const d = construirDatosBoletin(input.ctx);
  const w = await crearPdfWriter("helvetica");

  if (input.logoPng) {
    await w.drawLogo(input.logoPng);
  }

  // Cabecera empresa
  w.line(d.empresaNombre.toUpperCase(), { bold: true, size: 16 });
  if (d.empresaCif) w.line(`CIF: ${d.empresaCif}`);
  if (d.empresaDireccion) w.line(d.empresaDireccion);
  w.gap(6);
  w.line("BOLETÍN DE INSTALACIÓN", { bold: true, size: 13 });
  w.line(
    "Documento que certifica la instalación de una máquina recreativa en el local indicado.",
    { size: 9, color: { r: 0.3, g: 0.3, b: 0.3 } },
  );
  w.separator();

  // Local
  w.line("Local", { bold: true, size: 11 });
  w.kv("Nombre:", d.localNombre);
  w.kv("Dirección:", d.localDireccion ?? SIN_DATO);
  if (d.titularNombre) w.kv("Titular:", d.titularNombre);
  if (d.titularDocumento) w.kv("CIF/NIF titular:", d.titularDocumento);
  w.separator();

  // Máquina
  w.line("Máquina", { bold: true, size: 11 });
  w.kv("Número de serie:", d.numeroSerie);
  w.kv("Modelo:", d.modelo ?? SIN_DATO);
  w.kv("Fabricante:", d.fabricante ?? SIN_DATO);
  w.kv("Valor del crédito:", d.valorCredito);
  w.separator();

  // Licencia + condiciones
  w.line("Licencia y condiciones", { bold: true, size: 11 });
  w.kv("Número de licencia:", d.licenciaNumero);
  w.kv("Fecha de instalación:", d.fechaInstalacion);
  w.kv("Tasa semanal:", d.tasaSemanal);
  w.kv("Reparto al local:", d.porcentajeLocal);
  w.separator();

  // Contadores base (baseline)
  w.line("Contadores base (baseline)", { bold: true, size: 11 });
  w.kv("Entradas:", String(d.contadorEntradasBase));
  w.kv("Salidas:", String(d.contadorSalidasBase));
  w.line(
    "Lecturas de los contadores en la fecha de instalación; punto de partida del cálculo.",
    { size: 9, color: { r: 0.3, g: 0.3, b: 0.3 } },
  );
  w.separator();

  // Firma (si está disponible)
  if (input.firmaPng) {
    w.line("Firma del titular:", { bold: true });
    const ok = await w.drawPng(input.firmaPng, 60, 200);
    if (!ok) w.line("[firma no embeddable]");
    w.separator();
  }

  // Pie
  w.line(`Instalación id: ${d.instalacionId}`, { size: 7, color: { r: 0.4, g: 0.4, b: 0.4 } });
  w.line(`Documento emitido: ${formatFechaHoraEs(input.emitidoEn)}`, {
    size: 7,
    color: { r: 0.4, g: 0.4, b: 0.4 },
  });

  return await w.pdf.save();
}

/** Formatea una fecha ISO (date o datetime) a `dd/MM/yyyy` es-ES. */
function formatFechaEs(iso: string, _tz: string): string {
  const d = parseIsoDate(iso);
  if (!d) return iso;
  const dd = String(d.getUTCDate()).padStart(2, "0");
  const mm = String(d.getUTCMonth() + 1).padStart(2, "0");
  const yyyy = d.getUTCFullYear();
  return `${dd}/${mm}/${yyyy}`;
}

/** Formatea una fecha/hora ISO a `dd/MM/yyyy HH:mm` (UTC). */
function formatFechaHoraEs(iso: string): string {
  const d = parseIsoDate(iso);
  if (!d) return iso;
  const fecha = formatFechaEs(iso, "");
  const hh = String(d.getUTCHours()).padStart(2, "0");
  const min = String(d.getUTCMinutes()).padStart(2, "0");
  return `${fecha} ${hh}:${min}`;
}

function parseIsoDate(iso: string): Date | null {
  // Acepta tanto `2026-05-19` como `2026-05-19T10:00:00Z`.
  const normalized = /^\d{4}-\d{2}-\d{2}$/.test(iso) ? `${iso}T00:00:00Z` : iso;
  const d = new Date(normalized);
  return Number.isNaN(d.getTime()) ? null : d;
}
