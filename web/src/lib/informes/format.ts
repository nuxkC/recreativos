import { format, parseISO } from "date-fns";
import { es } from "date-fns/locale";

/**
 * Formateadores de la feature `informes`.
 *
 * Las cifras que se muestran aquí son sumas ya calculadas por el servidor
 * (vistas de agregación). Se representan con `Intl.NumberFormat` en es-ES,
 * coherente con `formatEur` de recaudaciones pero operando sobre `number`
 * porque las gráficas trabajan con números.
 */

const EUR_FORMATTER = new Intl.NumberFormat("es-ES", {
  style: "currency",
  currency: "EUR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const EUR_COMPACTO = new Intl.NumberFormat("es-ES", {
  style: "currency",
  currency: "EUR",
  notation: "compact",
  maximumFractionDigits: 1,
});

/** "1.234,56 €" */
export function formatEuros(value: number): string {
  if (!Number.isFinite(value)) return "—";
  return EUR_FORMATTER.format(value);
}

/** "1,2 mil €" — para ejes de gráficas donde el espacio es limitado. */
export function formatEurosCompacto(value: number): string {
  if (!Number.isFinite(value)) return "—";
  return EUR_COMPACTO.format(value);
}

/** "ene 26" — etiqueta corta para el eje X de la evolución mensual. */
export function formatMesCorto(mesIso: string): string {
  try {
    return format(parseISO(mesIso), "LLL yy", { locale: es });
  } catch {
    return mesIso;
  }
}

/** "enero de 2026" — etiqueta larga para tooltips y tablas. */
export function formatMesLargo(mesIso: string): string {
  try {
    return format(parseISO(mesIso), "LLLL 'de' yyyy", { locale: es });
  } catch {
    return mesIso;
  }
}
