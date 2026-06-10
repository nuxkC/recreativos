import { z } from "zod";

/**
 * Schema único para alta y edición. Lo importan tanto el formulario
 * cliente (validación inmediata con react-hook-form) como las Server
 * Actions (defensa en profundidad antes de tocar la BBDD).
 */

const trimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v));

/**
 * Email opcional. Si el usuario lo deja vacío se normaliza a null;
 * si lo proporciona, validamos formato. Mensaje en clave i18n.
 */
const emailOptional = z
  .string()
  .trim()
  .transform((v) => (v.length === 0 ? null : v))
  .pipe(z.string().email({ message: "emailInvalido" }).nullable());

export const LocalInputSchema = z.object({
  nombre: z
    .string()
    .trim()
    .min(1, { message: "nombreRequerido" })
    .max(200, { message: "nombreMuyLargo" }),
  direccion: trimmedString.pipe(z.string().max(300, { message: "direccionMuyLarga" }).nullable()),
  cifONif: trimmedString.pipe(z.string().max(20, { message: "cifONifMuyLargo" }).nullable()),
  titularNombre: trimmedString.pipe(
    z.string().max(150, { message: "titularNombreMuyLargo" }).nullable(),
  ),
  telefono: trimmedString.pipe(z.string().max(30, { message: "telefonoMuyLargo" }).nullable()),
  email: emailOptional,
  notas: trimmedString.pipe(z.string().max(2000, { message: "notasMuyLargas" }).nullable()),
});

export type LocalInput = z.infer<typeof LocalInputSchema>;
