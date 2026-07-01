import { z } from "zod";

/**
 * Validadores de formato compartidos por los schemas Zod de los formularios.
 * Casa canónica única en TS: lo importan los schemas (que a su vez consumen
 * el formulario cliente y la Server Action). El puerto Kotlin equivalente vive
 * en android `GestionShared.kt`; ambos se mantienen alineados por los mismos
 * vectores oro en sus test suites.
 */

/** Trim + null si queda vacío. */
export const trimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v));

// Regex de email canónico: pragmático, exige dominio con TLD (2+ letras).
// Byte-idéntico al de android GestionShared.kt (esEmailValido); ambos se fijan
// con los mismos vectores oro. Sustituye a z.string().email, deprecado en zod 4.
const EMAIL_REGEX = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

/** True si `raw` tiene formato de email válido (local@dominio.tld). */
export function esEmailValido(raw: string): boolean {
  return EMAIL_REGEX.test(raw);
}

/** Email opcional: vacío → null; si hay valor, valida formato. */
export const emailOptional = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v))
  .pipe(
    z
      .string()
      .refine((v) => esEmailValido(v), { message: "emailInvalido" })
      .nullable(),
  );

const DNI_LETRAS = "TRWAGMYFPDXBNJZSQVHLCKE";
const CIF_LETRAS_CONTROL = "JABCDEFGHI";

/** Mayúsculas, sin espacios ni guiones. */
function normalizarDocumento(raw: string): string {
  return raw.toUpperCase().replace(/[\s-]/g, "");
}

function esNif(doc: string): boolean {
  const numero = Number.parseInt(doc.slice(0, 8), 10);
  return doc[8] === DNI_LETRAS[numero % 23];
}

function esNie(doc: string): boolean {
  const prefijo = doc[0] === "X" ? "0" : doc[0] === "Y" ? "1" : "2";
  const numero = Number.parseInt(prefijo + doc.slice(1, 8), 10);
  return doc[8] === DNI_LETRAS[numero % 23];
}

function esCif(doc: string): boolean {
  // charAt (no `doc[0]`) para que el tipo sea `string` bajo
  // noUncheckedIndexedAccess: el doc ya viene validado con 9 chars.
  const letra = doc.charAt(0);
  const control = doc.charAt(8);
  let suma = 0;
  for (let i = 0; i < 7; i++) {
    let n = doc.charCodeAt(i + 1) - 48; // dígitos en posiciones 1..7
    if (i % 2 === 0) {
      // posiciones impares (1ª, 3ª, …) se multiplican por 2 y se "suman dígitos"
      n *= 2;
      if (n > 9) n -= 9;
    }
    suma += n;
  }
  const e = (10 - (suma % 10)) % 10;
  const digitoControl = String(e);
  const letraControl = CIF_LETRAS_CONTROL[e];
  if ("PQSNWK".includes(letra)) return control === letraControl; // control por letra
  if ("ABEH".includes(letra)) return control === digitoControl; // control por dígito
  return control === digitoControl || control === letraControl; // ambos válidos
}

/** True si `raw` es un NIF, NIE o CIF español válido (dígito de control real). */
export function esCifNif(raw: string): boolean {
  const doc = normalizarDocumento(raw);
  if (/^\d{8}[A-Z]$/.test(doc)) return esNif(doc);
  if (/^[XYZ]\d{7}[A-Z]$/.test(doc)) return esNie(doc);
  if (/^[ABCDEFGHJKLMNPQRSUVW]\d{7}[0-9A-J]$/.test(doc)) return esCif(doc);
  return false;
}

/** Quita espacios/guiones y el prefijo internacional +34 / 0034. */
export function normalizarTelefonoEs(raw: string): string {
  return raw.replace(/[\s-]/g, "").replace(/^(\+34|0034)/, "");
}

/** True si `raw` es un teléfono español válido (9 dígitos, empieza 6-9). */
export function esTelefonoEs(raw: string): boolean {
  return /^[6-9]\d{8}$/.test(normalizarTelefonoEs(raw));
}

/**
 * Campo CIF/NIF opcional. Vacío → null (sin error); con valor, valida longitud
 * y dígito de control. `maxMessage` es la clave i18n del error de longitud
 * (difiere entre forms: "cifONifMuyLargo" en local, "cifMuyLargo" en ajustes).
 */
export function cifNifSchema(maxMessage: string) {
  return trimmedString.pipe(
    z
      .string()
      .max(20, { message: maxMessage })
      .refine((v) => esCifNif(v), { message: "cifInvalido" })
      .nullable(),
  );
}

/** Campo teléfono opcional. Vacío → null; con valor, valida longitud y formato. */
export function telefonoSchema(maxMessage: string) {
  return trimmedString.pipe(
    z
      .string()
      .max(30, { message: maxMessage })
      .refine((v) => esTelefonoEs(v), { message: "telefonoInvalido" })
      .nullable(),
  );
}
