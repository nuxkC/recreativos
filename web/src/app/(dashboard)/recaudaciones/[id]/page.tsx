import Decimal from "decimal.js";
import { AlertTriangle } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { z } from "zod";

import { StatusChip } from "@/components/common/status-chip";
import { ResolverConflicto } from "@/components/conflictos/resolver-conflicto";
import { AnularRecaudacion } from "@/components/recaudaciones/anular-recaudacion";
import { DescargarPdf } from "@/components/recaudaciones/descargar-pdf";
import { DesgloseTable } from "@/components/recaudaciones/desglose-table";
import { EstadoRecaudacionBadges } from "@/components/recaudaciones/estado-badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { rolCumple, requireMembresiaActiva } from "@/lib/auth/guards";
import { ROLES_ADMIN } from "@/lib/auth/roles";
import { formatDate, formatDateTime, formatEur, formatPercent } from "@/lib/recaudaciones/format";
import { obtenerRecaudacion, obtenerSignedUrlsEvidencia } from "@/lib/recaudaciones/queries";

const IdSchema = z.string().guid();

interface RecaudacionDetallePageProps {
  params: Promise<{ id: string }>;
}

interface InfoRowProps {
  label: string;
  value: string;
  emphasis?: boolean;
  hint?: string;
}

function InfoRow({ label, value, emphasis, hint }: InfoRowProps) {
  return (
    <div className="flex flex-col gap-0.5 sm:flex-row sm:items-baseline sm:justify-between sm:gap-4">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className={emphasis ? "text-base font-semibold tabular-nums" : "text-sm tabular-nums"}>
        {value}
        {hint ? (
          <span className="ml-1 text-xs font-normal text-muted-foreground">{hint}</span>
        ) : null}
      </dd>
    </div>
  );
}

