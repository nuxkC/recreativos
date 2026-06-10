import { ShieldAlert } from "lucide-react";
import { getTranslations } from "next-intl/server";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

/**
 * Aterriza aquí un usuario autenticado que aún no pertenece a ninguna
 * empresa activa (p. ej. invitación pendiente o membresías deshabilitadas).
 */
export default async function SinAccesoPage() {
  const t = await getTranslations("sinAcceso");

  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/30 px-4 py-10">
      <Card className="w-full max-w-md">
        <CardHeader className="items-center text-center">
          <ShieldAlert className="size-10 text-muted-foreground" aria-hidden />
          <CardTitle>{t("title")}</CardTitle>
          <CardDescription className="text-balance">{t("description")}</CardDescription>
        </CardHeader>
        <CardContent>
          <form action="/auth/signout" method="post">
            <Button type="submit" variant="outline" className="w-full">
              {t("signOut")}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
