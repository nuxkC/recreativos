import { getTranslations } from "next-intl/server";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

import { RegistroForm } from "./registro-form";

export default async function RegistroPage() {
  const t = await getTranslations("registro");

  return (
    <main className="bg-muted/30 flex min-h-screen items-center justify-center px-4 py-10">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-2xl">{t("title")}</CardTitle>
          <CardDescription>{t("description")}</CardDescription>
        </CardHeader>
        <CardContent>
          <RegistroForm />
        </CardContent>
      </Card>
    </main>
  );
}
