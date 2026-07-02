import { getTranslations } from "next-intl/server";

import { CatalogoAdmin } from "@/components/catalogo/catalogo-admin";
import { requireAdminCatalogo } from "@/lib/auth/guards";
import { listarFabricantes, listarModelos } from "@/lib/catalogo/queries";

/**
 * Panel de administración GLOBAL del catálogo (curación). Solo accesible a
 * admins del catálogo (`requireAdminCatalogo` redirige a /sin-permiso si no).
 * La autorización real de renombrar/fusionar la aplican las RPCs.
 */
export default async function CatalogoPage() {
  await requireAdminCatalogo();
  const t = await getTranslations("catalogoAdmin");

  const [fabricantes, modelos] = await Promise.all([listarFabricantes(), listarModelos()]);

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{t("titulo")}</h1>
        <p className="text-muted-foreground text-sm">{t("descripcion")}</p>
      </div>
      <CatalogoAdmin fabricantes={fabricantes} modelos={modelos} />
    </div>
  );
}
