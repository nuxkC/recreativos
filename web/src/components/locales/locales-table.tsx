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
import { formatearDireccion } from "@/lib/locales/direccion";
import type { Local } from "@/lib/locales/types";

interface LocalesTableProps {
  locales: Local[];
}

export function LocalesTable({ locales }: LocalesTableProps) {
  const t = useTranslations("locales");

  if (locales.length === 0) {
    return (
      <div className="text-muted-foreground rounded-md border border-dashed p-8 text-center text-sm">
        {t("vacio")}
      </div>
    );
  }

  return (
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("campos.nombre")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.direccion")}</TableHead>
            <TableHead className="hidden lg:table-cell">{t("campos.titularNombre")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.telefono")}</TableHead>
            <TableHead className="w-12">
              <span className="sr-only">{t("accion.abrir")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {locales.map((local) => (
            <TableRow key={local.id} className="hover:bg-accent/40">
              <TableCell className="font-medium">
                <Link
                  href={`/locales/${local.id}`}
                  className="block hover:underline"
                  // T-244: par compartido con el <h1> del detalle (morph del nombre).
                  style={{ viewTransitionName: `local-name-${local.id}` }}
                >
                  {local.nombre}
                </Link>
              </TableCell>
              <TableCell className="text-muted-foreground hidden md:table-cell">
                {formatearDireccion(local) ?? "—"}
              </TableCell>
              <TableCell className="text-muted-foreground hidden lg:table-cell">
                {local.titularNombre ?? "—"}
              </TableCell>
              <TableCell className="text-muted-foreground hidden md:table-cell">
                {local.telefono ?? "—"}
              </TableCell>
              <TableCell>
                <Link
                  href={`/locales/${local.id}`}
                  aria-label={t("accion.abrir")}
                  className="text-muted-foreground hover:text-foreground inline-flex items-center"
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
