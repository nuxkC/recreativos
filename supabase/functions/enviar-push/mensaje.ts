/**
 * Construcción PURA del contenido de la notificación push de resolución de
 * conflicto (T-101). Sin red ni entorno: testeable en `mensaje.test.ts`.
 */

/** Fila mínima de `recaudacion` necesaria para el cuerpo de la push. */
export interface RecaudacionPushRow {
  id: string;
  empresa_id: string;
  tecnico_id: string;
  estado: string;
  resolucion: string | null;
  instalacion: {
    maquina: { numero_serie: string | null } | null;
    local: { nombre: string | null } | null;
  } | null;
}

export interface CuerpoPush {
  title: string;
  body: string;
  /** Datos para el deep-link en la app (string→string, como exige FCM). */
  data: Record<string, string>;
}

/**
 * Construye título, cuerpo y `data` de la notificación a partir de la
 * recaudación resuelta. El `data.tipo` reutiliza el vocabulario de las
 * alertas in-app (T-64) para que la app reaproveche el enrutado a detalle.
 */
export function construirCuerpoPush(rec: RecaudacionPushRow): CuerpoPush {
  const local = rec.instalacion?.local?.nombre?.trim() || "una recaudación";
  const maquina = rec.instalacion?.maquina?.numero_serie?.trim();

  const title = "Conflicto resuelto";
  const detalleMaquina = maquina ? ` (máquina ${maquina})` : "";
  const body = `Se ha resuelto el conflicto de ${local}${detalleMaquina}: ` +
    `${textoResolucion(rec.resolucion)}.`;

  return {
    title,
    body,
    data: {
      tipo: "recaudacion_conflicto",
      recaudacion_id: rec.id,
      resolucion: rec.resolucion ?? "",
    },
  };
}

/** Texto breve y en español de la resolución para el cuerpo de la push. */
export function textoResolucion(resolucion: string | null): string {
  switch (resolucion) {
    case "aceptada":
      return "se aceptaron tus importes";
    case "sustituida":
      return "se sustituyeron por los importes recalculados";
    case "anulada":
      return "la recaudación fue anulada";
    default:
      return "revisa el detalle";
  }
}
