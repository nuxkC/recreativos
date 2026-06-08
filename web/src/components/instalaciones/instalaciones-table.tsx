import { format, parseISO } from "date-fns";
import { es } from "date-fns/locale";
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
import type { Instalacion } from "@/lib/instalaciones/types";

import { EstadoInstalacionBadge } from "./estado-badge";

interface InstalacionesTableProps {
  instalaciones: Instalacion[];
}

function formatDate(iso: string | null): string {
  if (!iso) return "—";
  try {
    return format(parseISO(iso), "dd/MM/yyyy", { locale: es });
  } catch {
    return iso;
  }
}

export function InstalacionesTable({ instalaciones }: InstalacionesTableProps) {
  const t = useTranslations("instalaciones");

  if (instalaciones.length === 0) {
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
            <TableHead>{t("campos.maquina")}</TableHead>
            <TableHead>{t("campos.local")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.licencia")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.fechaInicio")}</TableHead>
            <TableHead className="hidden lg:table-cell">{t("campos.fechaFin")}</TableHead>
            <TableHead>{t("campos.estado")}</TableHead>
            <TableHead className="w-12">
              <span className="sr-only">{t("accion.abrir")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {instalaciones.map((inst) => (
            <TableRow key={inst.id} className="hover:bg-accent/40">
              <TableCell className="font-medium">
                <Link href={`/instalaciones/${inst.id}`} className="block hover:underline">
                  {inst.maquina?.numeroSerie ?? "—"}
                </Link>
                {inst.maquina?.modelo ? (
                  <span className="text-xs text-muted-foreground">{inst.maquina.modelo}</span>
                ) : null}
              </TableCell>
              <TableCell className="text-muted-foreground">{inst.local?.nombre ?? "—"}</TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">
                {inst.licencia?.numero ?? "—"}
              </TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">
                {formatDate(inst.fechaInicio)}
              </TableCell>
              <TableCell className="hidden text-muted-foreground lg:table-cell">
                {formatDate(inst.fechaFin)}
              </TableCell>
              <TableCell>
                <EstadoInstalacionBadge estado={inst.estado} />
              </TableCell>
              <TableCell>
                <Link
                  href={`/instalaciones/${inst.id}`}
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
