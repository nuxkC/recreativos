import { createClient } from "@/lib/supabase/server";

/**
 * Tolva de una instalación activa: teórica (nivel objetivo), efectiva (derivada
 * del ledger) y pendiente (merma de avería por reponer). Fuente: la vista
 * `v_instalacion_tolva` (security_invoker → RLS limita al tenant).
 */
export interface TolvaInstalacion {
  instalacionId: string;
  maquinaNumeroSerie: string;
  maquinaModelo: string | null;
  /** Importes en string (decimal), nunca number, para no perder precisión. */
  teorica: string;
  efectiva: string;
  pendiente: string;
}

/**
 * Tolva de cada máquina actualmente instalada en el local. Solo instalaciones
 * activas (una máquina en almacén no tiene tolva en curso). El nombre de la
 * máquina se resuelve aparte: `v_instalacion_tolva` no es embebible por PostgREST.
 */
export async function obtenerTolvaInstalaciones(localId: string): Promise<TolvaInstalacion[]> {
  const supabase = await createClient();

  const { data: filas, error } = await supabase
    .from("v_instalacion_tolva")
    .select("instalacion_id, maquina_id, teorica, efectiva, pendiente")
    .eq("local_id", localId)
    .eq("estado", "activa");

  if (error) {
    throw new Error(`No se pudo cargar la tolva del local: ${error.message}`);
  }
  if (!filas || filas.length === 0) {
    return [];
  }

  const maquinaIds = filas.map((f) => f.maquina_id as string);
  const { data: maquinas } = await supabase
    .from("maquina")
    .select("id, numero_serie, modelo")
    .in("id", maquinaIds);

  const porId = new Map((maquinas ?? []).map((m) => [m.id as string, m]));

  return filas.map((f) => {
    const maquina = porId.get(f.maquina_id as string);
    return {
      instalacionId: f.instalacion_id as string,
      maquinaNumeroSerie: (maquina?.numero_serie as string) ?? "—",
      maquinaModelo: (maquina?.modelo as string | null) ?? null,
      teorica: String(f.teorica),
      efectiva: String(f.efectiva),
      pendiente: String(f.pendiente),
    };
  });
}
