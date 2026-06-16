import { format, parseISO } from "date-fns";
import { es } from "date-fns/locale";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { z } from "zod";

import { HistorialAverias } from "@/components/averias/historial-averias";
import { EliminarMaquina } from "@/components/maquinas/eliminar-maquina";
import { EstadoMaquinaBadge } from "@/components/maquinas/estado-badge";
import { MaquinaForm } from "@/components/maquinas/maquina-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { listarAveriasMaquina, maquinaTieneInstalacionActiva } from "@/lib/averias/queries";
import { requireRol } from "@/lib/auth/guards";
import { ROLES_GESTION } from "@/lib/auth/roles";
import { obtenerMaquina } from "@/lib/maquinas/queries";

const IdSchema = z.string().guid();

interface MaquinaDetallePageProps {
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

export default async function MaquinaDetallePage(props: MaquinaDetallePageProps) {
  const params = await props.params;
  const activa = await requireRol(ROLES_GESTION);
  const t = await getTranslations("maquinas");

  const idParsed = IdSchema.safeParse(params.id);
  if (!idParsed.success) {
    notFound();
  }

  const maquina = await obtenerMaquina(activa.empresa.id, idParsed.data);
  if (!maquina) {
    notFound();
  }

  const [averias, maquinaInstalada] = await Promise.all([
    listarAveriasMaquina(activa.empresa.id, maquina.id),
    maquinaTieneInstalacionActiva(activa.empresa.id, maquina.id),
  ]);

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <Link href="/maquinas" className="text-sm text-muted-foreground hover:underline">
          ← {t("accion.volver")}
        </Link>
        <div className="flex items-center justify-between gap-4">
          <div className="space-y-1">
            <h1 className="flex flex-wrap items-center gap-3 text-2xl font-semibold tracking-tight">
              <span>{maquina.numeroSerie}</span>
              {maquina.modelo ? (
                <span className="text-base font-normal text-muted-foreground">
                  {maquina.modelo}
                </span>
              ) : null}
              <EstadoMaquinaBadge estado={maquina.estado} />
            </h1>
            <p className="text-sm text-muted-foreground">
              {t("detalle.actualizado", {
                fecha: formatDate(maquina.updatedAt),
              })}
            </p>
          </div>
          <EliminarMaquina maquinaId={maquina.id} numeroSerie={maquina.numeroSerie} />
        </div>
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("formulario.titulo")}</CardTitle>
          <CardDescription>{t("formulario.descripcion")}</CardDescription>
        </CardHeader>
        <CardContent>
          <MaquinaForm mode="edit" maquina={maquina} />
        </CardContent>
      </Card>
      <HistorialAverias
        maquinaId={maquina.id}
        averias={averias}
        maquinaInstalada={maquinaInstalada}
      />
    </div>
  );
}
