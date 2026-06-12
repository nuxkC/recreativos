/**
 * Tipos de la feature `averias` (T-220 backend, T-221 web).
 *
 * Espejo de `public.averia` y `public.averia_recambio` en
 * `supabase/migrations/20260612150000_averias_modelo.sql`.
 *
 * El historial sigue a la MÁQUINA: cada avería guarda un snapshot de
 * `instalacion_id`/`local_id` del momento (puede ser null si la máquina
 * estaba en almacén). Nada económico vive aquí; la tolva llega en T-223.
 */

export const CATEGORIAS_AVERIA = [
  "atasco_billete",
  "atasco_moneda",
  "error",
  "falta_pago",
  "no_enciende",
  "otro",
] as const;

export type CategoriaAveria = (typeof CATEGORIAS_AVERIA)[number];

export function isCategoriaAveria(value: string): value is CategoriaAveria {
  return (CATEGORIAS_AVERIA as readonly string[]).includes(value);
}

export const ESTADOS_AVERIA = ["abierta", "en_reparacion", "resuelta"] as const;

export type EstadoAveria = (typeof ESTADOS_AVERIA)[number];

export function isEstadoAveria(value: string): value is EstadoAveria {
  return (ESTADOS_AVERIA as readonly string[]).includes(value);
}

/** Un recambio sustituido al reparar una avería. */
export interface Recambio {
  id: string;
  averiaId: string;
  pieza: string;
  cantidad: number;
  /** Coste informativo en euros como string (numeric); null si no se anotó. */
  coste: string | null;
  notas: string | null;
  createdAt: string;
}

/** Forma normalizada que consume la UI. */
export interface Averia {
  id: string;
  empresaId: string;
  maquinaId: string;
  instalacionId: string | null;
  localId: string | null;
  categoria: CategoriaAveria;
  descripcion: string | null;
  estado: EstadoAveria;
  poneMaquinaFueraServicio: boolean;
  reportadaPor: string | null;
  resueltaPor: string | null;
  fechaReporte: string;
  fechaResolucion: string | null;
  notas: string | null;
  createdAt: string;
  updatedAt: string;
  /** Nombre del local snapshot (embebido vía FK); null si ocurrió en almacén. */
  localNombre: string | null;
  recambios: Recambio[];
}

/** Forma cruda de `averia_recambio` devuelta por PostgREST. */
export interface RecambioRow {
  id: string;
  averia_id: string;
  pieza: string;
  cantidad: number;
  coste: string | null;
  notas: string | null;
  created_at: string;
}

/** Forma cruda de `averia` (con recambios embebidos vía PostgREST). */
export interface AveriaRow {
  id: string;
  empresa_id: string;
  maquina_id: string;
  instalacion_id: string | null;
  local_id: string | null;
  categoria: string;
  descripcion: string | null;
  estado: string;
  pone_maquina_fuera_servicio: boolean;
  reportada_por: string | null;
  resuelta_por: string | null;
  fecha_reporte: string;
  fecha_resolucion: string | null;
  notas: string | null;
  created_at: string;
  updated_at: string;
  local: { nombre: string } | null;
  recambios: RecambioRow[] | null;
}

function mapRecambioRow(row: RecambioRow): Recambio {
  return {
    id: row.id,
    averiaId: row.averia_id,
    pieza: row.pieza,
    cantidad: row.cantidad,
    coste: row.coste,
    notas: row.notas,
    createdAt: row.created_at,
  };
}

export function mapAveriaRow(row: AveriaRow): Averia {
  return {
    id: row.id,
    empresaId: row.empresa_id,
    maquinaId: row.maquina_id,
    instalacionId: row.instalacion_id,
    localId: row.local_id,
    categoria: isCategoriaAveria(row.categoria) ? row.categoria : "otro",
    descripcion: row.descripcion,
    estado: isEstadoAveria(row.estado) ? row.estado : "abierta",
    poneMaquinaFueraServicio: row.pone_maquina_fuera_servicio,
    reportadaPor: row.reportada_por,
    resueltaPor: row.resuelta_por,
    fechaReporte: row.fecha_reporte,
    fechaResolucion: row.fecha_resolucion,
    notas: row.notas,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    localNombre: row.local?.nombre ?? null,
    recambios: (row.recambios ?? [])
      .map(mapRecambioRow)
      .sort((a, b) => (a.createdAt < b.createdAt ? -1 : a.createdAt > b.createdAt ? 1 : 0)),
  };
}

/** True si la avería sigue abierta (cualquier estado distinto de resuelta). */
export function averiaAbierta(averia: Pick<Averia, "estado">): boolean {
  return averia.estado !== "resuelta";
}
