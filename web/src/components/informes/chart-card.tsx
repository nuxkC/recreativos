"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

interface ChartCardProps {
  title: string;
  description: string;
  /** Cuando no hay datos, muestra el EmptyState en lugar de la gráfica. */
  isEmpty: boolean;
  children: ReactNode;
}

/**
 * Tarjeta contenedora de una gráfica. Centraliza cabecera y estado vacío
 * para las tres visualizaciones de informes.
 */
export function ChartCard({ title, description, isEmpty, children }: ChartCardProps) {
  const t = useTranslations("informes");

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        {isEmpty ? (
          <p className="rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground">
            {t("vacio")}
          </p>
        ) : (
          children
        )}
      </CardContent>
    </Card>
  );
}
