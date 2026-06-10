import Decimal from "decimal.js";
import { useTranslations } from "next-intl";

import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatEur } from "@/lib/recaudaciones/format";
import type { DenominacionItem } from "@/lib/recaudaciones/types";

interface DesgloseTableProps {
  items: DenominacionItem[];
}

function subtotal(item: DenominacionItem): string {
  return new Decimal(item.denominacion).times(item.cantidad).toFixed(2);
}

function totalDe(items: DenominacionItem[]): string {
  return items
    .reduce(
      (acc, item) => acc.plus(new Decimal(item.denominacion).times(item.cantidad)),
      new Decimal(0),
    )
    .toFixed(2);
}

export function DesgloseTable({ items }: DesgloseTableProps) {
  const t = useTranslations("recaudaciones.desglose");
  if (items.length === 0) {
    return (
      <p className="rounded-md border border-dashed p-3 text-center text-xs text-muted-foreground">
        {t("vacio")}
      </p>
    );
  }
  // Mostramos las denominaciones de mayor a menor importe.
  const ordered = [...items].sort((a, b) => b.denominacion - a.denominacion);
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t("denominacion")}</TableHead>
          <TableHead className="text-right">{t("cantidad")}</TableHead>
          <TableHead className="text-right">{t("subtotal")}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {ordered.map((item) => (
          <TableRow key={item.denominacion}>
            <TableCell className="tabular-nums">{formatEur(String(item.denominacion))}</TableCell>
            <TableCell className="text-right tabular-nums">{item.cantidad}</TableCell>
            <TableCell className="text-right tabular-nums">{formatEur(subtotal(item))}</TableCell>
          </TableRow>
        ))}
      </TableBody>
      <TableFooter>
        <TableRow>
          <TableCell colSpan={2} className="text-right font-medium">
            {t("total")}
          </TableCell>
          <TableCell className="text-right font-semibold tabular-nums">
            {formatEur(totalDe(items))}
          </TableCell>
        </TableRow>
      </TableFooter>
    </Table>
  );
}
