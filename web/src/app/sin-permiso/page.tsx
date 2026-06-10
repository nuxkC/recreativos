import { Lock } from "lucide-react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

/**
 * Aterrizan aquí los usuarios que intentan abrir una sección para la
 * que su rol en la empresa activa no tiene acceso (ej. un técnico
 * navegando manualmente a `/licencias/nueva`).
 */
export default async function SinPermisoPage() {
  const t = await getTranslations("sinPermiso");

  return (
    <main className="bg-muted/30 flex min-h-screen items-center justify-center px-4 py-10">
      <Card className="w-full max-w-md">
        <CardHeader className="items-center text-center">
          <Lock className="size-10 text-muted-foreground" aria-hidden />
          <CardTitle>{t("title")}</CardTitle>
          <CardDescription className="text-balance">{t("description")}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild className="w-full">
            <Link href="/dashboard">{t("backToDashboard")}</Link>
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
