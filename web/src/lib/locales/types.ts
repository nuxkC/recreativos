/**
 * Tipos de la feature `locales`.
 *
 * Espejo de `public.local` en
 * `supabase/migrations/20260519220100_create_inventory_tables.sql`.
 *
 * Mientras no esté en su sitio el pipeline de `supabase gen types
 * typescript`, los tipos de fila viven aquí — declarados a mano y
 * con un test manual en cada migración futura que los toque.
 *
 * Nota: a diferencia de `licencia`, la tabla `local` NO tiene columna
 * `estado` ni `UNIQUE` sobre `nombre`. Por eso la UI de locales no
 * incluye badge ni filtro por estado y no se trata el código de error
 * 23505.
 */

/** Forma normalizada que consume la UI. */
export interface Local {
  id: string;
  empresaId: string;
  nombre: string;
  direccion: string | null;
  /** Dirección estructurada (T-277). CCAA = lista de oro; códigos = INE. */
  comunidadAutonoma: string | null;
  provinciaCodigo: string | null;
  municipioCodigo: string | null;
  calle: string | null;
  codigoPostal: string | null;
  cifONif: string | null;
  titularNombre: string | null;
  telefono: string | null;
  email: string | null;
  notas: string | null;
  /** Override del % de recuperación de deuda. NULL = hereda el de la empresa. */
  porcentajeRecuperacion: number | null;
  /** Calendario de recaudación (Planificación P1). cadencia + fecha van juntas. */
  cadenciaSemanas: number | null;
  fechaInicioRecaudacion: string | null;
  /** Operario asignado (usuario). NULL = sin asignar. */
  operarioId: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Forma cruda devuelta por Supabase. */
export interface LocalRow {
  id: string;
  empresa_id: string;
  nombre: string;
  direccion: string | null;
  comunidad_autonoma: string | null;
  provincia_codigo: string | null;
  municipio_codigo: string | null;
  calle: string | null;
  codigo_postal: string | null;
  cif_o_nif: string | null;
  titular_nombre: string | null;
  telefono: string | null;
  email: string | null;
  notas: string | null;
  porcentaje_recuperacion: number | null;
  cadencia_semanas: number | null;
  fecha_inicio_recaudacion: string | null;
  operario_id: string | null;
  created_at: string;
  updated_at: string;
}

export function mapLocalRow(row: LocalRow): Local {
  return {
    id: row.id,
    empresaId: row.empresa_id,
    nombre: row.nombre,
    direccion: row.direccion,
    comunidadAutonoma: row.comunidad_autonoma,
    provinciaCodigo: row.provincia_codigo,
    municipioCodigo: row.municipio_codigo,
    calle: row.calle,
    codigoPostal: row.codigo_postal,
    cifONif: row.cif_o_nif,
    titularNombre: row.titular_nombre,
    telefono: row.telefono,
    email: row.email,
    notas: row.notas,
    porcentajeRecuperacion: row.porcentaje_recuperacion,
    cadenciaSemanas: row.cadencia_semanas,
    fechaInicioRecaudacion: row.fecha_inicio_recaudacion,
    operarioId: row.operario_id,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}
