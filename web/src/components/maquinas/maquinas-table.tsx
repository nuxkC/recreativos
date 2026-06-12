import Decimal from "decimal.js";
import { ChevronRight, Wrench } from "lucide-react";
import Link from "next/link";
import { useTranslations } from "next-intl";

import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { Maquina } from "@/lib/maquinas/types";

import { EstadoMaquinaBadge } from "./estado-badge";

interface MaquinasTableProps {
  maquinas: Maquina[];
  /** Nº de averías abiertas por máquina (maquinaId → conteo). */
  averiasAbiertas?: Record<string, number>;
}

/**
 * Formatea un importe almacenado como string (preservando precisión
 * Decimal) al formato es-ES: "0,20 €". Centralizar `formatEuros`
 * cuando aparezca el segundo caso de uso (T-35); por ahora vive en
 * línea para no abstraer prematuramente.
 */
function formatValorCredito(valorCredito: string): string {
  try {
    const dec = new Decimal(valorCredito);
    return `${dec.toFixed(2).replace(".", ",")} €`;
  } catch {
    return `${valorCredito} €`;
  }
}

export function MaquinasTable({ maquinas, averiasAbiertas = {} }: MaquinasTableProps) {
  const t = useTranslations("maquinas");
  const tAverias = useTranslations("averias");

  if (maquinas.length === 0) {
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
            <TableHead>{t("campos.numeroSerie")}</TableHead>
            <TableHead>{t("campos.modelo")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.fabricante")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.valorCredito")}</TableHead>
            <TableHead>{t("campos.estado")}</TableHead>
            <TableHead className="w-12">
              <span className="sr-only">{t("accion.abrir")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {maquinas.map((maquina) => (
            <TableRow key={maquina.id} className="hover:bg-accent/40">
              <TableCell className="font-medium">
                <Link href={`/maquinas/${maquina.id}`} className="block hover:underline">
                  {maquina.numeroSerie}
                </Link>
              </TableCell>
              <TableCell className="text-muted-foreground">{maquina.modelo ?? "—"}</TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">
                {maquina.fabricante ?? "—"}
              </TableCell>
              <TableCell className="hidden tabular-nums text-muted-foreground md:table-cell">
                {formatValorCredito(maquina.valorCredito)}
              </TableCell>
              <TableCell>
                <div className="flex flex-wrap items-center gap-1.5">
                  <EstadoMaquinaBadge estado={maquina.estado} />
                  {averiasAbiertas[maquina.id] ? (
                    <Badge variant="warning" className="gap-1">
                      <Wrench className="size-3" aria-hidden />
                      {tAverias("etiqueta.abiertas", { count: averiasAbiertas[maquina.id] })}
                    </Badge>
                  ) : null}
                </div>
              </TableCell>
              <TableCell>
                <Link
                  href={`/maquinas/${maquina.id}`}
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
