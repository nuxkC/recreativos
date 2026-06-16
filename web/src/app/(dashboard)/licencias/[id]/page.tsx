import { format, parseISO } from "date-fns";
import { es } from "date-fns/locale";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { z } from "zod";

import { EliminarLicencia } from "@/components/licencias/eliminar-licencia";
import { EstadoLicenciaBadge } from "@/components/licencias/estado-badge";
import { LicenciaForm } from "@/components/licencias/licencia-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { obtenerLicencia } from "@/lib/licencias/queries";

const IdSchema = z.string().uuid();

interface LicenciaDetallePageProps {
  params: Promise<{ id: string }>;
}

function formatDate(iso: string | null): string {
  if (!iso) return "—";
  try {
    return format(parseISO(iso), "dd/MM/yyyy HH:mm", { locale: es });
  } catch {
    return iso;
  }
}

export default async function LicenciaDetallePage(props: LicenciaDetallePageProps) {
  const params = await props.params;
  const activa = await requireRol(ROLES_GESTION);
  const t = await getTranslations("licencias");

  const idParsed = IdSchema.safeParse(params.id);
  if (!idParsed.success) {
    notFound();
  }

  const licencia = await obtenerLicencia(activa.empresa.id, idParsed.data);
  if (!licencia) {
    notFound();
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <Link href="/licencias" className="text-sm text-muted-foreground hover:underline">
          ← {t("accion.volver")}
        </Link>
        <div className="flex items-center justify-between gap-4">
          <div className="space-y-1">
            <h1 className="flex items-center gap-3 text-2xl font-semibold tracking-tight">
              <span>{licencia.numero}</span>
              <EstadoLicenciaBadge estado={licencia.estado} />
            </h1>
            <p className="text-sm text-muted-foreground">
              {t("detalle.actualizado", {
                fecha: formatDate(licencia.updatedAt),
              })}
            </p>
          </div>
          <EliminarLicencia licenciaId={licencia.id} numero={licencia.numero} />
        </div>
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("formulario.titulo")}</CardTitle>
          <CardDescription>{t("formulario.descripcion")}</CardDescription>
        </CardHeader>
        <CardContent>
          <LicenciaForm mode="edit" licencia={licencia} />
        </CardContent>
      </Card>
    </div>
  );
}
