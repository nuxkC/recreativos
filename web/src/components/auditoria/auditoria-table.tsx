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
import type { EventoAuditoria } from "@/lib/auditoria/types";
import { isEntidadAuditoria } from "@/lib/auditoria/types";
import { formatDateTime } from "@/lib/recaudaciones/format";

interface AuditoriaTableProps {
  eventos: EventoAuditoria[];
}

export function AuditoriaTable({ eventos }: AuditoriaTableProps) {
  const t = useTranslations("auditoria");

  if (eventos.length === 0) {
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
            <TableHead>{t("campos.accion")}</TableHead>
            <TableHead className="hidden md:table-cell">{t("campos.entidad")}</TableHead>
            <TableHead className="hidden lg:table-cell">{t("campos.actor")}</TableHead>
            <TableHead className="hidden xl:table-cell">{t("campos.datos")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {eventos.map((evento) => (
            <TableRow key={evento.id} className="hover:bg-accent/40">
              <TableCell className="whitespace-nowrap font-medium">
                {formatDateTime(evento.createdAt)}
              </TableCell>
              <TableCell>
                <Badge variant="secondary">{t(`acciones.${evento.accion}`)}</Badge>
              </TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">
                <span>
                  {isEntidadAuditoria(evento.entidadTabla)
                    ? t(`entidades.${evento.entidadTabla}`)
                    : evento.entidadTabla}
                </span>
                {evento.entidadId ? (
                  <span className="text-foreground/50 block font-mono text-xs">
                    {evento.entidadId.slice(0, 8)}
                  </span>
                ) : null}
              </TableCell>
              <TableCell className="hidden text-muted-foreground lg:table-cell">
                {evento.actorNombre ?? (evento.actorUsuarioId ? "—" : t("sistema"))}
              </TableCell>
              <TableCell className="hidden xl:table-cell">
                <code className="block max-w-md truncate text-xs text-muted-foreground">
                  {resumenDatos(evento.datos)}
                </code>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function resumenDatos(datos: Record<string, unknown>): string {
  const entradas = Object.entries(datos);
  if (entradas.length === 0) return "—";
  return entradas.map(([clave, valor]) => `${clave}: ${formatValor(valor)}`).join(" · ");
}

function formatValor(valor: unknown): string {
  if (valor === null) return "∅";
  if (typeof valor === "object") return JSON.stringify(valor);
  return String(valor);
}
