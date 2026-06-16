import { AlertTriangle, Banknote, CalendarX, Store, Wrench } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { AlertasList } from "@/components/dashboard/alertas-list";
import { HeroRecaudacion } from "@/components/dashboard/hero-recaudacion";
import { KpiCard } from "@/components/dashboard/kpi-card";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireMembresiaActiva } from "@/lib/auth/guards";
import {
  contarConflictosPendientes,
  contarMaquinasPorEstado,
  listarAlertasPendientes,
  listarInstalacionesSinRecaudar,
  listarLicenciasProximasACaducar,
  obtenerResumenRecaudacion,
  obtenerSerieRecaudacionMensual,
} from "@/lib/dashboard/queries";
import { obtenerCapitalEnLaCalle } from "@/lib/deudas/queries";
import { formatDate, formatEur } from "@/lib/recaudaciones/format";

export default async function DashboardPage() {
  const activa = await requireMembresiaActiva();
  const t = await getTranslations("dashboard");
  const tNav = await getTranslations("nav");

  const [resumen, maquinas, conflictos, licencias, sinRecaudar, alertas, capital, serie] =
    await Promise.all([
      obtenerResumenRecaudacion(activa.empresa.id),
      contarMaquinasPorEstado(activa.empresa.id),
      contarConflictosPendientes(activa.empresa.id),
      listarLicenciasProximasACaducar(activa.empresa.id, 30),
      listarInstalacionesSinRecaudar(activa.empresa.id, 14),
      listarAlertasPendientes(activa.empresa.id, 10),
      obtenerCapitalEnLaCalle(activa.empresa.id),
      obtenerSerieRecaudacionMensual(activa.empresa.id),
    ]);

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{tNav("dashboard")}</h1>
        <p className="text-sm text-muted-foreground">{t("welcome")}</p>
      </div>

      {/* Bento (T-238, T-8): recaudación como dato-héroe (2×2) + 4 KPIs
          clicables, cada uno deep-link a su lista filtrada (T-12). */}
      <div className="grid auto-rows-fr gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <HeroRecaudacion
          bruto={resumen.mesActual.bruto}
          brutoAccesible={formatEur(resumen.mesActual.bruto.toFixed(2))}
          serie={serie}
          variacion={resumen.variacionBruto}
          titulo={t("kpis.recaudacionMes")}
          hintRecuento={t("kpis.recaudacionMesHint", { count: resumen.mesActual.recuento })}
          vsLabel={t("kpis.vsMesAnterior")}
          href="/recaudaciones"
        />
        <KpiCard
          icon={Banknote}
          title={t("kpis.capitalEnLaCalle")}
          value={formatEur(capital.total)}
          hint={t("kpis.capitalEnLaCalleHint", {
            tolva: formatEur(capital.tolva),
            prestamo: formatEur(capital.prestamo),
          })}
          href="/deudas"
          ariaLabel={`${t("kpis.capitalEnLaCalle")}: ${formatEur(capital.total)}`}
        />
        <KpiCard
          icon={Wrench}
          title={t("kpis.averiasAbiertas")}
          value={String(maquinas.averiadas)}
          hint={t("kpis.averiasAbiertasHint", { count: maquinas.averiadas })}
          variant={maquinas.averiadas > 0 ? "destructive" : "default"}
          href="/maquinas?estado=averiada"
          ariaLabel={`${t("kpis.averiasAbiertas")}: ${maquinas.averiadas}`}
        />
        <KpiCard
          icon={AlertTriangle}
          title={t("kpis.conflictosPendientes")}
          value={String(conflictos)}
          hint={conflictos > 0 ? t("kpis.conflictosHintPendientes") : t("kpis.conflictosHintOk")}
          variant={conflictos > 0 ? "warning" : "default"}
          href="/recaudaciones?estado=conflicto"
          ariaLabel={`${t("kpis.conflictosPendientes")}: ${conflictos}`}
        />
        <KpiCard
          icon={CalendarX}
          title={t("kpis.licenciasPorCaducar")}
          value={String(licencias.length)}
          hint={t("kpis.licenciasHint", { dias: 30 })}
          variant={licencias.length > 0 ? "warning" : "default"}
          href="/licencias"
          ariaLabel={`${t("kpis.licenciasPorCaducar")}: ${licencias.length}`}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <Store className="size-4" aria-hidden />
              {t("sinRecaudar.titulo")}
            </CardTitle>
            <CardDescription>{t("sinRecaudar.descripcion", { dias: 14 })}</CardDescription>
          </CardHeader>
          <CardContent>
            {sinRecaudar.length === 0 ? (
              <p className="rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground">
                {t("sinRecaudar.vacio")}
              </p>
            ) : (
              <ul className="divide-y rounded-md border text-sm">
                {sinRecaudar.map((inst) => (
                  <li key={inst.id} className="flex items-center justify-between gap-3 p-3">
                    <div className="min-w-0 flex-1">
                      <Link
                        href={`/instalaciones/${inst.id}`}
                        className="font-medium hover:underline"
                      >
                        {inst.maquinaNumeroSerie ?? "—"}
                      </Link>
                      <p className="truncate text-xs text-muted-foreground">
                        {inst.localNombre ?? "—"}
                      </p>
                    </div>
                    <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                      {inst.ultimaRecaudacion
                        ? t("sinRecaudar.diasSinRecaudar", { dias: inst.diasSinRecaudar })
                        : t("sinRecaudar.nunca")}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <CalendarX className="size-4" aria-hidden />
              {t("licenciasPorCaducar.titulo")}
            </CardTitle>
            <CardDescription>{t("licenciasPorCaducar.descripcion", { dias: 30 })}</CardDescription>
          </CardHeader>
          <CardContent>
            {licencias.length === 0 ? (
              <p className="rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground">
                {t("licenciasPorCaducar.vacio")}
              </p>
            ) : (
              <ul className="divide-y rounded-md border text-sm">
                {licencias.map((lic) => (
                  <li key={lic.id} className="flex items-center justify-between gap-3 p-3">
                    <Link href={`/licencias/${lic.id}`} className="font-medium hover:underline">
                      {lic.numero}
                    </Link>
                    <span
                      className={`shrink-0 text-xs tabular-nums ${
                        lic.diasRestantes < 0
                          ? "text-destructive"
                          : lic.diasRestantes < 7
                            ? "text-warning-text"
                            : "text-muted-foreground"
                      }`}
                    >
                      {lic.diasRestantes < 0
                        ? t("licenciasPorCaducar.caducada", {
                            fecha: formatDate(lic.fechaCaducidad),
                          })
                        : t("licenciasPorCaducar.diasRestantes", {
                            dias: lic.diasRestantes,
                            fecha: formatDate(lic.fechaCaducidad),
                          })}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      <AlertasList alertas={alertas} />
    </div>
  );
}
