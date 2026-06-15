"use client";

import NumberFlow from "@number-flow/react";
import { ArrowUpRight, Coins, TrendingDown, TrendingUp } from "lucide-react";
import Link from "next/link";
import * as React from "react";

import { Sparkline } from "@/components/common/sparkline";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface HeroRecaudacionProps {
  /**
   * Recaudación bruta del mes (€). Es un número de AGREGACIÓN para presentación
   * (como el resto de KPIs del dashboard), no una cifra-SSOT: el `aria-label`
   * lleva el importe money-safe ya formateado para el lector de pantalla.
   */
  bruto: number;
  /** Importe money-safe formateado (es-ES) para el nombre accesible. */
  brutoAccesible: string;
  /** Serie mensual (coordenadas del sparkline, NUNCA fuente de cifra). */
  serie: number[];
  /** Variación vs mes anterior (-1..N) o null si no hay base. */
  variacion: number | null;
  titulo: string;
  hintRecuento: string;
  vsLabel: string;
  href: string;
}

// Formato es-ES «1.234,56 €» para el count-up. Inline (sin anotar como
// `Intl.NumberFormatOptions`) para que el literal "currency" no se ensanche a
// `string` y encaje en el tipo `Format` que espera NumberFlow.
const FORMATO_EUR = {
  style: "currency",
  currency: "EUR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
} as const;

/**
 * KPI héroe del dashboard bento (T-238, T-8): la recaudación del mes como
 * dato-héroe — cifra grande tabular con count-up, sparkline `primary` de
 * tendencia y variación vs. mes anterior. Toda la tarjeta es un deep-link a
 * Recaudaciones (T-12).
 *
 * El count-up anima 0 → total al montar; `NumberFlow` respeta
 * `prefers-reduced-motion` (no anima y muestra el total directamente). La capa
 * de animación es decorativa (`aria-hidden`): el nombre accesible lo aporta el
 * `aria-label` del enlace con el importe money-safe.
 */
export function HeroRecaudacion({
  bruto,
  brutoAccesible,
  serie,
  variacion,
  titulo,
  hintRecuento,
  vsLabel,
  href,
}: HeroRecaudacionProps) {
  const [valor, setValor] = React.useState(0);
  React.useEffect(() => {
    setValor(bruto);
  }, [bruto]);

  const trendValue =
    typeof variacion === "number"
      ? `${variacion > 0 ? "+" : ""}${(variacion * 100).toFixed(1)} %`
      : null;
  const trendUp = typeof variacion === "number" && variacion > 0;
  const trendDown = typeof variacion === "number" && variacion < 0;

  return (
    <Card className="group relative overflow-hidden sm:col-span-2 sm:row-span-2">
      <Link
        href={href}
        aria-label={`${titulo}: ${brutoAccesible}. ${hintRecuento}`}
        className="flex h-full flex-col gap-4 rounded-lg p-6 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <div className="flex items-center justify-between">
          <span className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
            <Coins className="size-4" aria-hidden="true" />
            {titulo}
          </span>
          <ArrowUpRight
            className="size-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100"
            aria-hidden="true"
          />
        </div>

        <NumberFlow
          value={valor}
          format={FORMATO_EUR}
          locales="es-ES"
          willChange
          aria-hidden="true"
          className="font-mono text-4xl font-semibold tabular-nums sm:text-5xl"
        />

        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
          {trendValue ? (
            <span
              className={cn(
                "inline-flex items-center gap-1 tabular-nums",
                trendUp && "text-success-text",
                trendDown && "text-warning-text",
                !trendUp && !trendDown && "text-muted-foreground",
              )}
            >
              {trendUp ? (
                <TrendingUp className="size-3" aria-hidden="true" />
              ) : trendDown ? (
                <TrendingDown className="size-3" aria-hidden="true" />
              ) : null}
              {trendValue}
              <span className="text-muted-foreground">{vsLabel}</span>
            </span>
          ) : null}
          <span className="text-muted-foreground">· {hintRecuento}</span>
        </div>

        <div className="-mx-6 -mb-6 mt-auto">
          <Sparkline data={serie} role="primary" height={64} showArea />
        </div>
      </Link>
    </Card>
  );
}
