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
import type { CambioPlaca } from "@/lib/cambios-placa/types";
import { formatDateTime } from "@/lib/recaudaciones/format";

interface CambiosPlacaTableProps {
  cambios: CambioPlaca[];
}

export function CambiosPlacaTable({ cambios }: CambiosPlacaTableProps) {
  const t = useTranslations("cambiosPlaca");

  if (cambios.length === 0) {
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
            <TableHead className="hidden lg:table-cell">{t("campos.usuario")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.placas")}</TableHead>
            <TableHead className="w-12">
              <span className="sr-only">{t("accion.abrir")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {cambios.map((c) => (
            <TableRow key={c.id} className="hover:bg-accent/40">
              <TableCell className="font-medium">
                <Link href={`/cambios-placa/${c.id}`} className="block hover:underline">
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
              <TableCell className="hidden text-muted-foreground lg:table-cell">
                {c.usuario?.nombreCompleto ?? "—"}
              </TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">
                {c.numeroSeriePlacaAnterior || c.numeroSeriePlacaNueva ? (
                  <span>
                    {c.numeroSeriePlacaAnterior ?? "—"}{" "}
                    <span className="text-foreground/50">→</span> {c.numeroSeriePlacaNueva ?? "—"}
                  </span>
                ) : (
                  "—"
                )}
              </TableCell>
              <TableCell>
                <Link
                  href={`/cambios-placa/${c.id}`}
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
