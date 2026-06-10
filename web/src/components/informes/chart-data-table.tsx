"use client";

import { useTranslations } from "next-intl";

import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

export interface ColumnaTabla {
  key: string;
  label: string;
  /** Alinea a la derecha (cifras numéricas). */
  numerica?: boolean;
}

export interface FilaTabla {
  id: string;
  celdas: Record<string, string>;
}

interface ChartDataTableProps {
  caption: string;
  columnas: ColumnaTabla[];
  filas: FilaTabla[];
}

/**
 * Alternativa accesible a la gráfica: una tabla con los mismos datos,
 * plegada en un `<details>` (nativo, navegable por teclado). Cumple la
 * recomendación de no depender solo de un canvas/svg para transmitir la
 * información.
 */
export function ChartDataTable({ caption, columnas, filas }: ChartDataTableProps) {
  const t = useTranslations("informes.tabla");

  return (
    <details className="mt-4 text-sm">
      <summary className="cursor-pointer rounded-sm text-xs font-medium text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
        {t("ver")}
      </summary>
      <div className="mt-2">
        <Table>
          <TableCaption className="sr-only">{caption}</TableCaption>
          <TableHeader>
            <TableRow>
              {columnas.map((col) => (
                <TableHead key={col.key} className={col.numerica ? "text-right" : undefined}>
                  {col.label}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {filas.map((fila) => (
              <TableRow key={fila.id}>
                {columnas.map((col) => (
                  <TableCell
                    key={col.key}
                    className={col.numerica ? "text-right tabular-nums" : undefined}
                  >
                    {fila.celdas[col.key] ?? "—"}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </details>
  );
}
