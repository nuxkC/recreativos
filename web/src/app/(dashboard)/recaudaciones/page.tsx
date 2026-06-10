import { getTranslations } from "next-intl/server";

import { ExportarCsv } from "@/components/recaudaciones/exportar-csv";
import { RecaudacionesFilters } from "@/components/recaudaciones/recaudaciones-filters";
import { RecaudacionesTable } from "@/components/recaudaciones/recaudaciones-table";
import { requireMembresiaActiva } from "@/lib/auth/guards";
import { listarLocalesResumen, listarRecaudaciones } from "@/lib/recaudaciones/queries";
import {
  ESTADOS_RECAUDACION,
  isEstadoRecaudacion,
  type EstadoRecaudacion,
} from "@/lib/recaudaciones/types";

const ESTADO_FILTROS_VALIDOS = [...ESTADOS_RECAUDACION, "conflicto"] as const;

interface RecaudacionesPageProps {
  searchParams: {
    estado?: string;
    local?: string;
    desde?: string;
    hasta?: string;
    instalacion?: string;
  };
}

function parseEstado(value: string | undefined): EstadoRecaudacion | "conflicto" | null {
  if (!value) return null;
  if (value === "conflicto") return "conflicto";
  if (isEstadoRecaudacion(value)) return value;
  return null;
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ISO_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

export default async function RecaudacionesPage({ searchParams }: RecaudacionesPageProps) {
  const activa = await requireMembresiaActiva();
  const tNav = await getTranslations("nav");

  const estadoParam = parseEstado(searchParams.estado);
  const localParam =
    searchParams.local && UUID_REGEX.test(searchParams.local) ? searchParams.local : null;
  const instalacionParam =
    searchParams.instalacion && UUID_REGEX.test(searchParams.instalacion)
      ? searchParams.instalacion
      : null;
  const desdeParam =
    searchParams.desde && ISO_DATE_REGEX.test(searchParams.desde) ? searchParams.desde : null;
  const hastaParam =
    searchParams.hasta && ISO_DATE_REGEX.test(searchParams.hasta) ? searchParams.hasta : null;

  const [recaudaciones, locales] = await Promise.all([
    listarRecaudaciones(activa.empresa.id, {
      estado: estadoParam,
      localId: localParam,
      instalacionId: instalacionParam,
      desde: desdeParam,
      hasta: hastaParam,
    }),
    listarLocalesResumen(activa.empresa.id),
  ]);

  const estadoFiltro = (ESTADO_FILTROS_VALIDOS as readonly string[]).includes(estadoParam ?? "")
    ? (estadoParam as (typeof ESTADO_FILTROS_VALIDOS)[number])
    : null;

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">{tNav("recaudaciones")}</h1>
          <p className="text-sm text-muted-foreground">{tNav("descriptions.recaudaciones")}</p>
        </div>
        <ExportarCsv />
      </div>
      <RecaudacionesFilters
        estadoInicial={estadoFiltro}
        localInicial={localParam}
        desdeInicial={desdeParam}
        hastaInicial={hastaParam}
        locales={locales}
        instalacionFijaId={instalacionParam}
      />
      <RecaudacionesTable recaudaciones={recaudaciones} />
    </div>
  );
}
