import { format, parseISO } from "date-fns";
import { es } from "date-fns/locale";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { z } from "zod";

import { CalendarioLocalForm } from "@/components/locales/calendario-local-form";
import { EliminarLocal } from "@/components/locales/eliminar-local";
import { LocalForm } from "@/components/locales/local-form";
import { TolvaInstalaciones } from "@/components/tolva/tolva-instalaciones";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { formatearDireccion } from "@/lib/locales/direccion";
import { listarMunicipios, listarProvincias } from "@/lib/locales/geo-queries";
import { obtenerLocal } from "@/lib/locales/queries";
import { listarOperarios } from "@/lib/operarios/queries";
import { obtenerTolvaInstalaciones } from "@/lib/tolva/queries";

const IdSchema = z.string().guid();

interface LocalDetallePageProps {
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

export default async function LocalDetallePage(props: LocalDetallePageProps) {
  const params = await props.params;
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
  const operarios = await listarOperarios(activa.empresa.id);
  const [provincias, municipios] = await Promise.all([listarProvincias(), listarMunicipios()]);
  // Resuelve código→nombre reutilizando la geo ya cargada para el formulario.
  const direccionMostrada = formatearDireccion(local, {
    municipio: municipios.find((m) => m.codigo === local.municipioCodigo)?.nombre ?? null,
    provincia: provincias.find((p) => p.codigo === local.provinciaCodigo)?.nombre ?? null,
  });

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <Link href="/locales" className="text-muted-foreground text-sm hover:underline">
          ← {t("accion.volver")}
        </Link>
        <div className="flex items-center justify-between gap-4">
          <div className="space-y-1">
            <h1
              className="text-2xl font-semibold tracking-tight"
              // T-244: par compartido con el nombre en la lista (morph lista→detalle).
              style={{ viewTransitionName: `local-name-${local.id}` }}
            >
              {local.nombre}
            </h1>
            <p className="text-muted-foreground text-sm">{direccionMostrada ?? "—"}</p>
            <p className="text-muted-foreground text-xs">
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
          <LocalForm mode="edit" local={local} provincias={provincias} municipios={municipios} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("calendario.titulo")}</CardTitle>
          <CardDescription>{t("calendario.descripcion")}</CardDescription>
        </CardHeader>
        <CardContent>
          <CalendarioLocalForm
            localId={local.id}
            cadenciaSemanas={local.cadenciaSemanas}
            fechaInicioRecaudacion={local.fechaInicioRecaudacion}
            operarioId={local.operarioId}
            operarios={operarios}
          />
        </CardContent>
      </Card>

      <TolvaInstalaciones instalaciones={tolvaInstalaciones} />

      {/* La gestión de deuda vive en la sección Deudas (centro de mando, T-218):
          desde aquí solo se redirige a la página del local en esa sección. */}
      <Link href={`/deudas/${local.id}`} className="block">
        <Card className="hover:bg-accent transition-colors">
          <CardContent className="flex items-center justify-between gap-4 py-4">
            <div className="space-y-0.5">
              <p className="font-medium">{t("deudas.titulo")}</p>
              <p className="text-muted-foreground text-sm">{t("deudas.descripcion")}</p>
            </div>
            <ArrowRight className="text-muted-foreground size-5 shrink-0" aria-hidden />
          </CardContent>
        </Card>
      </Link>
    </div>
  );
}
