import { Plus } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { MaquinasFilters } from "@/components/maquinas/maquinas-filters";
import { MaquinasTable } from "@/components/maquinas/maquinas-table";
import { Button } from "@/components/ui/button";
import { rolCumple, requireMembresiaActiva } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { listarMaquinas } from "@/lib/maquinas/queries";
import { isEstadoMaquina, type EstadoMaquina } from "@/lib/maquinas/types";

interface MaquinasPageProps {
  searchParams: {
    q?: string;
    estado?: string;
  };
}

export default async function MaquinasPage({ searchParams }: MaquinasPageProps) {
  const activa = await requireMembresiaActiva();
  const t = await getTranslations("maquinas");
  const tNav = await getTranslations("nav");

  const busqueda = searchParams.q?.trim() ?? "";
  const estadoParam =
    searchParams.estado && isEstadoMaquina(searchParams.estado)
      ? (searchParams.estado as EstadoMaquina)
      : null;

  const maquinas = await listarMaquinas(activa.empresa.id, {
    busqueda,
    estado: estadoParam,
  });

  const puedeCrear = rolCumple(activa.rol, ROLES_GESTION);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">{tNav("maquinas")}</h1>
          <p className="text-sm text-muted-foreground">{tNav("descriptions.maquinas")}</p>
        </div>
        {puedeCrear ? (
          <Button asChild className="gap-2">
            <Link href="/maquinas/nueva">
              <Plus className="size-4" aria-hidden />
              {t("accion.nueva")}
            </Link>
          </Button>
        ) : null}
      </div>
      <MaquinasFilters busquedaInicial={busqueda} estadoInicial={estadoParam} />
      <MaquinasTable maquinas={maquinas} />
    </div>
  );
}
