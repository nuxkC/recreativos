import { Plus } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { InstalacionesFilters } from "@/components/instalaciones/instalaciones-filters";
import { InstalacionesTable } from "@/components/instalaciones/instalaciones-table";
import { Button } from "@/components/ui/button";
import { rolCumple, requireMembresiaActiva } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { listarInstalaciones, listarLocalesResumen } from "@/lib/instalaciones/queries";
import { isEstadoInstalacion, type EstadoInstalacion } from "@/lib/instalaciones/types";

interface InstalacionesPageProps {
  searchParams: Promise<{
    estado?: string;
    local?: string;
  }>;
}

export default async function InstalacionesPage(props: InstalacionesPageProps) {
  const searchParams = await props.searchParams;
  const activa = await requireMembresiaActiva();
  const t = await getTranslations("instalaciones");
  const tNav = await getTranslations("nav");

  const estadoParam =
    searchParams.estado && isEstadoInstalacion(searchParams.estado)
      ? (searchParams.estado as EstadoInstalacion)
      : null;
  const localParam = searchParams.local?.trim() || null;

  const [instalaciones, locales] = await Promise.all([
    listarInstalaciones(activa.empresa.id, {
      estado: estadoParam,
      localId: localParam,
    }),
    listarLocalesResumen(activa.empresa.id),
  ]);

  const puedeCrear = rolCumple(activa.rol, ROLES_GESTION);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">{tNav("instalaciones")}</h1>
          <p className="text-sm text-muted-foreground">{tNav("descriptions.instalaciones")}</p>
        </div>
        {puedeCrear ? (
          <Button asChild className="gap-2">
            <Link href="/instalaciones/nueva">
              <Plus className="size-4" aria-hidden />
              {t("accion.nueva")}
            </Link>
          </Button>
        ) : null}
      </div>
      <InstalacionesFilters
        estadoInicial={estadoParam}
        localInicial={localParam}
        locales={locales}
      />
      <InstalacionesTable instalaciones={instalaciones} />
    </div>
  );
}
