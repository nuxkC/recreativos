/**
 * Tipos de la feature `recaudaciones`.
 *
 * Espejo de `public.recaudacion` en
 * `supabase/migrations/20260519220200_create_recaudacion_audit_tables.sql`.
 *
 * Las recaudaciones son INMUTABLES: la web solo lee, descarga el PDF de
 * archivo, y eventualmente las anula vía Edge Function (no UPDATE
 * directo). El alta la hace siempre la app del técnico.
 */

export const ESTADOS_RECAUDACION = ["firme", "anulada"] as const;
export type EstadoRecaudacion = (typeof ESTADOS_RECAUDACION)[number];

export function isEstadoRecaudacion(value: string): value is EstadoRecaudacion {
  return (ESTADOS_RECAUDACION as readonly string[]).includes(value);
}

/**
 * Item de denominación tal y como se persiste en `desglose_total` y
 * `desglose_local` (jsonb).
 */
export interface DenominacionItem {
  denominacion: number;
  cantidad: number;
}

/** Forma normalizada que consume la UI. Las cifras vienen como string
 *  (numeric en Postgres) para preservar precisión. La UI las pasa por
 *  Decimal antes de formatear. */
export interface Recaudacion {
  id: string;
  empresaId: string;
  instalacionId: string;
  tecnicoId: string;
  fecha: string;

  contadorEntradasAnterior: number;
  contadorSalidasAnterior: number;
  contadorEntradasActual: number;
  contadorSalidasActual: number;

  valorCreditoAplicado: string;
  recaudacionBruta: string;
  semanasAplicadas: number;
  tasaSemanalAplicada: string;
  tasaTotalAplicada: string;
  recaudacionNeta: string;
  porcentajeLocalAplicado: string;
  parteLocal: string;
  parteEmpresa: string;

  /** Deuda recuperada (retenida de la parte del local) en esta recaudación. */
  recuperadoTotal: string;
  /** Lo realmente entregado al local: parteLocal − recuperadoTotal (generada). */
  pagadoLocal: string;

  /** Auditoría del redondeo (solo presente si la empresa lo tenía activo). */
  recaudacionBrutaReal: string | null;
  contadorSalidasLeido: number | null;
  redondeoAplicado: number | null;

  desgloseTotal: DenominacionItem[];
  desgloseLocal: DenominacionItem[];

  firmaUrl: string | null;
  fotoEntradasUrl: string | null;
  fotoSalidasUrl: string | null;
  ocrEntradasValor: number | null;
  ocrSalidasValor: number | null;
  pdfUrl: string | null;

  observaciones: string | null;

  dispositivoId: string | null;
  idempotencyKey: string;
  baselineOrigen: string;
  baselineId: string | null;

  conflicto: boolean;
  brutoRecalculado: string | null;
  netoRecalculado: string | null;
  parteLocalRecalculada: string | null;
  parteEmpresaRecalculada: string | null;
  revisadoPor: string | null;
  revisadoEn: string | null;
  resolucion: "aceptada" | "sustituida" | "anulada" | null;
  resolucionNotas: string | null;

  estado: EstadoRecaudacion;
  motivoAnulacion: string | null;
  anuladaPor: string | null;
  anuladaEn: string | null;

  createdAt: string;
  updatedAt: string;

  /** Datos joinados para mostrar nombres en lugar de UUIDs. */
  instalacion: {
    id: string;
    licencia: { id: string; numero: string } | null;
    maquina: { id: string; numeroSerie: string; modelo: string | null } | null;
    local: { id: string; nombre: string } | null;
  } | null;
  tecnico: { id: string; nombreCompleto: string | null } | null;
}

/** Forma cruda que devuelve Supabase (snake_case + jsonb crudo). */
export interface RecaudacionRow {
  id: string;
  empresa_id: string;
  instalacion_id: string;
  tecnico_id: string;
  fecha: string;
  contador_entradas_anterior: number;
  contador_salidas_anterior: number;
  contador_entradas_actual: number;
  contador_salidas_actual: number;
  valor_credito_aplicado: string;
  recaudacion_bruta: string;
  semanas_aplicadas: number;
  tasa_semanal_aplicada: string;
  tasa_total_aplicada: string;
  recaudacion_neta: string;
  porcentaje_local_aplicado: string;
  parte_local: string;
  parte_empresa: string;
  recuperado_total: string;
  pagado_local: string;
  recaudacion_bruta_real: string | null;
  contador_salidas_leido: number | null;
  redondeo_aplicado: number | null;
  desglose_total: unknown;
  desglose_local: unknown;
  firma_url: string | null;
  foto_entradas_url: string | null;
  foto_salidas_url: string | null;
  ocr_entradas_valor: number | null;
  ocr_salidas_valor: number | null;
  pdf_url: string | null;
  observaciones: string | null;
  dispositivo_id: string | null;
  idempotency_key: string;
  baseline_origen: string;
  baseline_id: string | null;
  conflicto: boolean;
  bruto_recalculado: string | null;
  neto_recalculado: string | null;
  parte_local_recalculada: string | null;
  parte_empresa_recalculada: string | null;
  revisado_por: string | null;
  revisado_en: string | null;
  resolucion: string | null;
  resolucion_notas: string | null;
  estado: string;
  motivo_anulacion: string | null;
  anulada_por: string | null;
  anulada_en: string | null;
  created_at: string;
  updated_at: string;
  instalacion: {
    id: string;
    licencia: { id: string; numero: string } | null;
    maquina: {
      id: string;
      numero_serie: string;
      modelo: string | null;
    } | null;
    local: { id: string; nombre: string } | null;
  } | null;
  tecnico: {
    id: string;
    nombre_completo: string | null;
  } | null;
}

