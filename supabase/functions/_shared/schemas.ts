/**
 * Schemas Zod compartidos entre Edge Functions.
 *
 * SSOT: estos mismos schemas se importan (o replican literalmente) en el
 * cliente web cuando convenga validar antes de enviar al servidor.
 */

import { z } from "zod";

import { DENOMINACIONES_PERMITIDAS } from "./constants.ts";

const denominacionesAllowed = DENOMINACIONES_PERMITIDAS as readonly number[];

/** Item de denominación: {denominacion, cantidad}. */
export const DenominacionItemSchema = z.object({
  denominacion: z
    .number()
    .refine((v) => denominacionesAllowed.includes(v), {
      message: `Denominación no permitida. Válidas: ${denominacionesAllowed.join(", ")}`,
    }),
  cantidad: z.number().int().nonnegative(),
});

/** Desglose: array de items. */
export const DesgloseSchema = z.array(DenominacionItemSchema);

const Uuid = z.string().uuid();
const IsoDate = z.string().datetime({ offset: true });

/** Input de `calcular-recaudacion`. */
export const CalcularRecaudacionInputSchema = z.object({
  instalacion_id: Uuid,
  contador_entradas_actual: z.number().int().nonnegative(),
  contador_salidas_actual: z.number().int().nonnegative(),
  fecha: IsoDate.optional(),
});
export type CalcularRecaudacionInput = z.infer<typeof CalcularRecaudacionInputSchema>;

/** Input de `crear-recaudacion`. */
export const CrearRecaudacionInputSchema = z.object({
  instalacion_id: Uuid,
  fecha: IsoDate,

  contador_entradas_actual: z.number().int().nonnegative(),
  contador_salidas_actual: z.number().int().nonnegative(),

  desglose_total: DesgloseSchema,
  desglose_local: DesgloseSchema,

  // Evidencia (firma obligatoria, fotos opcionales).
  firma_base64: z.string().min(1, "Falta la firma del titular"),
  foto_entradas_base64: z.string().optional(),
  foto_salidas_base64: z.string().optional(),
  ocr_entradas_valor: z.number().int().nullable().optional(),
  ocr_salidas_valor: z.number().int().nullable().optional(),

  observaciones: z.string().optional(),

  // Trazabilidad de sync (lo que vio el cliente al iniciar la recaudación).
  dispositivo_id: z.string().optional(),
  idempotency_key: Uuid,
  baseline_origen: z.enum(["recaudacion_anterior", "cambio_placa", "instalacion_base"]),
  baseline_id: Uuid.nullable(),
  baseline_entradas: z.number().int().nonnegative(),
  baseline_salidas: z.number().int().nonnegative(),
});
export type CrearRecaudacionInput = z.infer<typeof CrearRecaudacionInputSchema>;

/** Input de `crear-cambio-placa`. */
export const CrearCambioPlacaInputSchema = z.object({
  instalacion_id: Uuid,
  fecha: IsoDate,
  contador_entradas_nuevo: z.number().int().nonnegative().default(0),
  contador_salidas_nuevo: z.number().int().nonnegative().default(0),
  motivo: z.string().optional(),
  numero_serie_placa_anterior: z.string().optional(),
  numero_serie_placa_nueva: z.string().optional(),
  foto_base64: z.string().optional(),
  notas: z.string().optional(),
});
export type CrearCambioPlacaInput = z.infer<typeof CrearCambioPlacaInputSchema>;

// ----------------------------------------------------------------------------- T-23 / T-24 / T-26 / T-27

/** Input de `cerrar-instalacion`. */
export const CerrarInstalacionInputSchema = z.object({
  instalacion_id: Uuid,
  fecha_fin: z.string().date(),
  notas: z.string().optional(),
});
export type CerrarInstalacionInput = z.infer<typeof CerrarInstalacionInputSchema>;

/** Input de `adquirir-lock`. */
export const AdquirirLockInputSchema = z.object({
  instalacion_id: Uuid,
  dispositivo_id: z.string().optional(),
  /** Si TRUE, sobrescribe un lock existente aunque sea de otro técnico. */
  forzar: z.boolean().default(false),
});
export type AdquirirLockInput = z.infer<typeof AdquirirLockInputSchema>;

/** Input de `liberar-lock`. */
export const LiberarLockInputSchema = z.object({
  instalacion_id: Uuid,
});
export type LiberarLockInput = z.infer<typeof LiberarLockInputSchema>;

/** Input de `anular-recaudacion`. */
export const AnularRecaudacionInputSchema = z.object({
  recaudacion_id: Uuid,
  motivo: z.string().min(3, "El motivo debe tener al menos 3 caracteres"),
});
export type AnularRecaudacionInput = z.infer<typeof AnularRecaudacionInputSchema>;

/** Input de `resolver-conflicto`. */
export const ResolverConflictoInputSchema = z.object({
  recaudacion_id: Uuid,
  resolucion: z.enum(["aceptada", "sustituida", "anulada"]),
  notas: z.string().optional(),
});
export type ResolverConflictoInput = z.infer<typeof ResolverConflictoInputSchema>;

