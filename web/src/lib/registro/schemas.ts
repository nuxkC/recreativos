import { z } from "zod";

import { esEmailValido } from "@/lib/shared/validators";

/**
 * Schema del registro self-service (T-200). Lo comparten el formulario cliente
 * (validación inmediata con react-hook-form) y la Server Action (defensa en
 * profundidad antes de invocar la Edge Function). Los mensajes son claves i18n
 * que el formulario traduce con `registro.validacion.*`.
 */

export const PASSWORD_MIN_LENGTH = 6;

export const RegistroInputSchema = z.object({
  nombreEmpresa: z
    .string()
    .trim()
    .min(1, { message: "nombreEmpresaRequerido" })
    .max(150, { message: "nombreEmpresaMuyLargo" }),
  nombreCompleto: z
    .string()
    .trim()
    .min(1, { message: "nombreCompletoRequerido" })
    .max(150, { message: "nombreCompletoMuyLargo" }),
  email: z
    .string()
    .trim()
    .min(1, { message: "emailRequerido" })
    .refine((v) => esEmailValido(v), { message: "emailInvalido" }),
  password: z
    .string()
    .min(1, { message: "passwordRequerida" })
    .min(PASSWORD_MIN_LENGTH, { message: "passwordMin" }),
});

export type RegistroInput = z.infer<typeof RegistroInputSchema>;
