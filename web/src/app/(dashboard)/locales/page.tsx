import { Plus } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { LocalesFilters } from "@/components/locales/locales-filters";
import { LocalesTable } from "@/components/locales/locales-table";
import { Button } from "@/components/ui/button";
import { rolCumple, requireMembresiaActiva } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { listarLocales } from "@/lib/locales/queries";

interface LocalesPageProps {
  searchParams: {
    q?: string;
  };
}

export default async function LocalesPage({ searchParams }: LocalesPageProps) {
  const activa = await requireMembresiaActiva();
  const t = await getTranslations("locales");
  const tNav = await getTranslations("nav");

  const busqueda = searchParams.q?.trim() ?? "";

  const locales = await listarLocales(activa.empresa.id, {
    busqueda,
  });

  const puedeCrear = rolCumple(activa.rol, ROLES_GESTION);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">{tNav("locales")}</h1>
          <p className="text-sm text-muted-foreground">{tNav("descriptions.locales")}</p>
        </div>
        {puedeCrear ? (
          <Button asChild className="gap-2">
            <Link href="/locales/nuevo">
              <Plus className="size-4" aria-hidden />
              {t("accion.nuevo")}
            </Link>
          </Button>
        ) : null}
      </div>
      <LocalesFilters busquedaInicial={busqueda} />
      <LocalesTable locales={locales} />
    </div>
  );
}
