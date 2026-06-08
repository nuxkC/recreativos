/**
 * Tipos de la feature `instalaciones`.
 *
 * Espejo de `public.instalacion` en
 * `supabase/migrations/20260519220100_create_inventory_tables.sql`.
 *
 * Una instalación es la asociación máquina+licencia+local con sus
 * condiciones económicas. Solo puede haber UNA instalación `activa` por
 * máquina y por licencia simultáneamente (índices únicos parciales).
 */

export const ESTADOS_INSTALACION = ["activa", "cerrada"] as const;
export type EstadoInstalacion = (typeof ESTADOS_INSTALACION)[number];

export function isEstadoInstalacion(value: string): value is EstadoInstalacion {
  return (ESTADOS_INSTALACION as readonly string[]).includes(value);
}

/** Forma normalizada que consume la UI (incluye datos de las tablas FK). */
export interface Instalacion {
  id: string;
  empresaId: string;
  maquinaId: string;
  licenciaId: string;
  localId: string;
  fechaInicio: string;
  fechaFin: string | null;
  /** Tasa semanal en € (decimal as string para preservar precisión). */
  tasaSemanal: string;
  /** Porcentaje del neto que se queda el local, 0..100 (decimal as string). */
  porcentajeLocal: string;
  contadorEntradasBase: number;
  contadorSalidasBase: number;
  estado: EstadoInstalacion;
  notas: string | null;
  createdAt: string;
  updatedAt: string;
  /** Datos de los FK (cargados con `select` anidado). */
  licencia: { id: string; numero: string } | null;
  maquina: { id: string; numeroSerie: string; modelo: string | null } | null;
  local: { id: string; nombre: string } | null;
}

/** Forma cruda devuelta por Supabase con joins anidados. */
export interface InstalacionRow {
  id: string;
  empresa_id: string;
  maquina_id: string;
  licencia_id: string;
  local_id: string;
  fecha_inicio: string;
  fecha_fin: string | null;
  tasa_semanal: string;
  porcentaje_local: string;
  contador_entradas_base: number;
  contador_salidas_base: number;
  estado: string;
  notas: string | null;
  created_at: string;
  updated_at: string;
  licencia: { id: string; numero: string } | null;
  maquina: { id: string; numero_serie: string; modelo: string | null } | null;
  local: { id: string; nombre: string } | null;
}

export function mapInstalacionRow(row: InstalacionRow): Instalacion {
  return {
    id: row.id,
    empresaId: row.empresa_id,
    maquinaId: row.maquina_id,
    licenciaId: row.licencia_id,
    localId: row.local_id,
    fechaInicio: row.fecha_inicio,
    fechaFin: row.fecha_fin,
    tasaSemanal: row.tasa_semanal,
    porcentajeLocal: row.porcentaje_local,
    contadorEntradasBase: Number(row.contador_entradas_base),
    contadorSalidasBase: Number(row.contador_salidas_base),
    estado: isEstadoInstalacion(row.estado) ? row.estado : "activa",
    notas: row.notas,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    licencia: row.licencia,
    maquina: row.maquina
      ? {
          id: row.maquina.id,
          numeroSerie: row.maquina.numero_serie,
          modelo: row.maquina.modelo,
        }
      : null,
    local: row.local,
  };
}

/** Resúmenes ligeros para los selects del formulario. */
export interface LicenciaResumen {
  id: string;
  numero: string;
}

export interface MaquinaResumen {
  id: string;
  numeroSerie: string;
  modelo: string | null;
}

export interface LocalResumen {
  id: string;
  nombre: string;
}
