import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { type EstadoAgenda, listarRutas } from "@/lib/operarios/queries";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

// Orden de presentación: lo más urgente primero (atrasado → toca hoy → pendiente → al día → sin planificar).
const ORDEN_ESTADO: Record<EstadoAgenda, number> = {
  atrasado: 0,
  toca_hoy: 1,
  pendiente: 2,
  al_dia: 3,
  sin_planificar: 4,
};

const CLASE_ESTADO: Record<EstadoAgenda, string> = {
  atrasado: "bg-destructive/15 text-destructive",
  toca_hoy: "bg-amber-500/15 text-amber-600 dark:text-amber-400",
  pendiente: "bg-sky-500/15 text-sky-600 dark:text-sky-400",
  al_dia: "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400",
  sin_planificar: "bg-muted text-muted-foreground",
};

export default async function OperariosPage() {
  const activa = await requireRol(ROLES_GESTION);
  const t = await getTranslations("operarios");
  const tRoles = await getTranslations("roles");
  const rutas = await listarRutas(activa.empresa.id);
  const totalPendientes = rutas.reduce((acc, r) => acc + r.pendientes, 0);

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{t("titulo")}</h1>
        <p className="text-sm text-muted-foreground">{t("subtitulo")}</p>
      </div>

      {/* Resumen global: cuántos locales están pendientes en toda la empresa. */}
      <Card>
        <CardContent className="flex items-center justify-between gap-3 py-4">
          <span className="text-sm text-muted-foreground">{t("resumen.label")}</span>
          <span
            className={cn(
              "text-sm font-semibold",
              totalPendientes > 0 ? "text-destructive" : "text-emerald-600 dark:text-emerald-400",
            )}
          >
            {t("resumen.pendientes", { count: totalPendientes })}
          </span>
        </CardContent>
      </Card>

      {rutas.map((ruta) => {
        const esSinAsignar = ruta.operario === null;
        const locales = [...ruta.locales].sort(
          (a, b) =>
            ORDEN_ESTADO[a.estado] - ORDEN_ESTADO[b.estado] || a.nombre.localeCompare(b.nombre, "es"),
        );
        return (
          <Card
            key={ruta.operario?.id ?? "sin-asignar"}
            className={esSinAsignar ? "border-dashed" : undefined}
          >
            <CardHeader>
              <div className="flex items-center justify-between gap-3">
                <CardTitle className="text-lg">
                  {esSinAsignar ? t("sinAsignar") : ruta.operario!.nombre}
                </CardTitle>
                <div className="flex items-center gap-2">
                  {ruta.pendientes > 0 ? (
                    <Badge variant="destructive">
                      {t("pendientesBadge", { count: ruta.pendientes })}
                    </Badge>
                  ) : null}
                  {esSinAsignar ? null : (
                    <Badge variant="secondary">{tRoles(ruta.operario!.rol)}</Badge>
                  )}
                </div>
              </div>
              <CardDescription>{t("localesCount", { count: ruta.locales.length })}</CardDescription>
            </CardHeader>
            <CardContent>
              {ruta.locales.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  {esSinAsignar ? t("sinAsignarVacio") : t("operarioVacio")}
                </p>
              ) : (
                <ul className="divide-y divide-border/60">
                  {locales.map((local) => (
                    <li key={local.id}>
                      <Link
                        href={`/locales/${local.id}`}
                        className="flex items-center justify-between gap-3 py-2 text-sm hover:underline"
                      >
                        <span className="truncate">{local.nombre}</span>
                        <span className="flex shrink-0 items-center gap-2">
                          <span
                            className={cn(
                              "rounded-full px-2 py-0.5 text-xs font-medium",
                              CLASE_ESTADO[local.estado],
                            )}
                          >
                            {t(`estado.${local.estado}`)}
                          </span>
                          <ArrowRight className="size-4 text-muted-foreground" aria-hidden />
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}
