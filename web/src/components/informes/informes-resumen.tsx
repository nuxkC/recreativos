import { Coins, Landmark, Receipt, TrendingUp } from "lucide-react";
import { getTranslations } from "next-intl/server";

import { KpiCard } from "@/components/dashboard/kpi-card";
import { formatEuros } from "@/lib/informes/format";
import type { ResumenInformes } from "@/lib/informes/types";

interface InformesResumenProps {
  resumen: ResumenInformes;
}

/**
 * Cabecera de KPIs con los totales del periodo seleccionado. Server Component:
 * solo presenta cifras ya calculadas por el servidor.
 */
export async function InformesResumen({ resumen }: InformesResumenProps) {
  const t = await getTranslations("informes.resumen");

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <KpiCard
        icon={Coins}
        title={t("brutoTotal")}
        value={formatEuros(resumen.brutoTotal)}
        hint={t("periodoHint")}
      />
      <KpiCard
        icon={TrendingUp}
        title={t("netoTotal")}
        value={formatEuros(resumen.netoTotal)}
        hint={t("periodoHint")}
      />
      <KpiCard
        icon={Landmark}
        title={t("parteEmpresaTotal")}
        value={formatEuros(resumen.parteEmpresaTotal)}
        hint={t("periodoHint")}
      />
      <KpiCard
        icon={Receipt}
        title={t("numRecaudaciones")}
        value={String(resumen.numRecaudaciones)}
        hint={t("periodoHint")}
      />
    </div>
  );
}
