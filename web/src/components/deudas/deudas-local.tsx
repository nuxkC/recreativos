import { getTranslations } from "next-intl/server";

import { CondonarCredito } from "@/components/deudas/condonar-credito";
import { NuevoPrestamo } from "@/components/deudas/nuevo-prestamo";
import { PorcentajeRecuperacionLocal } from "@/components/deudas/porcentaje-recuperacion-local";
import { RegistrarPago } from "@/components/deudas/registrar-pago";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { CreditoLocal, LocalSaldo, Recuperacion } from "@/lib/deudas/types";
import { formatDate, formatEur } from "@/lib/recaudaciones/format";

interface DeudasLocalProps {
  localId: string;
  saldo: LocalSaldo | null;
  creditos: CreditoLocal[];
  recuperaciones: Recuperacion[];
  porcentajeEmpresa: number;
  porcentajeLocal: number | null;
  esAdmin: boolean;
}

function SaldoItem({
  label,
  value,
  emphasis,
}: {
  label: string;
  value: string;
  emphasis?: boolean;
}) {
  return (
    <div className="space-y-0.5">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={emphasis ? "text-xl font-semibold tabular-nums" : "text-base tabular-nums"}>
        {value}
      </p>
    </div>
  );
}

export async function DeudasLocal({
  localId,
  saldo,
  creditos,
  recuperaciones,
  porcentajeEmpresa,
  porcentajeLocal,
  esAdmin,
}: DeudasLocalProps) {
  const t = await getTranslations("deudas");

  const abiertas = creditos.filter((c) => c.estado === "abierto");
  const cerradas = creditos.filter((c) => c.estado !== "abierto");
  const numDeudas = saldo?.numDeudasAbiertas ?? abiertas.length;

  return (
    <>
      <Card>
        <CardHeader className="flex-row items-start justify-between gap-4 space-y-0">
          <div className="space-y-1">
            <CardTitle className="text-lg">{t("titulo")}</CardTitle>
            <CardDescription>{t("descripcion")}</CardDescription>
          </div>
          <NuevoPrestamo localId={localId} />
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Resumen de saldo --------------------------------------------- */}
          <div className="grid grid-cols-1 gap-4 rounded-md border p-4 sm:grid-cols-3">
            <SaldoItem
              label={t("saldo.total")}
              value={formatEur(saldo?.saldoTotal ?? "0")}
              emphasis
            />
            <SaldoItem label={t("saldo.tolva")} value={formatEur(saldo?.saldoTolva ?? "0")} />
            <SaldoItem label={t("saldo.prestamo")} value={formatEur(saldo?.saldoPrestamo ?? "0")} />
          </div>

          {/* Deudas abiertas --------------------------------------------- */}
          {abiertas.length === 0 ? (
            <p className="rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground">
              {t("deuda.sinDeudas")}
            </p>
          ) : (
            <ul className="divide-y rounded-md border">
              {abiertas.map((c) => (
                <li key={c.id} className="flex flex-wrap items-center justify-between gap-3 p-3">
                  <div className="min-w-0 space-y-1">
                    <div className="flex items-center gap-2">
                      <Badge variant="outline">{t(`tipo.${c.tipo}`)}</Badge>
                      <span className="text-sm font-medium tabular-nums">{formatEur(c.saldo)}</span>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {t("deuda.principal")}: {formatEur(c.principal)} · {formatDate(c.fecha)}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <RegistrarPago creditoId={c.id} localId={localId} saldo={c.saldo} />
                    {esAdmin ? <CondonarCredito creditoId={c.id} localId={localId} /> : null}
                  </div>
                </li>
              ))}
            </ul>
          )}

          {/* Histórico de deudas cerradas/condonadas --------------------- */}
          {cerradas.length > 0 ? (
            <div className="space-y-2">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t("deuda.historico")}
              </p>
              <ul className="divide-y rounded-md border text-sm text-muted-foreground">
                {cerradas.map((c) => (
                  <li key={c.id} className="flex items-center justify-between gap-3 p-3">
                    <span className="flex items-center gap-2">
                      <Badge variant="outline">{t(`tipo.${c.tipo}`)}</Badge>
                      {t(`estado.${c.estado}`)}
                    </span>
                    <span className="tabular-nums">
                      {formatEur(c.principal)} · {formatDate(c.fecha)}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

          {/* % de recuperación automática (override del local) ----------- */}
          <div className="space-y-3 rounded-md border p-4">
            <div>
              <h3 className="text-sm font-medium">{t("porcentaje.titulo")}</h3>
              <p className="text-sm text-muted-foreground">{t("porcentaje.descripcion")}</p>
            </div>
            <PorcentajeRecuperacionLocal
              localId={localId}
              valorLocal={porcentajeLocal}
              valorEmpresa={porcentajeEmpresa}
            />
          </div>

          <p className="text-xs text-muted-foreground">
            {t("saldo.numDeudas", { count: numDeudas })}
          </p>
        </CardContent>
      </Card>

      {/* Libro mayor ---------------------------------------------------- */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">{t("ledger.titulo")}</CardTitle>
          <CardDescription>{t("ledger.descripcion")}</CardDescription>
        </CardHeader>
        <CardContent>
          {recuperaciones.length === 0 ? (
            <p className="rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground">
              {t("ledger.vacio")}
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("ledger.fecha")}</TableHead>
                  <TableHead>{t("ledger.tipo")}</TableHead>
                  <TableHead>{t("ledger.origen")}</TableHead>
                  <TableHead className="text-right">{t("ledger.importe")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {recuperaciones.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="tabular-nums">{formatDate(r.fecha)}</TableCell>
                    <TableCell>{r.tipoCredito ? t(`tipo.${r.tipoCredito}`) : "—"}</TableCell>
                    <TableCell>{t(`origen.${r.origen}`)}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatEur(r.importe)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </>
  );
}
