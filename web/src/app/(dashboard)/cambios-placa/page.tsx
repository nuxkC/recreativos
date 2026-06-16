import { getTranslations } from "next-intl/server";

import { CambiosPlacaFilters } from "@/components/cambios-placa/cambios-placa-filters";
import { CambiosPlacaTable } from "@/components/cambios-placa/cambios-placa-table";
import { requireMembresiaActiva } from "@/lib/auth/guards";
import { listarCambiosPlaca, listarLocalesResumen } from "@/lib/cambios-placa/queries";

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ISO_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

interface CambiosPlacaPageProps {
  searchParams: Promise<{
    local?: string;
    desde?: string;
    hasta?: string;
    instalacion?: string;
  }>;
}

export default async function CambiosPlacaPage(props: CambiosPlacaPageProps) {
  const searchParams = await props.searchParams;
  const activa = await requireMembresiaActiva();
  const tNav = await getTranslations("nav");

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

  const [cambios, locales] = await Promise.all([
    listarCambiosPlaca(activa.empresa.id, {
      localId: localParam,
      instalacionId: instalacionParam,
      desde: desdeParam,
      hasta: hastaParam,
    }),
    listarLocalesResumen(activa.empresa.id),
  ]);

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{tNav("cambiosPlaca")}</h1>
        <p className="text-sm text-muted-foreground">{tNav("descriptions.cambiosPlaca")}</p>
      </div>
      <CambiosPlacaFilters
        localInicial={localParam}
        desdeInicial={desdeParam}
        hastaInicial={hastaParam}
        locales={locales}
        instalacionFijaId={instalacionParam}
      />
      <CambiosPlacaTable cambios={cambios} />
    </div>
  );
}
