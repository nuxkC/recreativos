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
import type { Local } from "@/lib/locales/types";

interface LocalesTableProps {
  locales: Local[];
}

export function LocalesTable({ locales }: LocalesTableProps) {
  const t = useTranslations("locales");

  if (locales.length === 0) {
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
                <Link href={`/locales/${local.id}`} className="block hover:underline">
                  {local.nombre}
                </Link>
              </TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">
                {local.direccion ?? "—"}
              </TableCell>
              <TableCell className="hidden text-muted-foreground lg:table-cell">
                {local.titularNombre ?? "—"}
              </TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">
                {local.telefono ?? "—"}
              </TableCell>
              <TableCell>
                <Link
                  href={`/locales/${local.id}`}
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
