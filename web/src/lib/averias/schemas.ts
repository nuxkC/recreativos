import Decimal from "decimal.js";
import { z } from "zod";

import { CATEGORIAS_AVERIA } from "./types";

/**
 * Schemas de alta/edición de avería y de recambio. Los comparten el
 * formulario cliente y las Server Actions (defensa en profundidad).
 *
 * El `coste` de un recambio es dinero: se valida y normaliza con `Decimal`
 * (≥ 0, ≤ 99999,99 €, dos decimales) y se transmite como string. Es opcional:
 * vacío → null (es informativo, no se recupera de la recaudación).
 */

const trimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v));

const costeSchema = z
  .string()
  .trim()
  .transform((raw, ctx) => {
    if (raw.length === 0) return null;
    const normalized = raw.replace(",", ".");
    let dec: Decimal;
    try {
      dec = new Decimal(normalized);
    } catch {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "costeInvalido" });
      return z.NEVER;
    }
    if (!dec.isFinite()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "costeInvalido" });
      return z.NEVER;
    }
    if (dec.lessThan(0)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "costeNegativo" });
      return z.NEVER;
    }
    if (dec.greaterThan("99999.99")) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "costeMaximo" });
      return z.NEVER;
    }
    if (dec.decimalPlaces() > 2) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "costeDecimales" });
      return z.NEVER;
    }
    return dec.toFixed(2);
  });

export const AveriaInputSchema = z.object({
  categoria: z.enum(CATEGORIAS_AVERIA, {
    errorMap: () => ({ message: "categoriaRequerida" }),
  }),
  descripcion: trimmedString.pipe(z.string().max(2000).nullable()),
  poneMaquinaFueraServicio: z.coerce.boolean(),
  notas: trimmedString.pipe(z.string().max(2000).nullable()),
});

export type AveriaInput = z.infer<typeof AveriaInputSchema>;

/**
 * Alta de avería con el bloque de tolva (§5.6): si la avería hizo que la
 * máquina pagara premio de la tolva, `afectaTolva` lo marca e `importeTolva`
 * (mismo tratamiento `Decimal` que el coste) recoge cuánto. Solo aplica al
 * alta — `crear_averia` inserta la `merma`; la edición no toca la tolva.
 */
export const CrearAveriaInputSchema = AveriaInputSchema.extend({
  afectaTolva: z.coerce.boolean(),
  importeTolva: costeSchema,
}).superRefine((val, ctx) => {
  if (
    val.afectaTolva &&
    (val.importeTolva === null || new Decimal(val.importeTolva).lessThanOrEqualTo(0))
  ) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["importeTolva"],
      message: "importeTolvaRequerido",
    });
  }
});

export type CrearAveriaInput = z.infer<typeof CrearAveriaInputSchema>;

export const RecambioInputSchema = z.object({
  pieza: z
    .string()
    .trim()
    .min(1, { message: "piezaRequerida" })
    .max(120, { message: "piezaMuyLarga" }),
  cantidad: z.coerce
    .number({ invalid_type_error: "cantidadEntera" })
    .int({ message: "cantidadEntera" })
    .positive({ message: "cantidadPositiva" }),
  coste: costeSchema,
  notas: trimmedString.pipe(z.string().max(500).nullable()),
});

export type RecambioInput = z.infer<typeof RecambioInputSchema>;

/** Notas de resolución (opcionales) al cerrar una avería. */
export const ResolucionInputSchema = z.object({
  notasResolucion: trimmedString.pipe(z.string().max(2000).nullable()),
});

export type ResolucionInput = z.infer<typeof ResolucionInputSchema>;
