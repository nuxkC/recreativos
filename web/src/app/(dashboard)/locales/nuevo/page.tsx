import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { LocalForm } from "@/components/locales/local-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { listarMunicipios, listarProvincias } from "@/lib/locales/geo-queries";

export default async function NuevoLocalPage() {
  await requireRol(ROLES_GESTION);
  const t = await getTranslations("locales");
  const [provincias, municipios] = await Promise.all([listarProvincias(), listarMunicipios()]);

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <Link href="/locales" className="text-muted-foreground text-sm hover:underline">
          ← {t("accion.volver")}
        </Link>
        <h1 className="text-2xl font-semibold tracking-tight">{t("nuevo.titulo")}</h1>
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("formulario.titulo")}</CardTitle>
          <CardDescription>{t("formulario.descripcion")}</CardDescription>
        </CardHeader>
        <CardContent>
          <LocalForm mode="create" provincias={provincias} municipios={municipios} />
        </CardContent>
      </Card>
    </div>
  );
}
