/**
 * Constantes de dominio compartidas entre Edge Functions.
 * SSOT — no duplicar estos valores en cliente sin importarlos.
 */

/**
 * Denominaciones permitidas por las máquinas recreativas (€).
 *
 * Excepción consciente a la regla "dinero jamás como number": no son cantidades
 * calculadas sino un conjunto CERRADO de 9 valores constantes. Los nueve
 * round-trip exactos a `Decimal` (`new Decimal(0.1).toString() === "0.1"`) y el
 * desglose siempre se suma con `Decimal` (ver `sumarDesglose` en calculo.ts),
 * nunca con aritmética float nativa. Por eso no hay riesgo de error de coma
 * flotante y se mantienen como number para no romper el contrato con web/Android.
 */
export const DENOMINACIONES_PERMITIDAS = [
  0.1,
  0.2,
  0.5,
  1,
  2,
  5,
  10,
  20,
  50,
] as const;

export type DenominacionPermitida = (typeof DENOMINACIONES_PERMITIDAS)[number];

/** Zona horaria por defecto cuando una empresa no la sobreescribe. */
export const ZONA_HORARIA_DEFAULT = "Europe/Madrid";

/** Tiempo de vida de un lock optimista de recaudación. */
export const LOCK_TTL_MINUTES = 30;

/** Antigüedad máxima de la sincronización antes de bloquear nuevas recaudaciones. */
export const SYNC_MAX_AGE_HOURS = 48;

/** Duración del periodo de prueba (trial) de una empresa recién registrada, en días. */
export const TRIAL_DIAS = 14;

/** Roles dentro de una empresa. Coincide con la columna `empresa_usuario.rol`. */
export const ROLES = ["owner", "admin", "gestor", "tecnico", "contable"] as const;
export type Rol = (typeof ROLES)[number];

/** Roles autorizados para operaciones de gestión (CRUD de inventario). */
export const ROLES_GESTION: readonly Rol[] = ["owner", "admin", "gestor"];

/** Roles autorizados para anular recaudaciones. */
export const ROLES_ANULACION: readonly Rol[] = ["owner", "admin"];
