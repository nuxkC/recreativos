/**
 * Tipos de la feature `cambios-placa`.
 *
 * Espejo de `public.cambio_placa` en
 * `supabase/migrations/20260519220200_create_recaudacion_audit_tables.sql`.
 *
 * Read-only desde la web: los cambios de placa los registra la app del
 * técnico vía Edge Function `crear-cambio-placa` (T-22). La web sólo
 * lista y muestra el detalle.
 */

export interface CambioPlaca {
  id: string;
  empresaId: string;
  instalacionId: string;
  fecha: string;
  usuarioId: string;
  contadorEntradasNuevo: number;
  contadorSalidasNuevo: number;
  motivo: string | null;
  numeroSeriePlacaAnterior: string | null;
  numeroSeriePlacaNueva: string | null;
  fotoUrl: string | null;
  notas: string | null;
  createdAt: string;
  updatedAt: string;
  /** Datos joinados para mostrar en la UI sin lookups extra. */
  instalacion: {
    id: string;
    licencia: { id: string; numero: string } | null;
    maquina: { id: string; numeroSerie: string; modelo: string | null } | null;
    local: { id: string; nombre: string } | null;
  } | null;
  usuario: {
    id: string;
    nombreCompleto: string | null;
  } | null;
}

export interface CambioPlacaRow {
  id: string;
  empresa_id: string;
  instalacion_id: string;
  fecha: string;
  usuario_id: string;
  contador_entradas_nuevo: number;
  contador_salidas_nuevo: number;
  motivo: string | null;
  numero_serie_placa_anterior: string | null;
  numero_serie_placa_nueva: string | null;
  foto_url: string | null;
  notas: string | null;
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
  usuario: {
    id: string;
    nombre_completo: string | null;
  } | null;
}

export function mapCambioPlacaRow(row: CambioPlacaRow): CambioPlaca {
  return {
    id: row.id,
    empresaId: row.empresa_id,
    instalacionId: row.instalacion_id,
    fecha: row.fecha,
    usuarioId: row.usuario_id,
    contadorEntradasNuevo: Number(row.contador_entradas_nuevo),
    contadorSalidasNuevo: Number(row.contador_salidas_nuevo),
    motivo: row.motivo,
    numeroSeriePlacaAnterior: row.numero_serie_placa_anterior,
    numeroSeriePlacaNueva: row.numero_serie_placa_nueva,
    fotoUrl: row.foto_url,
    notas: row.notas,
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
    usuario: row.usuario
      ? {
          id: row.usuario.id,
          nombreCompleto: row.usuario.nombre_completo,
        }
      : null,
  };
}
