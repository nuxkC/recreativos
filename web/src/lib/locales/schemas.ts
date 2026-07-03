import { z } from "zod";

import {
  cifNifSchema,
  emailOptional,
  telefonoSchema,
  trimmedString,
} from "@/lib/shared/validators";

/**
 * Schema único para alta y edición. Lo importan tanto el formulario
 * cliente (validación inmediata con react-hook-form) como las Server
 * Actions (defensa en profundidad antes de tocar la BBDD).
 */

export const LocalInputSchema = z.object({
  nombre: z
    .string()
    .trim()
    .min(1, { message: "nombreRequerido" })
    .max(200, { message: "nombreMuyLargo" }),
  // Dirección estructurada (T-277). CCAA/provincia/municipio vienen de selectores
  // cerrados (validados por el CHECK/FK de BBDD); calle y CP son texto libre.
  comunidadAutonoma: trimmedString.pipe(z.string().max(80).nullable()),
  provinciaCodigo: trimmedString.pipe(z.string().nullable()),
  municipioCodigo: trimmedString.pipe(z.string().nullable()),
  calle: trimmedString.pipe(z.string().max(300, { message: "calleMuyLarga" }).nullable()),
  codigoPostal: trimmedString.pipe(
    z.string().regex(/^\d{5}$/, { message: "codigoPostalInvalido" }).nullable(),
  ),
  cifONif: cifNifSchema("cifONifMuyLargo"),
  titularNombre: trimmedString.pipe(
    z.string().max(150, { message: "titularNombreMuyLargo" }).nullable(),
  ),
  telefono: telefonoSchema("telefonoMuyLargo"),
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
