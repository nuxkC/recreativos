import { getTranslations } from "next-intl/server";

import { EvolucionChart } from "@/components/informes/evolucion-chart";
import { InformesFiltros } from "@/components/informes/informes-filtros";
import { InformesResumen } from "@/components/informes/informes-resumen";
import { PorLocalChart } from "@/components/informes/por-local-chart";
import { PorMaquinaChart } from "@/components/informes/por-maquina-chart";
import { requireRol } from "@/lib/auth/guards";
import { listarLocalesInformes, obtenerInformes } from "@/lib/informes/queries";
import { resolverFiltros, type InformesSearchParams } from "@/lib/informes/schemas";
import { ROLES_INFORMES } from "@/lib/informes/permisos";

interface InformesPageProps {
  searchParams: Promise<InformesSearchParams>;
}

export default async function InformesPage(props: InformesPageProps) {
  const searchParams = await props.searchParams;
  const activa = await requireRol(ROLES_INFORMES);
  const tNav = await getTranslations("nav");

  const filtros = resolverFiltros(searchParams);

  const [data, locales] = await Promise.all([
    obtenerInformes(activa.empresa.id, filtros),
    listarLocalesInformes(activa.empresa.id),
  ]);

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{tNav("informes")}</h1>
        <p className="text-sm text-muted-foreground">{tNav("descriptions.informes")}</p>
      </div>

      <InformesFiltros
        localInicial={filtros.localId}
        desdeInicial={filtros.desde}
        hastaInicial={filtros.hasta}
        locales={locales}
      />

      <InformesResumen resumen={data.resumen} />

      <EvolucionChart datos={data.evolucionMensual} />

      <div className="grid gap-6 xl:grid-cols-2">
        <PorLocalChart datos={data.porLocal} />
        <PorMaquinaChart datos={data.porMaquina} />
      </div>
    </div>
  );
}
