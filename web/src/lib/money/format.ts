import Decimal from "decimal.js";

export interface EurFragments {
  /** true si el importe es negativo. El signo «−» debe anunciarse: NO es aria-hidden. */
  negative: boolean;
  /** Parte entera agrupada en miles es-ES con «.» (p. ej. "1.234"). */
  integer: string;
  /** Dos dígitos decimales, SIEMPRE. */
  decimals: string;
  /** true si la entrada no es un decimal válido (no se renderiza cifra). */
  invalid: boolean;
}

const THOUSANDS = /\B(?=(\d{3})+(?!\d))/g;

/**
 * Descompone un importe (string decimal del servidor) en fragmentos money-safe
 * para presentación. NUNCA pasa por Number/toNumber(): agrupa los miles sobre
 * el string exacto de `Decimal.toFixed(2)`, preservando los céntimos en
 * importes grandes (99.999.999,99). El signo se extrae aparte para poder teñir
 * o animar los dígitos sin perderlo. Es el único formateador de dinero de la UI.
 */
export function splitEur(value: string | null | undefined): EurFragments {
  if (value === null || value === undefined || value === "") {
    return { negative: false, integer: "", decimals: "", invalid: true };
  }
  let dec: Decimal;
  try {
    dec = new Decimal(value);
  } catch {
    return { negative: false, integer: "", decimals: "", invalid: true };
  }
  const negative = dec.isNegative() && !dec.isZero();
  const fixed = dec.abs().toFixed(2); // "1234.56" — money-safe, sin Number
  const [intPart = "0", decPart = "00"] = fixed.split(".");
  return {
    negative,
    integer: intPart.replace(THOUSANDS, "."),
    decimals: decPart,
    invalid: false,
  };
}

/**
 * aria-label natural de un importe, derivado del Decimal exacto (incluye el
 * signo: el lector debe anunciar «menos»). No usa los separadores visuales de
 * miles para que el lector no lea «punto».
 */
export function eurAriaLabel(value: string | null | undefined): string {
  const f = splitEur(value);
  if (f.invalid) return "importe no disponible";
  const entero = f.integer.replace(/\./g, ""); // sin separador de miles para el lector
  const signo = f.negative ? "menos " : "";
  return `${signo}${entero},${f.decimals} euros`;
}
