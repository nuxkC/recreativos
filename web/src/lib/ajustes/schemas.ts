import { z } from "zod";

import { REDONDEO_RECAUDACION_OPCIONES, ZONAS_HORARIAS } from "./types";

const trimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v));

const optionalEmail = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v))
  .pipe(z.string().email({ message: "emailInvalido" }).nullable());

export const EmpresaAjustesSchema = z.object({
  nombre: z
    .string()
    .trim()
    .min(1, { message: "nombreRequerido" })
    .max(150, { message: "nombreMuyLargo" }),
  cif: trimmedString.pipe(z.string().max(20, { message: "cifMuyLargo" }).nullable()),
  direccion: trimmedString.pipe(z.string().max(300, { message: "direccionMuyLarga" }).nullable()),
  telefono: trimmedString.pipe(z.string().max(30, { message: "telefonoMuyLargo" }).nullable()),
  email: optionalEmail,
  zonaHoraria: z.enum(ZONAS_HORARIAS, { message: "zonaHorariaInvalida" }),
  ticketCabecera: trimmedString.pipe(z.string().max(500, { message: "ticketMuyLargo" }).nullable()),
  ticketPie: trimmedString.pipe(z.string().max(500, { message: "ticketMuyLargo" }).nullable()),
  redondeoRecaudacion: z.coerce
    .number()
    .int()
    .refine((v) => (REDONDEO_RECAUDACION_OPCIONES as readonly number[]).includes(v), {
      message: "redondeoInvalido",
    }),
  // % por defecto de la parte_local que se retiene en cada recaudación para
  // amortizar deudas del local (tolva/préstamo). 0 = sin recuperación automática.
  // Un local puede sobreescribirlo (override) desde su ficha.
  porcentajeRecuperacion: z.coerce
    .number({ message: "porcentajeRecuperacionInvalido" })
    .int({ message: "porcentajeRecuperacionInvalido" })
    .min(0, { message: "porcentajeRecuperacionRango" })
    .max(100, { message: "porcentajeRecuperacionRango" }),
});

export type EmpresaAjustesInput = z.infer<typeof EmpresaAjustesSchema>;
