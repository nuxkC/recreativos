/**
 * Tipos de la feature `deudas` (tolva y préstamos del local).
 *
 * Espejo de `credito_local` / `recuperacion` y de las vistas de saldo
 * (`v_credito_local_saldo`, `v_local_saldo`) creadas en
 * `supabase/migrations/20260612120000_tolva_prestamos_creditos.sql` (T-212).
 *
 * Las cifras económicas (`numeric` en Postgres) viajan como string para
 * preservar precisión; la UI las pasa por Decimal antes de formatear
 * (`formatEur`). Nunca se opera con ellas como number.
 */

export const TIPOS_CREDITO = ["tolva", "prestamo"] as const;
export type TipoCredito = (typeof TIPOS_CREDITO)[number];

export const ESTADOS_CREDITO = ["abierto", "saldado", "condonado"] as const;
export type EstadoCredito = (typeof ESTADOS_CREDITO)[number];

export const ORIGENES_RECUPERACION = ["efectivo", "recaudacion"] as const;
export type OrigenRecuperacion = (typeof ORIGENES_RECUPERACION)[number];

function isTipoCredito(value: string): value is TipoCredito {
  return (TIPOS_CREDITO as readonly string[]).includes(value);
}

function isEstadoCredito(value: string): value is EstadoCredito {
  return (ESTADOS_CREDITO as readonly string[]).includes(value);
}

function isOrigenRecuperacion(value: string): value is OrigenRecuperacion {
  return (ORIGENES_RECUPERACION as readonly string[]).includes(value);
}

/** Una deuda del local con su saldo vivo (vista `v_credito_local_saldo`). */
export interface CreditoLocal {
  id: string;
  empresaId: string;
  localId: string;
  tipo: TipoCredito;
  instalacionId: string | null;
  principal: string;
  tipoInteres: string;
  fecha: string;
  estado: EstadoCredito;
  notas: string | null;
  recuperado: string;
  saldo: string;
}

export interface CreditoLocalRow {
  credito_id: string;
  empresa_id: string;
  local_id: string;
  tipo: string;
  instalacion_id: string | null;
  principal: string;
  tipo_interes: string;
  fecha: string;
  estado: string;
  notas: string | null;
  recuperado: string;
  saldo: string;
}

export function mapCreditoLocalRow(row: CreditoLocalRow): CreditoLocal {
  return {
    id: row.credito_id,
    empresaId: row.empresa_id,
    localId: row.local_id,
    tipo: isTipoCredito(row.tipo) ? row.tipo : "prestamo",
    instalacionId: row.instalacion_id,
    principal: row.principal,
    tipoInteres: row.tipo_interes,
    fecha: row.fecha,
    estado: isEstadoCredito(row.estado) ? row.estado : "abierto",
    notas: row.notas,
    recuperado: row.recuperado,
    saldo: row.saldo,
  };
}

/** Un abono del libro mayor (`recuperacion`), con el tipo de deuda joinado. */
export interface Recuperacion {
  id: string;
  creditoId: string;
  tipoCredito: TipoCredito | null;
  origen: OrigenRecuperacion;
  importe: string;
  recaudacionId: string | null;
  fecha: string;
  notas: string | null;
}

export interface RecuperacionRow {
  id: string;
  credito_id: string;
  origen: string;
  importe: string;
  recaudacion_id: string | null;
  fecha: string;
  notas: string | null;
  credito: { tipo: string } | null;
}

export function mapRecuperacionRow(row: RecuperacionRow): Recuperacion {
  const tipo = row.credito?.tipo ?? null;
  return {
    id: row.id,
    creditoId: row.credito_id,
    tipoCredito: tipo && isTipoCredito(tipo) ? tipo : null,
    origen: isOrigenRecuperacion(row.origen) ? row.origen : "efectivo",
    importe: row.importe,
    recaudacionId: row.recaudacion_id,
    fecha: row.fecha,
    notas: row.notas,
  };
}

/** Saldo de deuda agregado por local (vista `v_local_saldo`, solo abiertas). */
export interface LocalSaldo {
  localId: string;
  saldoTotal: string;
  saldoTolva: string;
  saldoPrestamo: string;
  principalTotal: string;
  recuperadoTotal: string;
  numDeudasAbiertas: number;
}

export interface LocalSaldoRow {
  local_id: string;
  empresa_id: string;
  saldo_total: string;
  saldo_tolva: string;
  saldo_prestamo: string;
  principal_total: string;
  recuperado_total: string;
  num_deudas_abiertas: number;
}

export function mapLocalSaldoRow(row: LocalSaldoRow): LocalSaldo {
  return {
    localId: row.local_id,
    saldoTotal: row.saldo_total,
    saldoTolva: row.saldo_tolva,
    saldoPrestamo: row.saldo_prestamo,
    principalTotal: row.principal_total,
    recuperadoTotal: row.recuperado_total,
    numDeudasAbiertas: Number(row.num_deudas_abiertas),
  };
}

/** Agregado de toda la deuda abierta de la empresa ("capital en la calle"). */
export interface CapitalEnLaCalle {
  /** Suma de saldos vivos (en €, como string) de todas las deudas abiertas. */
  total: string;
  tolva: string;
  prestamo: string;
  /** Número de locales con al menos una deuda abierta. */
  numLocales: number;
}
