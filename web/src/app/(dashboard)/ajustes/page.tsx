import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";

import { AjustesForm } from "@/components/ajustes/ajustes-form";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_ADMIN } from "@/lib/auth/roles";
import { obtenerAjustesEmpresa } from "@/lib/ajustes/queries";
import { esAdminCatalogo } from "@/lib/catalogo/queries";

export default async function AjustesPage() {
  const activa = await requireRol(ROLES_ADMIN);
  const t = await getTranslations("ajustes");
  const tNav = await getTranslations("nav");
  const tCatalogo = await getTranslations("catalogoAdmin");

  const empresa = await obtenerAjustesEmpresa(activa.empresa.id);
  if (!empresa) notFound();

  // El acceso al catálogo GLOBAL es un permiso propio (es_admin_catalogo),
  // independiente del rol de empresa; solo se enlaza si el usuario lo tiene.
  const puedeCurarCatalogo = await esAdminCatalogo();

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{tNav("ajustes")}</h1>
        <p className="text-muted-foreground text-sm">{tNav("descriptions.ajustes")}</p>
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
      {puedeCurarCatalogo ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{tCatalogo("enlace.titulo")}</CardTitle>
            <CardDescription>{tCatalogo("enlace.descripcion")}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild variant="outline">
              <Link href="/catalogo">{tCatalogo("enlace.accion")}</Link>
            </Button>
          </CardContent>
        </Card>
      ) : null}
      <p className="text-muted-foreground text-xs">{t("nota.logo")}</p>
    </div>
  );
}
