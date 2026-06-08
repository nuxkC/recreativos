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

import { EstadoRecaudacionBadges } from "./estado-badge";

interface RecaudacionesTableProps {
  recaudaciones: Recaudacion[];
}

export function RecaudacionesTable({ recaudaciones }: RecaudacionesTableProps) {
  const t = useTranslations("recaudaciones");

  if (recaudaciones.length === 0) {
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
            <TableHead>{t("campos.fecha")}</TableHead>
            <TableHead>{t("campos.maquina")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.local")}</TableHead>
            <TableHead className="hidden lg:table-cell">{t("campos.tecnico")}</TableHead>
            <TableHead className="text-right tabular-nums">{t("campos.bruto")}</TableHead>
            <TableHead className="hidden text-right tabular-nums md:table-cell">
              {t("campos.parteLocal")}
            </TableHead>
            <TableHead>{t("campos.estado")}</TableHead>
            <TableHead className="w-12">
              <span className="sr-only">{t("accion.abrir")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {recaudaciones.map((rec) => (
            <TableRow key={rec.id} className="hover:bg-accent/40">
              <TableCell className="font-medium">
                <Link href={`/recaudaciones/${rec.id}`} className="block hover:underline">
                  {formatDateTime(rec.fecha)}
                </Link>
              </TableCell>
              <TableCell>
                <span className="font-medium">{rec.instalacion?.maquina?.numeroSerie ?? "—"}</span>
                {rec.instalacion?.maquina?.modelo ? (
                  <span className="block text-xs text-muted-foreground">
                    {rec.instalacion.maquina.modelo}
                  </span>
                ) : null}
              </TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">
                {rec.instalacion?.local?.nombre ?? "—"}
              </TableCell>
              <TableCell className="hidden text-muted-foreground lg:table-cell">
                {rec.tecnico?.nombreCompleto ?? "—"}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {formatEur(rec.recaudacionBruta)}
              </TableCell>
              <TableCell className="hidden text-right tabular-nums md:table-cell">
                {formatEur(rec.parteLocal)}
              </TableCell>
              <TableCell>
                <EstadoRecaudacionBadges
                  estado={rec.estado}
                  conflicto={rec.conflicto}
                  conflictoResuelto={rec.revisadoEn !== null}
                />
              </TableCell>
              <TableCell>
                <Link
                  href={`/recaudaciones/${rec.id}`}
                  aria-label={t("accion.abrir")}
                  className="inline-flex items-center text-muted-foreground hover:text-foreground"
                >
                  <ChevronRight className="size-4" aria-hidden />
                </Link>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
