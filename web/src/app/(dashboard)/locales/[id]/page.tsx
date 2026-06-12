import { format, parseISO } from "date-fns";
import { es } from "date-fns/locale";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { z } from "zod";

import { EliminarLocal } from "@/components/locales/eliminar-local";
import { LocalForm } from "@/components/locales/local-form";
import { TolvaInstalaciones } from "@/components/tolva/tolva-instalaciones";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { obtenerLocal } from "@/lib/locales/queries";
import { obtenerTolvaInstalaciones } from "@/lib/tolva/queries";

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

  const tolvaInstalaciones = await obtenerTolvaInstalaciones(local.id);

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

      <TolvaInstalaciones instalaciones={tolvaInstalaciones} />

      {/* La gestión de deuda vive en la sección Deudas (centro de mando, T-218):
          desde aquí solo se redirige a la página del local en esa sección. */}
      <Link href={`/deudas/${local.id}`} className="block">
        <Card className="transition-colors hover:bg-accent">
          <CardContent className="flex items-center justify-between gap-4 py-4">
            <div className="space-y-0.5">
              <p className="font-medium">{t("deudas.titulo")}</p>
              <p className="text-sm text-muted-foreground">{t("deudas.descripcion")}</p>
            </div>
            <ArrowRight className="size-5 shrink-0 text-muted-foreground" aria-hidden />
          </CardContent>
        </Card>
      </Link>
    </div>
  );
}
