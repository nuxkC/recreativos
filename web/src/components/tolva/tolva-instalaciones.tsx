import { getTranslations } from "next-intl/server";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { formatEur } from "@/lib/recaudaciones/format";
import type { TolvaInstalacion } from "@/lib/tolva/queries";

/**
 * Ficha de tolva del local: por cada máquina instalada, su tolva teórica
 * (objetivo), efectiva (real, derivada del ledger) y pendiente (merma de avería
 * por reponer en la próxima recaudación, §5.6). Se muestra en el contexto del
 * local porque así se ve sin tener que localizar primero la máquina.
 */
export async function TolvaInstalaciones({
  instalaciones,
}: {
  instalaciones: TolvaInstalacion[];
}) {
  if (instalaciones.length === 0) return null;

  const t = await getTranslations("tolva");

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">{t("titulo")}</CardTitle>
        <CardDescription>{t("descripcion")}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {instalaciones.map((inst) => {
          const pendiente = Number(inst.pendiente) > 0;
          return (
            <div
              key={inst.instalacionId}
              className="flex flex-wrap items-center justify-between gap-x-6 gap-y-1 border-b pb-3 last:border-b-0 last:pb-0"
            >
              <div className="min-w-0">
                <p className="font-medium">{inst.maquinaNumeroSerie}</p>
                {inst.maquinaModelo ? (
                  <p className="truncate text-sm text-muted-foreground">{inst.maquinaModelo}</p>
                ) : null}
              </div>
              <dl className="flex items-center gap-6 text-sm">
                <Figura label={t("teorica")} value={formatEur(inst.teorica)} />
                <Figura label={t("efectiva")} value={formatEur(inst.efectiva)} />
                <Figura
                  label={t("pendiente")}
                  value={formatEur(inst.pendiente)}
                  emphasis={pendiente}
                />
              </dl>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

function Figura({
  label,
  value,
  emphasis,
}: {
  label: string;
  value: string;
  emphasis?: boolean;
}) {
  return (
    <div className="text-right">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={emphasis ? "font-semibold text-amber-600 dark:text-amber-500" : "font-medium"}>
        {value}
      </dd>
    </div>
  );
}
