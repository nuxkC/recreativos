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

// -----------------------------------------------------------------------------
// Calendario de recaudación del local + operario (Planificación P1).
// -----------------------------------------------------------------------------

const isoDateRegex = /^\d{4}-\d{2}-\d{2}$/;

/** Cadencia en semanas (>0). Vacío = null (sin planificar). */
const cadenciaSemanasNullable = z.preprocess(
  (v) => (v === "" || v == null ? null : v),
  z.coerce
    .number({ message: "cadenciaInvalida" })
    .int({ message: "cadenciaInvalida" })
    .positive({ message: "cadenciaInvalida" })
    .nullable(),
);

/** Fecha de inicio del calendario (ISO YYYY-MM-DD). Vacío = null. */
const isoDateNullable = z.preprocess(
  (v) => (typeof v === "string" && v.trim().length === 0 ? null : v),
  z.string().trim().regex(isoDateRegex, { message: "fechaInvalida" }).nullable(),
);

/** UUID opcional (vacío = null). `.guid()` porque zod 4 `.uuid()` rechaza ids de seed (regresión T-255). */
const uuidNullable = z.preprocess(
  (v) => (v === "" || v == null ? null : v),
  z.string().guid({ message: "uuidInvalido" }).nullable(),
);

/**
 * Calendario de recaudación de un local + operario asignado. Lo usan el form de
 * la ficha de local y la Server Action `actualizarCalendarioLocal`. Coherencia:
 * cadencia y fecha van juntas (ambas o ninguna); el operario es independiente.
 */
export const CalendarioLocalInputSchema = z
  .object({
    localId: z.string().guid({ message: "uuidInvalido" }),
    cadenciaSemanas: cadenciaSemanasNullable,
    fechaInicioRecaudacion: isoDateNullable,
    operarioId: uuidNullable,
  })
  .refine((d) => (d.cadenciaSemanas == null) === (d.fechaInicioRecaudacion == null), {
    message: "calendarioIncompleto",
    path: ["cadenciaSemanas"],
  });

export type CalendarioLocalInput = z.infer<typeof CalendarioLocalInputSchema>;
