import { formatInTimeZone } from "date-fns-tz";
import { getTranslations } from "next-intl/server";

import { requireMembresiaActiva } from "@/lib/auth/guards";
import {
  nombreFicheroRecaudacionesCsv,
  recaudacionesToCsv,
  type RecaudacionesCsvLabels,
} from "@/lib/export/recaudaciones-csv";
import { listarRecaudaciones } from "@/lib/recaudaciones/queries";
import { isEstadoRecaudacion, type EstadoRecaudacion } from "@/lib/recaudaciones/types";

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ISO_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

function parseEstado(value: string | null): EstadoRecaudacion | "conflicto" | null {
  if (!value) return null;
  if (value === "conflicto") return "conflicto";
  if (isEstadoRecaudacion(value)) return value;
  return null;
}

function parseUuid(value: string | null): string | null {
  return value && UUID_REGEX.test(value) ? value : null;
}

function parseFecha(value: string | null): string | null {
  return value && ISO_DATE_REGEX.test(value) ? value : null;
}

/**
 * Genera un CSV con el listado de recaudaciones respetando los filtros
 * activos (estado, local, instalación, rango de fechas). Reusa la query
 * server-side de la feature, por lo que se aplican RLS y sesión del usuario.
 */
export async function GET(request: Request) {
  const activa = await requireMembresiaActiva();
  const { searchParams } = new URL(request.url);

  const estado = parseEstado(searchParams.get("estado"));
  const localId = parseUuid(searchParams.get("local"));
  const instalacionId = parseUuid(searchParams.get("instalacion"));
  const desde = parseFecha(searchParams.get("desde"));
  const hasta = parseFecha(searchParams.get("hasta"));

  const recaudaciones = await listarRecaudaciones(activa.empresa.id, {
    estado,
    localId,
    instalacionId,
    desde,
    hasta,
  });

  const t = await getTranslations("recaudaciones");
  const tEstado = await getTranslations("recaudaciones.estado");
  const labels: RecaudacionesCsvLabels = {
    fecha: t("campos.fecha"),
    local: t("campos.local"),
    maquina: t("campos.maquina"),
    modelo: t("campos.modelo"),
    bruto: t("campos.bruto"),
    tasa: t("campos.tasaTotal"),
    neto: t("campos.neto"),
    parteLocal: t("campos.parteLocal"),
    parteEmpresa: t("campos.parteEmpresa"),
    tecnico: t("campos.tecnico"),
    estado: t("campos.estado"),
    estadoFirme: tEstado("firme"),
    estadoAnulada: tEstado("anulada"),
  };

  const csv = recaudacionesToCsv(recaudaciones, activa.empresa.zonaHoraria, labels);
  const hoy = formatInTimeZone(new Date(), activa.empresa.zonaHoraria, "yyyy-MM-dd");
  const filename = nombreFicheroRecaudacionesCsv(desde, hasta, hoy);

  return new Response(csv, {
    status: 200,
    headers: {
      "Content-Type": "text/csv; charset=utf-8",
      "Content-Disposition": `attachment; filename="${filename}"`,
      "Cache-Control": "no-store",
    },
  });
}
