/**
 * Tipos para la pantalla de Ajustes (tabla `empresa`). El logo (campo
 * `logo_url`) NO se edita desde la web en T-40 porque el bucket
 * `logos/` está restringido a `service_role`. La subida llegará cuando
 * se cree una Edge Function dedicada (out of scope para fase 1).
 */

export interface EmpresaAjustes {
  id: string;
  nombre: string;
  cif: string | null;
  direccion: string | null;
  telefono: string | null;
  email: string | null;
  logoUrl: string | null;
  zonaHoraria: string;
  ticketCabecera: string | null;
  ticketPie: string | null;
  redondeoRecaudacion: number;
  porcentajeRecuperacion: number;
  createdAt: string;
  updatedAt: string;
}

export interface EmpresaAjustesRow {
  id: string;
  nombre: string;
  cif: string | null;
  direccion: string | null;
  telefono: string | null;
  email: string | null;
  logo_url: string | null;
  zona_horaria: string;
  ticket_cabecera: string | null;
  ticket_pie: string | null;
  redondeo_recaudacion: number;
  porcentaje_recuperacion: number;
  created_at: string;
  updated_at: string;
}

export function mapEmpresaAjustesRow(row: EmpresaAjustesRow): EmpresaAjustes {
  return {
    id: row.id,
    nombre: row.nombre,
    cif: row.cif,
    direccion: row.direccion,
    telefono: row.telefono,
    email: row.email,
    logoUrl: row.logo_url,
    zonaHoraria: row.zona_horaria,
    ticketCabecera: row.ticket_cabecera,
    ticketPie: row.ticket_pie,
    redondeoRecaudacion: row.redondeo_recaudacion,
    porcentajeRecuperacion: row.porcentaje_recuperacion,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

/**
 * Opciones de redondeo del bruto de recaudación (unidad en €). 0 = desactivado.
 * El ajuste lo aplica el servidor sobre el contador de salidas (ver SSOT
 * `_shared/calculo.ts`); aquí solo se elige la unidad.
 */
export const REDONDEO_RECAUDACION_OPCIONES = [0, 10] as const;

/**
 * Lista de zonas horarias razonables para España y latinoamérica. Si
 * hace falta más adelante migramos a un select autocompletable con todas
 * las IANA.
 */
export const ZONAS_HORARIAS = [
  "Europe/Madrid",
  "Atlantic/Canary",
  "Europe/Lisbon",
  "Europe/London",
  "America/Mexico_City",
  "America/Bogota",
  "America/Argentina/Buenos_Aires",
] as const;

export type ZonaHoraria = (typeof ZONAS_HORARIAS)[number];
