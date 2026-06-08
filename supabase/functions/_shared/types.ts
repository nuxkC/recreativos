/**
 * Tipos de dominio compartidos entre Edge Functions.
 *
 * Convención: los tipos que reflejan filas de la BBDD se nombran igual que la
 * tabla en PascalCase. Los tipos de input/output de funciones llevan sufijo
 * `Input` / `Result`.
 */

export type Rol = "owner" | "admin" | "gestor" | "tecnico" | "contable";

export type BaselineOrigen = "recaudacion_anterior" | "cambio_placa" | "instalacion_base";

export type EstadoRecaudacion = "firme" | "anulada";

export type ResolucionConflicto = "aceptada" | "sustituida" | "anulada";

/** Devuelto por la función SQL `obtener_baseline`. */
export interface BaselineInfo {
  entradas: number;
  salidas: number;
  fecha_referencia: string; // ISO
  origen: BaselineOrigen;
  referencia_id: string;
}

/** Forma raw que devuelve PostgREST al llamar a `obtener_baseline` por RPC. */
export interface BaselineRpcRow {
  entradas: number | string;
  salidas: number | string;
  fecha_referencia: string;
  origen: BaselineOrigen;
  referencia_id: string;
}

/** Item dentro de un desglose de denominaciones. */
export interface DenominacionItem {
  denominacion: number;
  cantidad: number;
}

/** Resultado del cálculo de recaudación (formato API). */
export interface CalculoRecaudacionResult {
  procede: boolean;
  bruto: string; // serializado como string para precisión
  semanas: number;
  tasa_semanal: string;
  tasa_total: string;
  neto: string;
  porcentaje_local: string;
  parte_local: string;
  parte_empresa: string;
  valor_credito: string;
  baseline: BaselineInfo;
}

/** Datos mínimos de una empresa que las Edge Functions necesitan al operar. */
export interface EmpresaContext {
  id: string;
  nombre: string;
  zona_horaria: string;
  cif: string | null;
  ticket_cabecera: string | null;
  ticket_pie: string | null;
  logo_url: string | null;
}

/** Datos de instalación + máquina + local + licencia, agregados. */
export interface InstalacionContext {
  id: string;
  empresa_id: string;
  maquina_id: string;
  licencia_id: string;
  local_id: string;
  fecha_inicio: string;
  tasa_semanal: string;
  porcentaje_local: string;
  contador_entradas_base: number;
  contador_salidas_base: number;
  estado: "activa" | "cerrada";
  maquina: {
    numero_serie: string;
    modelo: string | null;
    valor_credito: string;
  };
  local: {
    nombre: string;
    direccion: string | null;
    titular_nombre: string | null;
  };
  licencia: {
    numero: string;
  };
}
