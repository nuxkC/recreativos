import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { listarRutas } from "@/lib/operarios/queries";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default async function OperariosPage() {
  const activa = await requireRol(ROLES_GESTION);
  const t = await getTranslations("operarios");
  const tRoles = await getTranslations("roles");
  const rutas = await listarRutas(activa.empresa.id);

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{t("titulo")}</h1>
        <p className="text-sm text-muted-foreground">{t("subtitulo")}</p>
      </div>

      {rutas.map((ruta) => {
        const esSinAsignar = ruta.operario === null;
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
                {esSinAsignar ? null : <Badge variant="secondary">{tRoles(ruta.operario!.rol)}</Badge>}
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
                  {ruta.locales.map((local) => (
                    <li key={local.id}>
                      <Link
                        href={`/locales/${local.id}`}
                        className="flex items-center justify-between gap-3 py-2 text-sm hover:underline"
                      >
                        <span>{local.nombre}</span>
                        <ArrowRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
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
