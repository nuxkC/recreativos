import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { InstalacionForm } from "@/components/instalaciones/instalacion-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import {
  listarLicenciasResumen,
  listarLocalesResumen,
  listarMaquinasResumen,
} from "@/lib/instalaciones/queries";

export default async function NuevaInstalacionPage() {
  const activa = await requireRol(ROLES_GESTION);
  const t = await getTranslations("instalaciones");

  // Al crear: solo licencias / máquinas SIN una instalación activa.
  const [licencias, maquinas, locales] = await Promise.all([
    listarLicenciasResumen(activa.empresa.id, true),
    listarMaquinasResumen(activa.empresa.id, true),
    listarLocalesResumen(activa.empresa.id),
  ]);

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <Link href="/instalaciones" className="text-sm text-muted-foreground hover:underline">
          ← {t("accion.volver")}
        </Link>
        <h1 className="text-2xl font-semibold tracking-tight">{t("nueva.titulo")}</h1>
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("formulario.titulo")}</CardTitle>
          <CardDescription>{t("formulario.descripcion")}</CardDescription>
        </CardHeader>
        <CardContent>
          {licencias.length === 0 || maquinas.length === 0 ? (
            <div className="rounded-md border border-dashed p-6 text-sm text-muted-foreground">
              {licencias.length === 0
                ? t("nueva.sinLicenciasDisponibles")
                : t("nueva.sinMaquinasDisponibles")}
            </div>
          ) : (
            <InstalacionForm
              mode="create"
              licencias={licencias}
              maquinas={maquinas}
              locales={locales}
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
