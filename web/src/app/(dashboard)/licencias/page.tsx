import { Plus } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { LicenciasFilters } from "@/components/licencias/licencias-filters";
import { LicenciasTable } from "@/components/licencias/licencias-table";
import { Button } from "@/components/ui/button";
import { rolCumple, requireMembresiaActiva } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { listarLicencias } from "@/lib/licencias/queries";
import { isEstadoLicencia, type EstadoLicencia } from "@/lib/licencias/types";

interface LicenciasPageProps {
  searchParams: Promise<{
    q?: string;
    estado?: string;
  }>;
}

export default async function LicenciasPage(props: LicenciasPageProps) {
  const searchParams = await props.searchParams;
  const activa = await requireMembresiaActiva();
  const t = await getTranslations("licencias");
  const tNav = await getTranslations("nav");

  const busqueda = searchParams.q?.trim() ?? "";
  const estadoParam =
    searchParams.estado && isEstadoLicencia(searchParams.estado)
      ? (searchParams.estado as EstadoLicencia)
      : null;

  const licencias = await listarLicencias(activa.empresa.id, {
    busqueda,
    estado: estadoParam,
  });

  const puedeCrear = rolCumple(activa.rol, ROLES_GESTION);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">{tNav("licencias")}</h1>
          <p className="text-sm text-muted-foreground">{tNav("descriptions.licencias")}</p>
        </div>
        {puedeCrear ? (
          <Button asChild className="gap-2">
            <Link href="/licencias/nueva">
              <Plus className="size-4" aria-hidden />
              {t("accion.nueva")}
            </Link>
          </Button>
        ) : null}
      </div>
      <LicenciasFilters busquedaInicial={busqueda} estadoInicial={estadoParam} />
      <LicenciasTable licencias={licencias} />
    </div>
  );
}
