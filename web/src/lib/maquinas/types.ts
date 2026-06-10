/**
 * Tipos de la feature `maquinas`.
 *
 * Espejo de `public.maquina` en
 * `supabase/migrations/20260519220100_create_inventory_tables.sql`.
 *
 * Hasta que tengamos `supabase gen types typescript` integrado al
 * pipeline, los tipos de fila viven aquí — declarados a mano y con un
 * test manual cada vez que toquemos la migración.
 *
 * Nota sobre dinero: `valorCredito` se modela como `string` para
 * preservar la precisión exacta del `numeric(4, 2)` de la BBDD. Quien
 * lo opere debe envolverlo en `Decimal` antes de calcular.
 */

export const ESTADOS_MAQUINA = ["almacen", "instalada", "averiada", "baja"] as const;

export type EstadoMaquina = (typeof ESTADOS_MAQUINA)[number];

export function isEstadoMaquina(value: string): value is EstadoMaquina {
  return (ESTADOS_MAQUINA as readonly string[]).includes(value);
}

/** Forma normalizada que consume la UI. */
export interface Maquina {
  id: string;
  empresaId: string;
  numeroSerie: string;
  modelo: string | null;
  fabricante: string | null;
  /** Importe en euros como string para no perder precisión. */
  valorCredito: string;
  contadorEntradasInicial: number;
  contadorSalidasInicial: number;
  estado: EstadoMaquina;
  notas: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Forma cruda devuelta por Supabase. */
export interface MaquinaRow {
  id: string;
  empresa_id: string;
  numero_serie: string;
  modelo: string | null;
  fabricante: string | null;
  // numeric viene serializado como string desde PostgREST.
  valor_credito: string;
  // bigint viene serializado como string; lo normalizamos a number en
  // mapMaquinaRow porque en este dominio no llegamos jamás a
  // Number.MAX_SAFE_INTEGER (los contadores reales son del orden de
  // millones).
  contador_entradas_inicial: number | string;
  contador_salidas_inicial: number | string;
  estado: string;
  notas: string | null;
  created_at: string;
  updated_at: string;
}

function toNumeroEntero(value: number | string): number {
  if (typeof value === "number") return value;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function mapMaquinaRow(row: MaquinaRow): Maquina {
  return {
    id: row.id,
    empresaId: row.empresa_id,
    numeroSerie: row.numero_serie,
    modelo: row.modelo,
    fabricante: row.fabricante,
    valorCredito: row.valor_credito,
    contadorEntradasInicial: toNumeroEntero(row.contador_entradas_inicial),
    contadorSalidasInicial: toNumeroEntero(row.contador_salidas_inicial),
    estado: isEstadoMaquina(row.estado) ? row.estado : "almacen",
    notas: row.notas,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}
