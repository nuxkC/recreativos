import { AlertTriangle, CalendarX, Coins, Gamepad2, Store } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { AlertasList } from "@/components/dashboard/alertas-list";
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
} from "@/lib/dashboard/queries";
import { formatDate, formatEur } from "@/lib/recaudaciones/format";

export default async function DashboardPage() {
  const activa = await requireMembresiaActiva();
  const t = await getTranslations("dashboard");
  const tNav = await getTranslations("nav");

  const [resumen, maquinas, conflictos, licencias, sinRecaudar, alertas] = await Promise.all([
    obtenerResumenRecaudacion(activa.empresa.id),
    contarMaquinasPorEstado(activa.empresa.id),
    contarConflictosPendientes(activa.empresa.id),
    listarLicenciasProximasACaducar(activa.empresa.id, 30),
    listarInstalacionesSinRecaudar(activa.empresa.id, 14),
    listarAlertasPendientes(activa.empresa.id, 10),
  ]);

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{tNav("dashboard")}</h1>
        <p className="text-sm text-muted-foreground">{t("welcome")}</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard
          icon={Coins}
          title={t("kpis.recaudacionMes")}
          value={formatEur(resumen.mesActual.bruto.toFixed(2))}
          hint={t("kpis.recaudacionMesHint", { count: resumen.mesActual.recuento })}
          trend={resumen.variacionBruto}
          trendLabel={t("kpis.vsMesAnterior")}
        />
        <KpiCard
          icon={Gamepad2}
          title={t("kpis.maquinasInstaladas")}
          value={`${maquinas.instaladas} / ${maquinas.total}`}
          hint={t("kpis.maquinasHint", {
            almacen: maquinas.almacen,
            averiadas: maquinas.averiadas,
          })}
        />
        <KpiCard
          icon={AlertTriangle}
          title={t("kpis.conflictosPendientes")}
          value={String(conflictos)}
          hint={conflictos > 0 ? t("kpis.conflictosHintPendientes") : t("kpis.conflictosHintOk")}
          variant={conflictos > 0 ? "warning" : "default"}
        />
        <KpiCard
          icon={CalendarX}
          title={t("kpis.licenciasPorCaducar")}
          value={String(licencias.length)}
          hint={t("kpis.licenciasHint", { dias: 30 })}
          variant={licencias.length > 0 ? "warning" : "default"}
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
                            ? "text-amber-600 dark:text-amber-400"
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
