/**
 * T-200 — Lógica PURA del periodo de prueba (trial) de una empresa.
 *
 * Sin red ni dependencias de framework: calcula los días restantes y clasifica
 * el estado del trial para decidir qué mostrar en el banner del dashboard.
 * Testeable de forma aislada (`trial.test.ts`).
 */

/** Estado de suscripción persistido en `empresa.estado_suscripcion`. */
export type EstadoSuscripcion = "trial" | "activa" | "suspendida" | "cancelada";

/** Clasificación del trial para la UI. */
export type EstadoTrial = "vigente" | "porExpirar" | "expirado";

export interface InfoTrial {
  estado: EstadoTrial;
  /** Días naturales completos que faltan para que expire el trial (>= 0). */
  diasRestantes: number;
}

const MS_POR_DIA = 1000 * 60 * 60 * 24;

/** Umbral por defecto (en días) a partir del cual el trial se considera "por expirar". */
export const UMBRAL_POR_EXPIRAR_DIAS = 3;

/**
 * Calcula los días restantes y el estado del trial a partir de su fecha de fin.
 *
 * - `diasRestantes` se redondea hacia arriba: si quedan 0.2 días, mostramos 1
 *   ("te queda menos de 1 día" se sigue contando como 1 hasta que expira).
 * - `expirado` cuando ya pasó `trialFin` (días restantes 0).
 * - `porExpirar` cuando quedan `<= umbral` días (y aún no ha expirado).
 *
 * @param trialFin Fecha de fin del trial (Date o ISO string).
 * @param ahora Momento de referencia (por defecto `new Date()`).
 * @param umbralPorExpirar Días bajo los cuales el trial pasa a "por expirar".
 */
export function calcularInfoTrial(
  trialFin: Date | string,
  ahora: Date = new Date(),
  umbralPorExpirar: number = UMBRAL_POR_EXPIRAR_DIAS,
): InfoTrial {
  const fin = typeof trialFin === "string" ? new Date(trialFin) : trialFin;
  const restanteMs = fin.getTime() - ahora.getTime();

  if (Number.isNaN(restanteMs) || restanteMs <= 0) {
    return { estado: "expirado", diasRestantes: 0 };
  }

  const diasRestantes = Math.ceil(restanteMs / MS_POR_DIA);
  const estado: EstadoTrial = diasRestantes <= umbralPorExpirar ? "porExpirar" : "vigente";
  return { estado, diasRestantes };
}
