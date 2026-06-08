import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { LicenciaForm } from "@/components/licencias/licencia-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";

export default async function NuevaLicenciaPage() {
  await requireRol(ROLES_GESTION);
  const t = await getTranslations("licencias");

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <Link href="/licencias" className="text-sm text-muted-foreground hover:underline">
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
          <LicenciaForm mode="create" />
        </CardContent>
      </Card>
    </div>
  );
}
