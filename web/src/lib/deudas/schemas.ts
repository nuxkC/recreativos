import Decimal from "decimal.js";
import { z } from "zod";

/**
 * Schemas de las acciones de deudas. Los importan los formularios cliente
 * (validación inmediata) y las Server Actions (defensa en profundidad).
 *
 * Los importes (`numeric` en Postgres) se emiten como string `"X.YY"`;
 * aceptan coma o punto decimal. Nunca como number.
 */

const isoDateRegex = /^\d{4}-\d{2}-\d{2}$/;

const trimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v));

/** Fecha ISO opcional (vacío → null). */
const isoDateOpcional = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v))
  .pipe(z.string().regex(isoDateRegex, { message: "fechaInvalida" }).nullable());

/** Importe en euros estrictamente positivo, hasta numeric(10,2). */
const importePositivo = z
  .string()
  .trim()
  .min(1, { message: "importeRequerido" })
  .transform((v) => v.replace(",", "."))
  .pipe(z.string().refine((v) => /^\d+(\.\d{1,2})?$/.test(v), { message: "importeInvalido" }))
  .transform((v) => new Decimal(v))
  .refine((d) => d.gt(0), { message: "importePositivo" })
  .refine((d) => d.lte(99999999.99), { message: "importeFueraDeRango" })
  .transform((d) => d.toFixed(2));

export const PrestamoInputSchema = z.object({
  principal: importePositivo,
  fecha: isoDateOpcional,
  // El concepto es OBLIGATORIO al dar de alta un préstamo (T-216): sin él
  // acabábamos con deudas sin saber el porqué. El backend lo exige también.
  notas: z
    .string()
    .trim()
    .min(1, { message: "conceptoRequerido" })
    .max(2000, { message: "notasMuyLargas" }),
});

export type PrestamoInput = z.infer<typeof PrestamoInputSchema>;

export const RecuperacionEfectivoSchema = z.object({
  importe: importePositivo,
  notas: trimmedString.pipe(z.string().max(2000, { message: "notasMuyLargas" }).nullable()),
});

export type RecuperacionEfectivoInput = z.infer<typeof RecuperacionEfectivoSchema>;
