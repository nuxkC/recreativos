import { z } from "zod";

import { ZONAS_HORARIAS } from "./types";

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
});

export type EmpresaAjustesInput = z.infer<typeof EmpresaAjustesSchema>;
