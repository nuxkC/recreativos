import { format, startOfMonth, subMonths } from "date-fns";
import { z } from "zod";

/**
 * Validación de los filtros de informes en la frontera (searchParams).
 *
 * Los filtros viajan por la URL como searchParams (Server Component), así que
 * cualquier valor es `string | undefined` no fiable. Zod los normaliza y
 * descarta los inválidos con `.catch(null)` para no romper la página por un
 * query manipulado a mano.
 */

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ISO_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

const uuidOpcional = z.string().regex(UUID_REGEX).nullable().catch(null);

const fechaOpcional = z.string().regex(ISO_DATE_REGEX).nullable().catch(null);

export const InformesFiltrosSchema = z.object({
  local: uuidOpcional,
  desde: fechaOpcional,
  hasta: fechaOpcional,
});

export interface InformesSearchParams {
  local?: string;
  desde?: string;
  hasta?: string;
}

/** Número de meses que abarca el periodo por defecto (incluyendo el actual). */
export const MESES_POR_DEFECTO = 12;

/** Rango por defecto: los últimos `MESES_POR_DEFECTO` meses hasta hoy. */
export function rangoPorDefecto(ahora: Date = new Date()): { desde: string; hasta: string } {
  return {
    desde: format(startOfMonth(subMonths(ahora, MESES_POR_DEFECTO - 1)), "yyyy-MM-dd"),
    hasta: format(ahora, "yyyy-MM-dd"),
  };
}

/** Filtros efectivos resueltos: el rango siempre tiene valor (por defecto). */
export interface FiltrosResueltos {
  localId: string | null;
  desde: string;
  hasta: string;
}

/**
 * Resuelve los filtros efectivos a partir de los searchParams crudos,
 * aplicando el rango por defecto cuando faltan fechas.
 */
export function resolverFiltros(
  searchParams: InformesSearchParams,
  ahora: Date = new Date(),
): FiltrosResueltos {
  const parsed = InformesFiltrosSchema.safeParse({
    local: searchParams.local,
    desde: searchParams.desde,
    hasta: searchParams.hasta,
  });
  const valores = parsed.success ? parsed.data : { local: null, desde: null, hasta: null };
  const porDefecto = rangoPorDefecto(ahora);

  return {
    localId: valores.local,
    desde: valores.desde ?? porDefecto.desde,
    hasta: valores.hasta ?? porDefecto.hasta,
  };
}
