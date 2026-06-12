import Decimal from "decimal.js";
import { z } from "zod";

import { ESTADOS_INSTALACION } from "./types";

/**
 * Schema único para alta y edición. Lo importan tanto el formulario
 * cliente (validación inmediata con react-hook-form) como las Server
 * Actions (defensa en profundidad antes de tocar la BBDD).
 *
 * Las columnas `numeric` viajan como string en la API. El schema acepta
 * tanto coma como punto decimal y emite strings normalizados con punto.
 */

const trimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v));

const isoDateRegex = /^\d{4}-\d{2}-\d{2}$/;

const isoDateRequired = z.string().trim().regex(isoDateRegex, { message: "fechaInvalida" });

const Uuid = z.string().uuid({ message: "uuidInvalido" });

/**
 * Decimal positivo o cero que cabe en numeric(8,2). Se usa para
 * `tasa_semanal`. Lo emite como string `"X.YY"` con dos decimales.
 */
const decimalNoNegativo = z
  .string()
  .trim()
  .min(1, { message: "tasaRequerida" })
  .transform((v) => v.replace(",", "."))
  .pipe(z.string().refine((v) => /^\d+(\.\d{1,2})?$/.test(v), { message: "tasaInvalida" }))
  .transform((v) => new Decimal(v))
  .refine((d) => d.gte(0), { message: "tasaInvalida" })
  .refine((d) => d.lte(999999.99), { message: "tasaFueraDeRango" })
  .transform((d) => d.toFixed(2));

/**
 * Decimal entre 0 y 100 con hasta 2 decimales. Se usa para
 * `porcentaje_local`. Lo emite como string `"X.YY"`.
 */
const decimalPorcentaje = z
  .string()
  .trim()
  .min(1, { message: "porcentajeRequerido" })
  .transform((v) => v.replace(",", "."))
  .pipe(z.string().refine((v) => /^\d+(\.\d{1,2})?$/.test(v), { message: "porcentajeInvalido" }))
  .transform((v) => new Decimal(v))
  .refine((d) => d.gte(0) && d.lte(100), { message: "porcentajeFueraDeRango" })
  .transform((d) => d.toFixed(2));

/**
 * Tolva (dinero físico dejado en la máquina al instalarla). Opcional: vacío =
 * 0. Solo se usa en el alta (`crear_instalacion`); la deuda del local por la
 * tolva = porcentaje_local × tolva la crea el servidor. Cabe en numeric(10,2).
 */
const decimalTolvaOpcional = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? "0" : v.replace(",", ".")))
  .pipe(z.string().refine((v) => /^\d+(\.\d{1,2})?$/.test(v), { message: "tolvaInvalida" }))
  .transform((v) => new Decimal(v))
  .refine((d) => d.gte(0), { message: "tolvaInvalida" })
  .refine((d) => d.lte(99999999.99), { message: "tolvaFueraDeRango" })
  .transform((d) => d.toFixed(2));

export const InstalacionInputSchema = z.object({
  maquinaId: Uuid,
  licenciaId: Uuid,
  localId: Uuid,
  fechaInicio: isoDateRequired,
  tasaSemanal: decimalNoNegativo,
  porcentajeLocal: decimalPorcentaje,
  tolva: decimalTolvaOpcional,
  estado: z.enum(ESTADOS_INSTALACION).default("activa"),
  notas: trimmedString.pipe(z.string().max(2000, { message: "notasMuyLargas" }).nullable()),
});

export type InstalacionInput = z.infer<typeof InstalacionInputSchema>;

/** Schema para "cerrar instalación" (lo invoca el form de cierre). */
export const CerrarInstalacionInputSchema = z.object({
  fechaFin: isoDateRequired,
  notas: trimmedString.pipe(z.string().max(2000, { message: "notasMuyLargas" }).nullable()),
});

export type CerrarInstalacionInput = z.infer<typeof CerrarInstalacionInputSchema>;
