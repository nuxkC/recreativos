import Decimal from "decimal.js";
import { format, parseISO } from "date-fns";
import { es } from "date-fns/locale";

/** Formatea un decimal almacenado como string (p. ej. "12.34") en € español. */
export function formatEur(value: string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "—";
  let dec: Decimal;
  try {
    dec = new Decimal(value);
  } catch {
    return value;
  }
  const formatted = dec.toFixed(2).replace(".", ",");
  return `${formatted} €`;
}

/** Formatea un porcentaje almacenado como string ("50.00") como "50,00 %". */
export function formatPercent(value: string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "—";
  let dec: Decimal;
  try {
    dec = new Decimal(value);
  } catch {
    return value;
  }
  return `${dec.toFixed(2).replace(".", ",")} %`;
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  try {
    return format(parseISO(iso), "dd/MM/yyyy HH:mm", { locale: es });
  } catch {
    return iso;
  }
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  try {
    return format(parseISO(iso), "dd/MM/yyyy", { locale: es });
  } catch {
    return iso;
  }
}
