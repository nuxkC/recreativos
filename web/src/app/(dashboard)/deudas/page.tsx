import { Banknote, History, Wallet } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { MoneyText } from "@/components/common/money-text";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import {
  listarLocalesConSaldo,
  obtenerActividadDeuda,
  obtenerCapitalEnLaCalle,
} from "@/lib/deudas/queries";
import { formatEur } from "@/lib/recaudaciones/format";

/**
 * Sección "Deudas" = centro de mando completo de la deuda (T-218, T-239). El
 * detalle de un local redirige aquí. La cabecera son TRES lentes de la deuda:
 * saldo total (stock), capital en la calle (composición tolva/préstamo) y
 * actividad reciente (flujo de cobro). Bajo ellas, el ledger de locales: entrar
 * en uno lleva a su gestión (`/deudas/[localId]`: préstamo, abono, condonar, %).
 *
 * La deuda se muestra SIEMPRE en NEUTRO con icono € (MoneyText sin tono): deber
 * no es un error, nunca rojo (T-1).
 */
export default async function DeudasPage() {
  const activa = await requireRol(ROLES_GESTION);
  const t = await getTranslations("deudas");

  const [locales, capital, actividad] = await Promise.all([
    listarLocalesConSaldo(activa.empresa.id),
    obtenerCapitalEnLaCalle(activa.empresa.id),
    obtenerActividadDeuda(activa.empresa.id),
  ]);

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{t("seccion.titulo")}</h1>
        <p className="text-sm text-muted-foreground">{t("seccion.descripcion")}</p>
      </div>

      {/* Centro de mando: 3 tarjetas (T-239). Importes neutros, nunca rojo. */}
      <div className="grid gap-4 sm:grid-cols-3">
        {/* 1 · Saldo total adeudado (stock) — el dato-héroe de la sección. */}
        <Card>
          <CardHeader className="pb-2">
            <CardDescription className="flex items-center gap-2">
              <Wallet className="size-4" aria-hidden />
              {t("seccion.saldoTotal")}
            </CardDescription>
            <CardTitle>
              <MoneyText value={capital.total} size="kpi" />
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">
            {t("seccion.saldoTotalHint", { count: capital.numLocales })}
          </CardContent>
        </Card>

        {/* 2 · Capital en la calle (composición tolva / préstamo). */}
        <Card>
          <CardHeader className="pb-2">
            <CardDescription className="flex items-center gap-2">
              <Banknote className="size-4" aria-hidden />
              {t("seccion.capital")}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-1.5 text-sm">
            <div className="flex items-center justify-between gap-2">
              <span className="text-muted-foreground">{t("seccion.tolvaLabel")}</span>
              <MoneyText value={capital.tolva} />
            </div>
            <div className="flex items-center justify-between gap-2">
              <span className="text-muted-foreground">{t("seccion.prestamoLabel")}</span>
              <MoneyText value={capital.prestamo} />
            </div>
          </CardContent>
        </Card>

        {/* 3 · Actividad reciente (flujo de cobro). */}
        <Card>
          <CardHeader className="pb-2">
            <CardDescription className="flex items-center gap-2">
              <History className="size-4" aria-hidden />
              {t("seccion.actividad")}
            </CardDescription>
            <CardTitle>
              <MoneyText value={actividad.recuperado} size="kpi" />
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-0.5 text-xs text-muted-foreground">
            <p>
              {t("seccion.recuperado")} · {t("seccion.actividadPeriodo", { dias: actividad.dias })}
            </p>
            <p>{t("seccion.actividadMovimientos", { count: actividad.movimientos })}</p>
          </CardContent>
        </Card>
      </div>

      {/* Ledger de locales con saldo: cada uno → su gestión de deuda (T-218). */}
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
                  <MoneyText value={local.saldoTotal} bold />
                </CardContent>
              </Card>
            </Link>
          ))
        )}
      </div>
    </div>
  );
}