/**
 * Input de `registrar-empresa` (T-200).
 *
 * Registro self-service abierto: cualquiera puede crear una cuenta y una
 * empresa nueva en periodo de prueba, quedando como `owner`.
 *
 * `email`/`password` solo son necesarios cuando NO hay sesión previa (alta de
 * un usuario de Auth nuevo). Si la petición llega con un JWT de usuario válido,
 * la empresa se asocia a ese usuario y se ignoran email/password. La Edge
 * Function aplica esa regla server-side (ver `registrar-empresa/index.ts`).
 */
export const RegistrarEmpresaInputSchema = z.object({
  nombre_empresa: z.string().trim().min(1, "El nombre de la empresa es obligatorio").max(150),
  nombre_completo: z.string().trim().min(1, "Tu nombre es obligatorio").max(150),
  email: z.string().email().optional(),
  password: z.string().min(6, "La contraseña debe tener al menos 6 caracteres").max(72).optional(),
});
export type RegistrarEmpresaInput = z.infer<typeof RegistrarEmpresaInputSchema>;

/** Input de `invitar-usuario`. */
export const InvitarUsuarioInputSchema = z.object({
  empresa_id: Uuid,
  email: z.string().email(),
  rol: z.enum(["owner", "admin", "gestor", "tecnico", "contable"]),
  nombre_completo: z.string().min(1).optional(),
});
export type InvitarUsuarioInput = z.infer<typeof InvitarUsuarioInputSchema>;

/** Input de `reimprimir-ticket`. */
export const ReimprimirTicketInputSchema = z.object({
  recaudacion_id: Uuid,
});
export type ReimprimirTicketInput = z.infer<typeof ReimprimirTicketInputSchema>;

/**
 * Input de `generar-boletin-instalacion` (T-203).
 *
 * Genera (o reutiliza) el boletín digital de una instalación y devuelve una
 * signed URL. Idempotente: si el boletín ya existe no se regenera salvo que
 * se pida explícitamente con `forzar=true`.
 */
export const GenerarBoletinInstalacionInputSchema = z.object({
  instalacion_id: Uuid,
  forzar: z.boolean().default(false),
});
export type GenerarBoletinInstalacionInput = z.infer<
  typeof GenerarBoletinInstalacionInputSchema
>;

/**
 * Input de `enviar-email-tecnico` (T-71).
 *
 * Disparada por `resolver-conflicto` (T-26b) tras resolver una
 * recaudación en conflicto, para avisar por email al técnico que la
 * recaudó. La función se invoca con service_role (no requiere JWT del
 * caller) — la autorización efectiva la garantiza la propia función
 * `resolver-conflicto`, que ya validó rol antes de invocar.
 */
export const EnviarEmailTecnicoInputSchema = z.object({
  recaudacion_id: Uuid,
});
export type EnviarEmailTecnicoInput = z.infer<typeof EnviarEmailTecnicoInputSchema>;

/**
 * Input de `resumen-mensual` (T-102).
 *
 * Disparada por un cron mensual (pg_cron + pg_net) con body vacío `{}`,
 * en cuyo caso calcula el resumen del MES ANTERIOR. Acepta overrides:
 *   - `mes`: mes objetivo en formato `YYYY-MM` (p. ej. "2026-04").
 *   - `empresa_id`: limita el envío a una sola empresa (resto: todas).
 * La función se invoca con service_role (job de servidor, sin JWT de un
 * usuario concreto).
 */
export const ResumenMensualInputSchema = z.object({
  mes: z
    .string()
    .regex(/^\d{4}-(0[1-9]|1[0-2])$/, "Formato de mes inválido (esperado YYYY-MM)")
    .optional(),
  empresa_id: Uuid.optional(),
});
export type ResumenMensualInput = z.infer<typeof ResumenMensualInputSchema>;

/**
 * Input de `registrar-device-token` (T-101).
 *
 * La app la invoca con el JWT del usuario para registrar/actualizar el
 * token FCM de su dispositivo en la empresa activa. `usuario_id` NO se
 * acepta del cliente: se deriva de `auth.uid()` server-side.
 */
export const RegistrarDeviceTokenInputSchema = z.object({
  empresa_id: Uuid,
  token: z.string().min(1, "Falta el token de registro"),
  plataforma: z.enum(["android", "ios", "web"]).default("android"),
});
export type RegistrarDeviceTokenInput = z.infer<typeof RegistrarDeviceTokenInputSchema>;

/**
 * Input de `enviar-push` (T-101).
 *
 * Disparada por `resolver-conflicto` en modo fire-and-forget para avisar
 * al técnico de la resolución por push (canal principal; el email queda
 * como complemento/fallback). Se invoca con service_role.
 */
export const EnviarPushInputSchema = z.object({
  recaudacion_id: Uuid,
});
export type EnviarPushInput = z.infer<typeof EnviarPushInputSchema>;
