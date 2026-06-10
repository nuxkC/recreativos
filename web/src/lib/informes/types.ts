/**
 * Tipos de la feature `informes`.
 *
 * Los informes SOLO leen agregados ya calculados por Postgres a través de
 * las vistas `v_recaudaciones_por_local_mes` y `v_recaudaciones_por_maquina_mes`
 * (ver `supabase/migrations/20260519230600_create_views.sql`). La web nunca
 * recalcula recaudación: consume las cifras tal y como las suma el servidor.
 *
 * Las sumas `numeric` de Postgres llegan como string; aquí se normalizan a
 * `number` exclusivamente para representarlas en las gráficas. El formateo a
 * euros se centraliza en `format.ts`.
 */

/** Un punto de la serie temporal mensual (totales de la empresa en ese mes). */
export interface PuntoEvolucionMes {
  /** Primer día del mes en formato ISO `YYYY-MM-01`. */
  mes: string;
  brutoTotal: number;
  netoTotal: number;
  parteLocalTotal: number;
  parteEmpresaTotal: number;
  numRecaudaciones: number;
}

/** Agregado del periodo para un local. */
export interface AgregadoPorLocal {
  localId: string;
  localNombre: string;
  brutoTotal: number;
  netoTotal: number;
  parteEmpresaTotal: number;
  numRecaudaciones: number;
}

/** Agregado del periodo para una máquina. */
export interface AgregadoPorMaquina {
  maquinaId: string;
  maquinaEtiqueta: string;
  brutoTotal: number;
  netoTotal: number;
  parteEmpresaTotal: number;
  numRecaudaciones: number;
}

/** Totales del periodo seleccionado (cabecera de KPIs). */
export interface ResumenInformes {
  brutoTotal: number;
  netoTotal: number;
  parteEmpresaTotal: number;
  numRecaudaciones: number;
}

/** Payload completo que consume la pantalla de informes. */
export interface InformesData {
  resumen: ResumenInformes;
  evolucionMensual: PuntoEvolucionMes[];
  porLocal: AgregadoPorLocal[];
  porMaquina: AgregadoPorMaquina[];
}

/** Filtros aplicables a los informes. */
export interface InformesFiltros {
  localId?: string | null;
  /** Inclusive, formato `YYYY-MM-DD`. Se compara contra el mes. */
  desde?: string | null;
  /** Inclusive, formato `YYYY-MM-DD`. Se compara contra el mes. */
  hasta?: string | null;
}

/** Local mínimo para poblar el selector de filtro. */
export interface LocalOpcion {
  id: string;
  nombre: string;
}
