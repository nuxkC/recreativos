/**
 * Serialización CSV pura y sin dependencias.
 *
 * Decisiones de formato (orientado a gestoría / Excel es-ES):
 * - Separador de campos `;`. Excel en configuración regional española usa
 *   el punto y coma como separador de listas, y reserva la coma para los
 *   decimales. Así las columnas se reparten bien al abrir el fichero.
 * - Fin de línea CRLF (`\r\n`) según RFC 4180; es lo que espera Excel.
 * - BOM UTF-8 al inicio para que Excel detecte la codificación y muestre
 *   correctamente acentos y el símbolo €.
 *
 * El escapado sigue RFC 4180: un campo se entrecomilla si contiene el
 * separador, comillas dobles o saltos de línea, y las comillas internas se
 * duplican.
 */

export interface CsvOptions {
  /** Separador de campos. Por defecto `;` (Excel es-ES). */
  separator?: string;
  /** Fin de línea. Por defecto `\r\n` (RFC 4180 / Excel). */
  newline?: string;
  /** Antepone un BOM UTF-8. Por defecto `true`. */
  bom?: boolean;
}

/** Marca de orden de bytes UTF-8. */
export const UTF8_BOM = "\uFEFF";

const DEFAULT_SEPARATOR = ";";
const DEFAULT_NEWLINE = "\r\n";

/**
 * Escapa un valor para una celda CSV. Devuelve el valor entrecomillado solo
 * cuando es necesario (contiene separador, comillas o saltos de línea).
 */
export function escapeCsvField(value: string, separator: string): string {
  const mustQuote =
    value.includes(separator) ||
    value.includes('"') ||
    value.includes("\n") ||
    value.includes("\r");
  if (!mustQuote) {
    return value;
  }
  return `"${value.replace(/"/g, '""')}"`;
}

/**
 * Serializa cabeceras + filas a una cadena CSV. Los valores `null`/`undefined`
 * se tratan como celda vacía. Con `rows` vacío se devuelve solo la cabecera.
 */
export function toCsv(
  headers: readonly string[],
  rows: readonly (readonly (string | null | undefined)[])[],
  options: CsvOptions = {},
): string {
  const separator = options.separator ?? DEFAULT_SEPARATOR;
  const newline = options.newline ?? DEFAULT_NEWLINE;
  const bom = options.bom ?? true;

  const lines: string[] = [];
  lines.push(headers.map((header) => escapeCsvField(header, separator)).join(separator));
  for (const row of rows) {
    lines.push(row.map((cell) => escapeCsvField(cell ?? "", separator)).join(separator));
  }

  const body = lines.join(newline);
  return bom ? `${UTF8_BOM}${body}` : body;
}
