import { Building2 } from "lucide-react";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { seleccionarEmpresa } from "@/lib/empresas/actions";
import { listarMembresiasUsuarioActual } from "@/lib/empresas/queries";
import { createClient } from "@/lib/supabase/server";

/**
 * Pantalla de selección de empresa activa. Se muestra cuando el usuario
 * pertenece a >1 empresa y aún no ha elegido cuál usar (o ha pulsado
 * "Cambiar empresa" desde el menú).
 */
export default async function SeleccionarEmpresaPage() {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) {
    redirect("/login");
  }

  const membresias = await listarMembresiasUsuarioActual();

  if (membresias.length === 0) {
    redirect("/sin-acceso");
  }

  const [unica] = membresias;
  if (membresias.length === 1 && unica) {
    // Atajo: si solo hay una opción, seleccionarla automáticamente.
    const formData = new FormData();
    formData.set("empresaId", unica.empresa.id);
    await seleccionarEmpresa(formData);
  }

  const t = await getTranslations();

  return (
    <main className="bg-muted/30 flex min-h-screen items-center justify-center px-4 py-10">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>{t("seleccionarEmpresa.title")}</CardTitle>
          <CardDescription>
            {t("seleccionarEmpresa.description", { count: membresias.length })}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-2">
          {membresias.map((m) => (
            <form key={m.empresa.id} action={seleccionarEmpresa}>
              <input type="hidden" name="empresaId" value={m.empresa.id} />
              <Button
                type="submit"
                variant="outline"
                className="h-auto w-full justify-start gap-3 px-4 py-3"
              >
                <Building2 className="size-5 text-muted-foreground" aria-hidden />
                <div className="flex flex-col items-start">
                  <span className="text-sm font-medium">{m.empresa.nombre}</span>
                  <span className="text-xs text-muted-foreground">{t(`roles.${m.rol}`)}</span>
                </div>
              </Button>
            </form>
          ))}
        </CardContent>
      </Card>
    </main>
  );
}
