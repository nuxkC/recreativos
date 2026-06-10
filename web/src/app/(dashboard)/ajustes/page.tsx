import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";

import { AjustesForm } from "@/components/ajustes/ajustes-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_ADMIN } from "@/lib/auth/roles";
import { obtenerAjustesEmpresa } from "@/lib/ajustes/queries";

export default async function AjustesPage() {
  const activa = await requireRol(ROLES_ADMIN);
  const t = await getTranslations("ajustes");
  const tNav = await getTranslations("nav");

  const empresa = await obtenerAjustesEmpresa(activa.empresa.id);
  if (!empresa) notFound();

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{tNav("ajustes")}</h1>
        <p className="text-sm text-muted-foreground">{tNav("descriptions.ajustes")}</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("formulario.titulo")}</CardTitle>
          <CardDescription>{t("formulario.descripcion")}</CardDescription>
        </CardHeader>
        <CardContent>
          <AjustesForm empresa={empresa} />
        </CardContent>
      </Card>
      <p className="text-xs text-muted-foreground">{t("nota.logo")}</p>
    </div>
  );
}
