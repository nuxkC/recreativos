"use client";

import { Download } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";

/**
 * Botón "Exportar" que descarga el CSV de recaudaciones respetando los
 * filtros activos. Reenvía los searchParams actuales al Route Handler, que
 * genera el fichero server-side reusando la query (RLS + sesión).
 */
export function ExportarCsv() {
  const t = useTranslations("export");
  const searchParams = useSearchParams();
  const qs = searchParams.toString();
  const href = qs ? `/recaudaciones/export?${qs}` : "/recaudaciones/export";

  return (
    <Button asChild variant="outline" size="sm" className="gap-2">
      <a href={href} aria-label={t("recaudaciones.ariaLabel")} download>
        <Download className="size-4" aria-hidden />
        {t("recaudaciones.boton")}
      </a>
    </Button>
  );
}
