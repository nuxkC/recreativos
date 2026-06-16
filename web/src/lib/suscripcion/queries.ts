import { createClient } from "@/lib/supabase/server";

import type { EstadoSuscripcion } from "./trial";

export interface SuscripcionEmpresa {
  estadoSuscripcion: EstadoSuscripcion;
  trialInicio: string | null;
  trialFin: string | null;
}

interface EmpresaSuscripcionRow {
  estado_suscripcion: string;
  trial_inicio: string | null;
  trial_fin: string | null;
}

const ESTADOS: readonly EstadoSuscripcion[] = ["trial", "activa", "suspendida", "cancelada"];

function esEstado(value: string): value is EstadoSuscripcion {
  return (ESTADOS as readonly string[]).includes(value);
}

/**
 * Lee el estado de suscripción y la ventana de trial de una empresa.
 *
 * RLS garantiza que el usuario solo puede leer empresas a las que pertenece.
 * Devuelve `null` si no hay fila accesible o el estado no es reconocible.
 */
export async function obtenerSuscripcionEmpresa(
  empresaId: string,
): Promise<SuscripcionEmpresa | null> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("empresa")
    .select("estado_suscripcion, trial_inicio, trial_fin")
    .eq("id", empresaId)
    .returns<EmpresaSuscripcionRow[]>()
    .maybeSingle();

  if (error || !data || !esEstado(data.estado_suscripcion)) {
    return null;
  }

  return {
    estadoSuscripcion: data.estado_suscripcion,
    trialInicio: data.trial_inicio,
    trialFin: data.trial_fin,
  };
}
