import { Banknote } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { listarLocalesConSaldo, obtenerCapitalEnLaCalle } from "@/lib/deudas/queries";
import { formatEur } from "@/lib/recaudaciones/format";

/**
 * Sección "Deudas" (centro de mando, T-218): índice de locales con su saldo de
 * tolva/préstamos + capital en la calle. Entrar en un local lleva a su página
 * de gestión (`/deudas/[localId]`), donde se dan préstamos, abonos en efectivo,
 * condonaciones y se ajusta el % de recuperación.
 */
export default async function DeudasPage() {
  const activa = await requireRol(ROLES_GESTION);
  const t = await getTranslations("deudas");

  const [locales, capital] = await Promise.all([
    listarLocalesConSaldo(activa.empresa.id),
    obtenerCapitalEnLaCalle(activa.empresa.id),
  ]);

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{t("seccion.titulo")}</h1>
        <p className="text-sm text-muted-foreground">{t("seccion.descripcion")}</p>
      </div>

      <Card>
        <CardHeader className="pb-2">
          <CardDescription className="flex items-center gap-2">
            <Banknote className="size-4" aria-hidden />
            {t("seccion.capital")}
          </CardDescription>
          <CardTitle className="text-2xl tabular-nums">{formatEur(capital.total)}</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          {t("seccion.capitalDesglose", {
            tolva: formatEur(capital.tolva),
            prestamo: formatEur(capital.prestamo),
          })}
        </CardContent>
      </Card>

      <div className="space-y-2">
        {locales.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("seccion.vacio")}</p>
        ) : (
          locales.map((local) => (
            <Link key={local.localId} href={`/deudas/${local.localId}`} className="block">
              <Card className="transition-colors hover:bg-accent">
                <CardContent className="flex items-center justify-between gap-4 py-4">
                  <div className="space-y-0.5">
                    <p className="font-medium">{local.nombre}</p>
                    <p className="text-xs text-muted-foreground">
                      {t("seccion.localDesglose", {
                        tolva: formatEur(local.saldoTolva),
                        prestamo: formatEur(local.saldoPrestamo),
                        n: local.numDeudasAbiertas,
                      })}
                    </p>
                  </div>
                  <p className="text-lg font-semibold tabular-nums">
                    {formatEur(local.saldoTotal)}
                  </p>
                </CardContent>
              </Card>
            </Link>
          ))
        )}
      </div>
    </div>
  );
}
