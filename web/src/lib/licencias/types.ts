/**
 * Tipos de la feature `licencias`.
 *
 * Espejo de `public.licencia` en
 * `supabase/migrations/20260519220100_create_inventory_tables.sql`.
 *
 * Mientras no esté en su sitio el pipeline de `supabase gen types
 * typescript`, los tipos de fila viven aquí — declarados a mano y
 * con un test manual en cada migración futura que los toque.
 */

export const ESTADOS_LICENCIA = ["activa", "suspendida", "caducada", "baja"] as const;

export type EstadoLicencia = (typeof ESTADOS_LICENCIA)[number];

export function isEstadoLicencia(value: string): value is EstadoLicencia {
  return (ESTADOS_LICENCIA as readonly string[]).includes(value);
}

/** Las 19 comunidades y ciudades autónomas. Lista de oro: debe ser idéntica
 *  (byte a byte) al CHECK de BBDD y a la constante Kotlin de Android. */
export const COMUNIDADES_AUTONOMAS = [
  "Andalucía",
  "Aragón",
  "Asturias",
  "Islas Baleares",
  "Canarias",
  "Cantabria",
  "Castilla-La Mancha",
  "Castilla y León",
  "Cataluña",
  "Comunidad Valenciana",
  "Extremadura",
  "Galicia",
  "Madrid",
  "Murcia",
  "Navarra",
  "País Vasco",
  "La Rioja",
  "Ceuta",
  "Melilla",
] as const;

/** Forma normalizada que consume la UI. */
export interface Licencia {
  id: string;
  empresaId: string;
  numero: string;
  fechaExpedicion: string | null;
  fechaCaducidad: string | null;
  comunidadAutonoma: string | null;
  estado: EstadoLicencia;
  notas: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Forma cruda devuelta por Supabase. */
export interface LicenciaRow {
  id: string;
  empresa_id: string;
  numero: string;
  fecha_expedicion: string | null;
  fecha_caducidad: string | null;
  comunidad_autonoma: string | null;
  estado: string;
  notas: string | null;
  created_at: string;
  updated_at: string;
}

export function mapLicenciaRow(row: LicenciaRow): Licencia {
  return {
    id: row.id,
    empresaId: row.empresa_id,
    numero: row.numero,
    fechaExpedicion: row.fecha_expedicion,
    fechaCaducidad: row.fecha_caducidad,
    comunidadAutonoma: row.comunidad_autonoma,
    estado: isEstadoLicencia(row.estado) ? row.estado : "activa",
    notas: row.notas,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}