export default async function RecaudacionDetallePage(props: RecaudacionDetallePageProps) {
  const params = await props.params;
  const activa = await requireMembresiaActiva();
  const t = await getTranslations("recaudaciones");

  const idParsed = IdSchema.safeParse(params.id);
  if (!idParsed.success) notFound();

  const recaudacion = await obtenerRecaudacion(activa.empresa.id, idParsed.data);
  if (!recaudacion) notFound();

  const evidencia = await obtenerSignedUrlsEvidencia(recaudacion);

  const puedeAnular = rolCumple(activa.rol, ROLES_ADMIN);
  const puedeResolver = rolCumple(activa.rol, ROLES_ADMIN);
  const esAdmin = rolCumple(activa.rol, ROLES_ADMIN);
  const huboRedondeo = (recaudacion.redondeoAplicado ?? 0) > 0;
  const huboRecuperacion = Number(recaudacion.recuperadoTotal) > 0;
  const huboReposicionTolva = Number(recaudacion.reposicionTolva) > 0;
  // base_reparto no se persiste: es lo que se reparte tras devolver la tolva,
  // = parte_local + parte_empresa (exacto, = neto − reposición). Decimal, sin float.
  const baseReparto = new Decimal(recaudacion.parteLocal).plus(recaudacion.parteEmpresa).toFixed(2);
  const conflictoPendiente = recaudacion.conflicto && recaudacion.revisadoEn === null;
  // Delta del descuadre (servidor − registrado), money-safe con Decimal. El
  // signo explícito comunica el sentido de la diferencia (T-240).
  const deltaBrutoDec = new Decimal(
    recaudacion.brutoRecalculado ?? recaudacion.recaudacionBruta,
  ).minus(recaudacion.recaudacionBruta);
  const deltaBrutoLabel = `${deltaBrutoDec.gt(0) ? "+" : ""}${formatEur(deltaBrutoDec.toFixed(2))}`;

  const deltaEntradas = recaudacion.contadorEntradasActual - recaudacion.contadorEntradasAnterior;
  const deltaSalidas = recaudacion.contadorSalidasActual - recaudacion.contadorSalidasAnterior;

  return (
    <div className="mx-auto max-w-4xl space-y-4">
      <div className="space-y-1">
        <Link href="/recaudaciones" className="text-sm text-muted-foreground hover:underline">
          ← {t("accion.volver")}
        </Link>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-1">
            <h1 className="flex flex-wrap items-center gap-3 text-2xl font-semibold tracking-tight">
              <span>{formatDateTime(recaudacion.fecha)}</span>
              <EstadoRecaudacionBadges
                estado={recaudacion.estado}
                conflicto={recaudacion.conflicto}
                conflictoResuelto={recaudacion.revisadoEn !== null}
              />
            </h1>
            <p className="text-sm text-muted-foreground">
              {recaudacion.instalacion?.maquina?.numeroSerie ?? "—"}
              {recaudacion.instalacion?.local ? ` · ${recaudacion.instalacion.local.nombre}` : ""}
              {recaudacion.instalacion?.licencia
                ? ` · ${t("campos.licencia")} ${recaudacion.instalacion.licencia.numero}`
                : ""}
            </p>
            <p className="text-xs text-muted-foreground">
              {t("detalle.tecnico", {
                tecnico: recaudacion.tecnico?.nombreCompleto ?? "—",
              })}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <DescargarPdf recaudacionId={recaudacion.id} disponible={recaudacion.pdfUrl !== null} />
            {puedeAnular && recaudacion.estado === "firme" ? (
              <AnularRecaudacion recaudacionId={recaudacion.id} />
            ) : null}
          </div>
        </div>
      </div>

      {conflictoPendiente ? (
        <Card className="border-warning/40 bg-warning-subtle">
          <CardHeader className="flex-row items-start gap-3 space-y-0">
            <AlertTriangle className="size-5 text-warning" aria-hidden />
            <div className="space-y-1">
              <CardTitle className="text-base">{t("conflicto.titulo")}</CardTitle>
              <CardDescription>{t("conflicto.descripcion")}</CardDescription>
            </div>
          </CardHeader>
          <CardContent className="space-y-3">
            {/* Delta inline del descuadre: icono + texto + color de estado (T-240). */}
            <StatusChip
              role="warning"
              icon={<AlertTriangle className="size-3.5" aria-hidden />}
              label={t("conflicto.delta", { delta: deltaBrutoLabel })}
            />
            <dl className="grid gap-3 sm:grid-cols-2">
              <InfoRow
                label={t("conflicto.brutoCliente")}
                value={formatEur(recaudacion.recaudacionBruta)}
              />
              <InfoRow
                label={t("conflicto.brutoServidor")}
                value={formatEur(recaudacion.brutoRecalculado)}
              />
              <InfoRow
                label={t("conflicto.netoCliente")}
                value={formatEur(recaudacion.recaudacionNeta)}
              />
              <InfoRow
                label={t("conflicto.netoServidor")}
                value={formatEur(recaudacion.netoRecalculado)}
              />
              <InfoRow
                label={t("conflicto.parteLocalCliente")}
                value={formatEur(recaudacion.parteLocal)}
              />
              <InfoRow
                label={t("conflicto.parteLocalServidor")}
                value={formatEur(recaudacion.parteLocalRecalculada)}
              />
            </dl>
          </CardContent>
        </Card>
      ) : null}

      {conflictoPendiente && puedeResolver ? (
        <ResolverConflicto recaudacionId={recaudacion.id} />
      ) : null}

      {recaudacion.estado === "anulada" ? (
        <Card className="bg-muted/30 border-muted">
          <CardHeader>
            <CardTitle className="text-base">{t("anulacion.titulo")}</CardTitle>
            <CardDescription>
              {t("anulacion.fecha", {
                fecha: formatDateTime(recaudacion.anuladaEn),
              })}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm">
              <span className="font-medium">{t("anulacion.motivo")}: </span>
              {recaudacion.motivoAnulacion ?? "—"}
            </p>
          </CardContent>
        </Card>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t("seccion.cifras")}</CardTitle>
            <CardDescription>
              {t("seccion.cifrasDescripcion", {
                semanas: recaudacion.semanasAplicadas,
              })}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-2">
              <InfoRow
                label={t("campos.bruto")}
                value={formatEur(recaudacion.recaudacionBruta)}
                emphasis
              />
              <Separator className="my-1" />
              <InfoRow
                label={t("campos.tasaSemanal")}
                value={formatEur(recaudacion.tasaSemanalAplicada)}
                hint={t("detalle.semanasHint", {
                  semanas: recaudacion.semanasAplicadas,
                })}
              />
              <InfoRow
                label={t("campos.tasaTotal")}
                value={formatEur(recaudacion.tasaTotalAplicada)}
              />
              <Separator className="my-1" />
              <InfoRow
                label={t("campos.neto")}
                value={formatEur(recaudacion.recaudacionNeta)}
                emphasis
              />
              {huboReposicionTolva ? (
                <>
                  <InfoRow
                    label={t("campos.repuestoTolva")}
                    value={`− ${formatEur(recaudacion.reposicionTolva)}`}
                  />
                  <InfoRow
                    label={t("campos.baseReparto")}
                    value={formatEur(baseReparto)}
                    emphasis
                  />
                </>
              ) : null}
              <InfoRow
                label={t("campos.parteLocal")}
                value={formatEur(recaudacion.parteLocal)}
                hint={formatPercent(recaudacion.porcentajeLocalAplicado)}
              />
              {huboRecuperacion ? (
                <>
                  <InfoRow
                    label={t("campos.recuperado")}
                    value={`− ${formatEur(recaudacion.recuperadoTotal)}`}
                  />
                  <InfoRow
                    label={t("campos.entregadoLocal")}
                    value={formatEur(recaudacion.pagadoLocal)}
                    emphasis
                  />
                </>
              ) : null}
              <InfoRow
                label={t("campos.parteEmpresa")}
                value={formatEur(recaudacion.parteEmpresa)}
              />
              <Separator className="my-1" />
              <InfoRow
                label={t("campos.valorCredito")}
                value={formatEur(recaudacion.valorCreditoAplicado)}
              />
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t("seccion.contadores")}</CardTitle>
            <CardDescription>{t("seccion.contadoresDescripcion")}</CardDescription>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-2">
              <InfoRow
                label={t("campos.entradasAnterior")}
                value={recaudacion.contadorEntradasAnterior.toLocaleString("es-ES")}
              />
              <InfoRow
                label={t("campos.entradasActual")}
                value={recaudacion.contadorEntradasActual.toLocaleString("es-ES")}
              />
              <InfoRow
                label={t("campos.entradasDelta")}
                value={deltaEntradas.toLocaleString("es-ES")}
                emphasis
              />
              <Separator className="my-1" />
              <InfoRow
                label={t("campos.salidasAnterior")}
                value={recaudacion.contadorSalidasAnterior.toLocaleString("es-ES")}
              />
              <InfoRow
                label={t("campos.salidasActual")}
                value={recaudacion.contadorSalidasActual.toLocaleString("es-ES")}
              />
              <InfoRow
                label={t("campos.salidasDelta")}
                value={deltaSalidas.toLocaleString("es-ES")}
                emphasis
              />
              <Separator className="my-1" />
              <InfoRow
                label={t("campos.baselineOrigen")}
                value={t(`baselineOrigen.${recaudacion.baselineOrigen}`)}
              />
            </dl>
          </CardContent>
        </Card>
      </div>

      {esAdmin && huboRedondeo ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t("seccion.redondeo")}</CardTitle>
            <CardDescription>
              {t("seccion.redondeoDescripcion", { unidad: recaudacion.redondeoAplicado ?? 0 })}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-3 sm:grid-cols-2">
              <InfoRow
                label={t("campos.brutoReal")}
                value={formatEur(recaudacion.recaudacionBrutaReal)}
              />
              <InfoRow
                label={t("campos.brutoRedondeado")}
                value={formatEur(recaudacion.recaudacionBruta)}
              />
              <InfoRow
                label={t("campos.salidasLeido")}
                value={(
                  recaudacion.contadorSalidasLeido ?? recaudacion.contadorSalidasActual
                ).toLocaleString("es-ES")}
              />
              <InfoRow
                label={t("campos.salidasAjustado")}
                value={recaudacion.contadorSalidasActual.toLocaleString("es-ES")}
              />
            </dl>
          </CardContent>
        </Card>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t("seccion.desgloseTotal")}</CardTitle>
            <CardDescription>{t("seccion.desgloseTotalDescripcion")}</CardDescription>
          </CardHeader>
          <CardContent>
            <DesgloseTable items={recaudacion.desgloseTotal} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t("seccion.desgloseLocal")}</CardTitle>
            <CardDescription>{t("seccion.desgloseLocalDescripcion")}</CardDescription>
          </CardHeader>
          <CardContent>
            <DesgloseTable items={recaudacion.desgloseLocal} />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("seccion.evidencia")}</CardTitle>
          <CardDescription>{t("seccion.evidenciaDescripcion")}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 sm:grid-cols-3">
            <figure className="space-y-1">
              <figcaption className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t("campos.firma")}
              </figcaption>
              {evidencia.firma ? (
                <div className="relative aspect-video w-full overflow-hidden rounded-md border bg-white">
                  <Image
                    src={evidencia.firma}
                    alt={t("campos.firma")}
                    fill
                    sizes="(max-width: 640px) 100vw, 33vw"
                    className="object-contain"
                    unoptimized
                  />
                </div>
              ) : (
                <p className="rounded-md border border-dashed p-3 text-center text-xs text-muted-foreground">
                  {t("seccion.sinFirma")}
                </p>
              )}
            </figure>
            <figure className="space-y-1">
              <figcaption className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t("campos.fotoEntradas")}
              </figcaption>
              {evidencia.fotoEntradas ? (
                <div className="relative aspect-video w-full overflow-hidden rounded-md border bg-muted">
                  <Image
                    src={evidencia.fotoEntradas}
                    alt={t("campos.fotoEntradas")}
                    fill
                    sizes="(max-width: 640px) 100vw, 33vw"
                    className="object-cover"
                    unoptimized
                  />
                </div>
              ) : (
                <p className="rounded-md border border-dashed p-3 text-center text-xs text-muted-foreground">
                  {t("seccion.sinFoto")}
                </p>
              )}
            </figure>
            <figure className="space-y-1">
              <figcaption className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t("campos.fotoSalidas")}
              </figcaption>
              {evidencia.fotoSalidas ? (
                <div className="relative aspect-video w-full overflow-hidden rounded-md border bg-muted">
                  <Image
                    src={evidencia.fotoSalidas}
                    alt={t("campos.fotoSalidas")}
                    fill
                    sizes="(max-width: 640px) 100vw, 33vw"
                    className="object-cover"
                    unoptimized
                  />
                </div>
              ) : (
                <p className="rounded-md border border-dashed p-3 text-center text-xs text-muted-foreground">
                  {t("seccion.sinFoto")}
                </p>
              )}
            </figure>
          </div>
          {recaudacion.observaciones ? (
            <div className="bg-muted/30 mt-4 rounded-md border p-3 text-sm">
              <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t("campos.observaciones")}
              </p>
              <p>{recaudacion.observaciones}</p>
            </div>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("seccion.auditoria")}</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-2 sm:grid-cols-2">
            <InfoRow label={t("campos.creada")} value={formatDateTime(recaudacion.createdAt)} />
            <InfoRow
              label={t("campos.actualizada")}
              value={formatDateTime(recaudacion.updatedAt)}
            />
            <InfoRow label={t("campos.idempotencyKey")} value={recaudacion.idempotencyKey} />
            <InfoRow label={t("campos.dispositivo")} value={recaudacion.dispositivoId ?? "—"} />
            {recaudacion.fecha ? (
              <InfoRow label={t("campos.fechaRegistro")} value={formatDate(recaudacion.fecha)} />
            ) : null}
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
