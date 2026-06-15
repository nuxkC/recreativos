import Decimal from "decimal.js";
import { z } from "zod";

import { ESTADOS_MAQUINA } from "./types";

/**
 * Schema único para alta y edición. Lo importan tanto el formulario
 * cliente (validación inmediata con react-hook-form) como las Server
 * Actions (defensa en profundidad antes de tocar la BBDD).
 *
 * Notas de dominio:
 * - `valorCredito` es dinero. Se valida y normaliza con `Decimal` (mín
 *   0,01 €, máx 999,99 €, dos decimales). Se guarda como string para
 *   no perder precisión al cruzar fronteras.
 * - Los contadores son `bigint` ≥ 0 en la BBDD. En este dominio los
 *   valores reales no superan `Number.MAX_SAFE_INTEGER`, así que es
 *   seguro usar `number` en TS.
 */

const trimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v));

const valorCreditoSchema = z
  .string()
  .trim()
  .min(1, { message: "valorCreditoRequerido" })
  .transform((raw, ctx) => {
    // Aceptamos coma o punto como separador decimal: el usuario es
    // español y va a teclear "0,20" tan a menudo como "0.20".
    const normalized = raw.replace(",", ".");
    let dec: Decimal;
    try {
      dec = new Decimal(normalized);
    } catch {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "valorCreditoInvalido" });
      return z.NEVER;
    }
    if (!dec.isFinite()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "valorCreditoInvalido" });
      return z.NEVER;
    }
    if (dec.lessThan("0.01")) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "valorCreditoMinimo" });
      return z.NEVER;
    }
    if (dec.greaterThan("999.99")) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "valorCreditoMaximo" });
      return z.NEVER;
    }
    if (dec.decimalPlaces() > 2) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "valorCreditoDecimales" });
      return z.NEVER;
    }
    return dec.toFixed(2);
  });

const contadorSchema = z.coerce
  .number({ error: "contadorEntero" })
  .int({ message: "contadorEntero" })
  .nonnegative({ message: "contadorNegativo" });

export const MaquinaInputSchema = z.object({
  numeroSerie: z
    .string()
    .trim()
    .min(1, { message: "numeroSerieRequerido" })
    .max(80, { message: "numeroSerieMuyLargo" }),
  modelo: trimmedString.pipe(z.string().max(80).nullable()),
  fabricante: trimmedString.pipe(z.string().max(80).nullable()),
  valorCredito: valorCreditoSchema,
  contadorEntradasInicial: contadorSchema,
  contadorSalidasInicial: contadorSchema,
  estado: z.enum(ESTADOS_MAQUINA),
  notas: trimmedString.pipe(z.string().max(2000).nullable()),
});

export type MaquinaInput = z.infer<typeof MaquinaInputSchema>;
