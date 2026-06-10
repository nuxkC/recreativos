/**
 * Mapeo de recaudaciones al CSV para gestoría.
 *
 * Formato de importes pensado para importar en hoja de cálculo es-ES:
 * coma decimal, sin separador de miles y sin símbolo de moneda, de forma que
 * la celda sea numérica y operable. Las fechas se formatean en la zona
 * horaria de la empresa (date-fns-tz).
 */

import { formatInTimeZone } from "date-fns-tz";
import Decimal from "decimal.js";

import type { Recaudacion } from "@/lib/recaudaciones/types";

import { toCsv } from "./csv";

/** Etiquetas i18n que el Route Handler inyecta para mantener el mapeo puro. */
export interface RecaudacionesCsvLabels {
  fecha: string;
  local: string;
  maquina: string;
  modelo: string;
  bruto: string;
  tasa: string;
  neto: string;
  parteLocal: string;
  parteEmpresa: string;
  tecnico: string;
  estado: string;
  estadoFirme: string;
  estadoAnulada: string;
}

/**
 * Formatea un numeric (string de Postgres) como importe para gestoría:
 * dos decimales con coma, sin símbolo ni separador de miles. Valores vacíos o
 * no parseables devuelven cadena vacía.
 */
export function formatImporteCsv(value: string | null | undefined): string {
  if (value === null || value === undefined || value === "") {
    return "";
  }
  try {
    return new Decimal(value).toFixed(2).replace(".", ",");
  } catch {
    return "";
  }
}

/** Formatea una fecha ISO en la zona horaria de la empresa como `dd/MM/yyyy HH:mm`. */
export function formatFechaCsv(iso: string | null | undefined, zonaHoraria: string): string {
  if (!iso) {
    return "";
  }
  try {
    return formatInTimeZone(new Date(iso), zonaHoraria, "dd/MM/yyyy HH:mm");
  } catch {
    return iso;
  }
}

/** Serializa el listado de recaudaciones a CSV con las columnas clave para gestoría. */
export function recaudacionesToCsv(
  recaudaciones: readonly Recaudacion[],
  zonaHoraria: string,
  labels: RecaudacionesCsvLabels,
): string {
  const headers = [
    labels.fecha,
    labels.local,
    labels.maquina,
    labels.modelo,
    labels.bruto,
    labels.tasa,
    labels.neto,
    labels.parteLocal,
    labels.parteEmpresa,
    labels.tecnico,
    labels.estado,
  ];

  const rows = recaudaciones.map((rec) => [
    formatFechaCsv(rec.fecha, zonaHoraria),
    rec.instalacion?.local?.nombre ?? "",
    rec.instalacion?.maquina?.numeroSerie ?? "",
    rec.instalacion?.maquina?.modelo ?? "",
    formatImporteCsv(rec.recaudacionBruta),
    formatImporteCsv(rec.tasaTotalAplicada),
    formatImporteCsv(rec.recaudacionNeta),
    formatImporteCsv(rec.parteLocal),
    formatImporteCsv(rec.parteEmpresa),
    rec.tecnico?.nombreCompleto ?? "",
    rec.estado === "anulada" ? labels.estadoAnulada : labels.estadoFirme,
  ]);

  return toCsv(headers, rows);
}

/**
 * Construye el nombre del fichero a partir del rango activo. Si no hay rango
 * se usa `fallbackFecha` (p. ej. la fecha actual `YYYY-MM-DD`).
 */
export function nombreFicheroRecaudacionesCsv(
  desde: string | null,
  hasta: string | null,
  fallbackFecha: string,
): string {
  const partes = [desde, hasta].filter((parte): parte is string => Boolean(parte));
  const sufijo = partes.length > 0 ? partes.join("_") : fallbackFecha;
  return `recaudaciones_${sufijo}.csv`;
}