function parseDesglose(raw: unknown): DenominacionItem[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((item) => {
      if (typeof item !== "object" || item === null) return null;
      const obj = item as Record<string, unknown>;
      const denom = Number(obj.denominacion);
      const cant = Number(obj.cantidad);
      if (!Number.isFinite(denom) || !Number.isFinite(cant)) return null;
      return { denominacion: denom, cantidad: cant };
    })
    .filter((item): item is DenominacionItem => item !== null);
}

function isResolucion(value: string | null): value is "aceptada" | "sustituida" | "anulada" {
  return value === "aceptada" || value === "sustituida" || value === "anulada";
}

export function mapRecaudacionRow(row: RecaudacionRow): Recaudacion {
  return {
    id: row.id,
    empresaId: row.empresa_id,
    instalacionId: row.instalacion_id,
    tecnicoId: row.tecnico_id,
    fecha: row.fecha,
    contadorEntradasAnterior: Number(row.contador_entradas_anterior),
    contadorSalidasAnterior: Number(row.contador_salidas_anterior),
    contadorEntradasActual: Number(row.contador_entradas_actual),
    contadorSalidasActual: Number(row.contador_salidas_actual),
    valorCreditoAplicado: row.valor_credito_aplicado,
    recaudacionBruta: row.recaudacion_bruta,
    semanasAplicadas: row.semanas_aplicadas,
    tasaSemanalAplicada: row.tasa_semanal_aplicada,
    tasaTotalAplicada: row.tasa_total_aplicada,
    recaudacionNeta: row.recaudacion_neta,
    porcentajeLocalAplicado: row.porcentaje_local_aplicado,
    parteLocal: row.parte_local,
    parteEmpresa: row.parte_empresa,
    recuperadoTotal: row.recuperado_total,
    pagadoLocal: row.pagado_local,
    recaudacionBrutaReal: row.recaudacion_bruta_real,
    contadorSalidasLeido:
      row.contador_salidas_leido === null ? null : Number(row.contador_salidas_leido),
    redondeoAplicado: row.redondeo_aplicado === null ? null : Number(row.redondeo_aplicado),
    desgloseTotal: parseDesglose(row.desglose_total),
    desgloseLocal: parseDesglose(row.desglose_local),
    firmaUrl: row.firma_url,
    fotoEntradasUrl: row.foto_entradas_url,
    fotoSalidasUrl: row.foto_salidas_url,
    ocrEntradasValor: row.ocr_entradas_valor,
    ocrSalidasValor: row.ocr_salidas_valor,
    pdfUrl: row.pdf_url,
    observaciones: row.observaciones,
    dispositivoId: row.dispositivo_id,
    idempotencyKey: row.idempotency_key,
    baselineOrigen: row.baseline_origen,
    baselineId: row.baseline_id,
    conflicto: row.conflicto,
    brutoRecalculado: row.bruto_recalculado,
    netoRecalculado: row.neto_recalculado,
    parteLocalRecalculada: row.parte_local_recalculada,
    parteEmpresaRecalculada: row.parte_empresa_recalculada,
    revisadoPor: row.revisado_por,
    revisadoEn: row.revisado_en,
    resolucion: isResolucion(row.resolucion) ? row.resolucion : null,
    resolucionNotas: row.resolucion_notas,
    estado: isEstadoRecaudacion(row.estado) ? row.estado : "firme",
    motivoAnulacion: row.motivo_anulacion,
    anuladaPor: row.anulada_por,
    anuladaEn: row.anulada_en,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    instalacion: row.instalacion
      ? {
          id: row.instalacion.id,
          licencia: row.instalacion.licencia,
          maquina: row.instalacion.maquina
            ? {
                id: row.instalacion.maquina.id,
                numeroSerie: row.instalacion.maquina.numero_serie,
                modelo: row.instalacion.maquina.modelo,
              }
            : null,
          local: row.instalacion.local,
        }
      : null,
    tecnico: row.tecnico
      ? {
          id: row.tecnico.id,
          nombreCompleto: row.tecnico.nombre_completo,
        }
      : null,
  };
}
