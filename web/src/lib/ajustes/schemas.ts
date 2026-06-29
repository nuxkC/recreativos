import { z } from "zod";

import { cifNifSchema, emailOptional, telefonoSchema, trimmedString } from "@/lib/shared/validators";
import { REDONDEO_RECAUDACION_OPCIONES, ZONAS_HORARIAS } from "./types";

export const EmpresaAjustesSchema = z.object({
  nombre: z
    .string()
    .trim()
    .min(1, { message: "nombreRequerido" })
    .max(150, { message: "nombreMuyLargo" }),
  cif: cifNifSchema("cifMuyLargo"),
  direccion: trimmedString.pipe(z.string().max(300, { message: "direccionMuyLarga" }).nullable()),
  telefono: telefonoSchema("telefonoMuyLargo"),
  email: emailOptional,
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
