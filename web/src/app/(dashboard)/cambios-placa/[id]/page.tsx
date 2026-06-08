import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { z } from "zod";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { requireMembresiaActiva } from "@/lib/auth/guards";
import { obtenerCambioPlaca, obtenerSignedUrlFoto } from "@/lib/cambios-placa/queries";
import { formatDateTime } from "@/lib/recaudaciones/format";

const IdSchema = z.string().uuid();

interface CambioPlacaDetallePageProps {
  params: { id: string };
}

interface InfoRowProps {
  label: string;
  value: string;
}

function InfoRow({ label, value }: InfoRowProps) {
  return (
    <div className="flex flex-col gap-0.5 sm:flex-row sm:items-baseline sm:justify-between sm:gap-4">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="text-sm tabular-nums">{value}</dd>
    </div>
  );
}

export default async function CambioPlacaDetallePage({ params }: CambioPlacaDetallePageProps) {
  const activa = await requireMembresiaActiva();
  const t = await getTranslations("cambiosPlaca");

  const idParsed = IdSchema.safeParse(params.id);
  if (!idParsed.success) notFound();

  const cambio = await obtenerCambioPlaca(activa.empresa.id, idParsed.data);
  if (!cambio) notFound();

  const fotoUrl = await obtenerSignedUrlFoto(cambio);

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="space-y-1">
        <Link href="/cambios-placa" className="text-sm text-muted-foreground hover:underline">
          ← {t("accion.volver")}
        </Link>
        <h1 className="text-2xl font-semibold tracking-tight">{formatDateTime(cambio.fecha)}</h1>
        <p className="text-sm text-muted-foreground">
          {cambio.instalacion?.maquina?.numeroSerie ?? "—"}
          {cambio.instalacion?.local ? ` · ${cambio.instalacion.local.nombre}` : ""}
          {cambio.instalacion?.licencia
            ? ` · ${t("campos.licencia")} ${cambio.instalacion.licencia.numero}`
            : ""}
        </p>
        <p className="text-xs text-muted-foreground">
          {t("detalle.usuario", {
            usuario: cambio.usuario?.nombreCompleto ?? "—",
          })}
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t("seccion.contadoresNuevos")}</CardTitle>
            <CardDescription>{t("seccion.contadoresNuevosDescripcion")}</CardDescription>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-2">
              <InfoRow
                label={t("campos.contadorEntradasNuevo")}
                value={cambio.contadorEntradasNuevo.toLocaleString("es-ES")}
              />
              <InfoRow
                label={t("campos.contadorSalidasNuevo")}
                value={cambio.contadorSalidasNuevo.toLocaleString("es-ES")}
              />
              <Separator className="my-1" />
              <InfoRow
                label={t("campos.placaAnterior")}
                value={cambio.numeroSeriePlacaAnterior ?? "—"}
              />
              <InfoRow label={t("campos.placaNueva")} value={cambio.numeroSeriePlacaNueva ?? "—"} />
            </dl>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t("seccion.motivo")}</CardTitle>
            <CardDescription>{t("seccion.motivoDescripcion")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t("campos.motivo")}
              </p>
              <p>{cambio.motivo ?? "—"}</p>
            </div>
            {cambio.notas ? (
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  {t("campos.notas")}
                </p>
                <p className="whitespace-pre-line">{cambio.notas}</p>
              </div>
            ) : null}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("seccion.evidencia")}</CardTitle>
          <CardDescription>{t("seccion.evidenciaDescripcion")}</CardDescription>
        </CardHeader>
        <CardContent>
          {fotoUrl ? (
            <div className="relative aspect-video w-full overflow-hidden rounded-md border bg-muted">
              <Image
                src={fotoUrl}
                alt={t("campos.foto")}
                fill
                sizes="(max-width: 768px) 100vw, 50vw"
                className="object-contain"
                unoptimized
              />
            </div>
          ) : (
            <p className="rounded-md border border-dashed p-3 text-center text-sm text-muted-foreground">
              {t("seccion.sinFoto")}
            </p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("seccion.auditoria")}</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-2 sm:grid-cols-2">
            <InfoRow label={t("campos.creada")} value={formatDateTime(cambio.createdAt)} />
            <InfoRow label={t("campos.actualizada")} value={formatDateTime(cambio.updatedAt)} />
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
