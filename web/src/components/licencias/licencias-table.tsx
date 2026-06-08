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
import type { Licencia } from "@/lib/licencias/types";

import { EstadoLicenciaBadge } from "./estado-badge";

interface LicenciasTableProps {
  licencias: Licencia[];
}

function formatDate(iso: string | null): string {
  if (!iso) return "—";
  try {
    return format(parseISO(iso), "dd/MM/yyyy", { locale: es });
  } catch {
    return iso;
  }
}

export function LicenciasTable({ licencias }: LicenciasTableProps) {
  const t = useTranslations("licencias");

  if (licencias.length === 0) {
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
            <TableHead>{t("campos.numero")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.tipo")}</TableHead>
            <TableHead className="hidden lg:table-cell">{t("campos.comunidadAutonoma")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.fechaCaducidad")}</TableHead>
            <TableHead>{t("campos.estado")}</TableHead>
            <TableHead className="w-12">
              <span className="sr-only">{t("accion.abrir")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {licencias.map((licencia) => (
            <TableRow key={licencia.id} className="hover:bg-accent/40">
              <TableCell className="font-medium">
                <Link href={`/licencias/${licencia.id}`} className="block hover:underline">
                  {licencia.numero}
                </Link>
              </TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">
                {licencia.tipo ?? "—"}
              </TableCell>
              <TableCell className="hidden text-muted-foreground lg:table-cell">
                {licencia.comunidadAutonoma ?? "—"}
              </TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">
                {formatDate(licencia.fechaCaducidad)}
              </TableCell>
              <TableCell>
                <EstadoLicenciaBadge estado={licencia.estado} />
              </TableCell>
              <TableCell>
                <Link
                  href={`/licencias/${licencia.id}`}
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
