import { format, parseISO } from "date-fns";
import { es } from "date-fns/locale";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { z } from "zod";

import { CerrarInstalacion } from "@/components/instalaciones/cerrar-instalacion";
import { DescargarBoletin } from "@/components/instalaciones/descargar-boletin";
import { EliminarInstalacion } from "@/components/instalaciones/eliminar-instalacion";
import { EstadoInstalacionBadge } from "@/components/instalaciones/estado-badge";
import { InstalacionForm } from "@/components/instalaciones/instalacion-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import {
  listarLicenciasResumen,
  listarLocalesResumen,
  listarMaquinasResumen,
  obtenerInstalacion,
} from "@/lib/instalaciones/queries";

const IdSchema = z.string().uuid();

interface InstalacionDetallePageProps {
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

export default async function InstalacionDetallePage({ params }: InstalacionDetallePageProps) {
  const activa = await requireRol(ROLES_GESTION);
  const t = await getTranslations("instalaciones");

  const idParsed = IdSchema.safeParse(params.id);
  if (!idParsed.success) {
    notFound();
  }

  const instalacion = await obtenerInstalacion(activa.empresa.id, idParsed.data);
  if (!instalacion) {
    notFound();
  }

  // En edición las FKs no cambian, así que solo necesitamos las listas
  // de resumen para mostrar el nombre actual. No filtramos por
  // disponibilidad: la maquina/licencia activa es la propia.
  const [licencias, maquinas, locales] = await Promise.all([
    listarLicenciasResumen(activa.empresa.id, false),
    listarMaquinasResumen(activa.empresa.id, false),
    listarLocalesResumen(activa.empresa.id),
  ]);

  const etiqueta =
    `${instalacion.maquina?.numeroSerie ?? ""} @ ${instalacion.local?.nombre ?? ""}`.trim();

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <Link href="/instalaciones" className="text-sm text-muted-foreground hover:underline">
          ← {t("accion.volver")}
        </Link>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-1">
            <h1 className="flex flex-wrap items-center gap-3 text-2xl font-semibold tracking-tight">
              <span>{instalacion.maquina?.numeroSerie ?? "—"}</span>
              <EstadoInstalacionBadge estado={instalacion.estado} />
            </h1>
            <p className="text-sm text-muted-foreground">
              {instalacion.local?.nombre ?? "—"}
              {instalacion.licencia
                ? ` · ${t("campos.licencia")} ${instalacion.licencia.numero}`
                : ""}
            </p>
            <p className="text-xs text-muted-foreground">
              {t("detalle.actualizado", {
                fecha: formatDate(instalacion.updatedAt),
              })}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <DescargarBoletin instalacionId={instalacion.id} />
            {instalacion.estado === "activa" ? (
              <CerrarInstalacion
                instalacionId={instalacion.id}
                fechaInicio={instalacion.fechaInicio}
              />
            ) : null}
            <EliminarInstalacion instalacionId={instalacion.id} etiqueta={etiqueta} />
          </div>
        </div>
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("formulario.titulo")}</CardTitle>
          <CardDescription>
            {instalacion.estado === "cerrada"
              ? t("formulario.descripcionCerrada")
              : t("formulario.descripcionEdicion")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <InstalacionForm
            mode="edit"
            instalacion={instalacion}
            licencias={licencias}
            maquinas={maquinas}
            locales={locales}
          />
        </CardContent>
      </Card>
    </div>
  );
}
