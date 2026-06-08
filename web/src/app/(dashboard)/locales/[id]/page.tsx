import { format, parseISO } from "date-fns";
import { es } from "date-fns/locale";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { z } from "zod";

import { EliminarLocal } from "@/components/locales/eliminar-local";
import { LocalForm } from "@/components/locales/local-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { obtenerLocal } from "@/lib/locales/queries";

const IdSchema = z.string().uuid();

interface LocalDetallePageProps {
  params: { id: string };
}

function formatDate(iso: string | null): string {
  if (!iso) return "—";
  try {
    return format(parseISO(iso), "dd/MM/yyyy HH:mm", { locale: es });
  } catch {
    return iso;
  }
}

export default async function LocalDetallePage({ params }: LocalDetallePageProps) {
  const activa = await requireRol(ROLES_GESTION);
  const t = await getTranslations("locales");

  const idParsed = IdSchema.safeParse(params.id);
  if (!idParsed.success) {
    notFound();
  }

  const local = await obtenerLocal(activa.empresa.id, idParsed.data);
  if (!local) {
    notFound();
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <Link href="/locales" className="text-sm text-muted-foreground hover:underline">
          ← {t("accion.volver")}
        </Link>
        <div className="flex items-center justify-between gap-4">
          <div className="space-y-1">
            <h1 className="text-2xl font-semibold tracking-tight">{local.nombre}</h1>
            <p className="text-sm text-muted-foreground">{local.direccion ?? "—"}</p>
            <p className="text-xs text-muted-foreground">
              {t("detalle.actualizado", {
                fecha: formatDate(local.updatedAt),
              })}
            </p>
          </div>
          <EliminarLocal localId={local.id} nombre={local.nombre} />
        </div>
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("formulario.titulo")}</CardTitle>
          <CardDescription>{t("formulario.descripcion")}</CardDescription>
        </CardHeader>
        <CardContent>
          <LocalForm mode="edit" local={local} />
        </CardContent>
      </Card>
    </div>
  );
}
