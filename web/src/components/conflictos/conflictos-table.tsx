import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { useTranslations } from "next-intl";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDateTime, formatEur } from "@/lib/recaudaciones/format";
import type { Recaudacion } from "@/lib/recaudaciones/types";

interface ConflictosTableProps {
  conflictos: Recaudacion[];
}

export function ConflictosTable({ conflictos }: ConflictosTableProps) {
  const t = useTranslations("conflictos");
  const tRec = useTranslations("recaudaciones");

  if (conflictos.length === 0) {
    return (
      <div className="rounded-md border border-dashed p-8 text-center text-sm text-muted-foreground">
        {t("vacio")}
      </div>
    );
  }

  return (
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{tRec("campos.fecha")}</TableHead>
            <TableHead>{tRec("campos.maquina")}</TableHead>
            <TableHead className="hidden md:table-cell">{tRec("campos.local")}</TableHead>
            <TableHead className="text-right tabular-nums">{t("campos.brutoCliente")}</TableHead>
            <TableHead className="text-right tabular-nums">{t("campos.brutoServidor")}</TableHead>
            <TableHead className="text-right tabular-nums">{t("campos.diferencia")}</TableHead>
            <TableHead className="w-12">
              <span className="sr-only">{tRec("accion.abrir")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {conflictos.map((c) => {
            const cliente = Number(c.recaudacionBruta);
            const servidor = c.brutoRecalculado ? Number(c.brutoRecalculado) : null;
            const diferencia =
              servidor !== null && Number.isFinite(cliente) ? servidor - cliente : null;
            const signo =
              diferencia === null ? "" : diferencia > 0 ? "+" : diferencia < 0 ? "" : "";
            return (
              <TableRow key={c.id} className="hover:bg-accent/40">
                <TableCell className="font-medium">
                  <Link href={`/recaudaciones/${c.id}`} className="block hover:underline">
                    {formatDateTime(c.fecha)}
                  </Link>
                </TableCell>
                <TableCell>
                  <span className="font-medium">{c.instalacion?.maquina?.numeroSerie ?? "—"}</span>
                  {c.instalacion?.maquina?.modelo ? (
                    <span className="block text-xs text-muted-foreground">
                      {c.instalacion.maquina.modelo}
                    </span>
                  ) : null}
                </TableCell>
                <TableCell className="hidden text-muted-foreground md:table-cell">
                  {c.instalacion?.local?.nombre ?? "—"}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatEur(c.recaudacionBruta)}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatEur(c.brutoRecalculado)}
                </TableCell>
                <TableCell
                  className={
                    diferencia === null
                      ? "text-right tabular-nums text-muted-foreground"
                      : diferencia > 0
                        ? "text-right tabular-nums text-emerald-600 dark:text-emerald-400"
                        : diferencia < 0
                          ? "text-right tabular-nums text-amber-600 dark:text-amber-400"
                          : "text-right tabular-nums"
                  }
                >
                  {diferencia === null ? "—" : `${signo}${formatEur(diferencia.toFixed(2))}`}
                </TableCell>
                <TableCell>
                  <Link
                    href={`/recaudaciones/${c.id}`}
                    aria-label={tRec("accion.abrir")}
                    className="inline-flex items-center text-muted-foreground hover:text-foreground"
                  >
                    <ChevronRight className="size-4" aria-hidden />
                  </Link>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
