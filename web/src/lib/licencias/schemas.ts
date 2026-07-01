import { z } from "zod";

import { ESTADOS_LICENCIA } from "./types";

/**
 * Schema único para alta y edición. Lo importan tanto el formulario
 * cliente (validación inmediata con react-hook-form) como las Server
 * Actions (defensa en profundidad antes de tocar la BBDD).
 */

const trimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v));

const isoDateRegex = /^\d{4}-\d{2}-\d{2}$/;

const isoDateOptional = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v))
  .pipe(z.string().regex(isoDateRegex, { message: "fechaInvalida" }).nullable());

export const LicenciaInputSchema = z
  .object({
    numero: z
      .string()
      .trim()
      .min(1, { message: "numeroRequerido" })
      .max(80, { message: "numeroMuyLargo" }),
    fechaExpedicion: isoDateOptional,
    fechaCaducidad: isoDateOptional,
    comunidadAutonoma: trimmedString.pipe(z.string().max(80).nullable()),
    estado: z.enum(ESTADOS_LICENCIA),
    notas: trimmedString.pipe(z.string().max(2000).nullable()),
  })
  .superRefine((data, ctx) => {
    if (data.fechaExpedicion && data.fechaCaducidad && data.fechaCaducidad < data.fechaExpedicion) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["fechaCaducidad"],
        message: "fechaCaducidadAntesDeExpedicion",
      });
    }
  });

export type LicenciaInput = z.infer<typeof LicenciaInputSchema>;
