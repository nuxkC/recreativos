import { ArrowUpRight, TrendingDown, TrendingUp, type LucideIcon } from "lucide-react";
import Link from "next/link";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface KpiCardProps {
  icon: LucideIcon;
  title: string;
  value: string;
  hint?: string;
  /** Variación numérica (-1..N). Positivo verde, negativo ámbar, 0 neutro. */
  trend?: number | null;
  /** Texto que acompaña al trend (ej. "vs. mes anterior"). */
  trendLabel?: string;
  /** Variante semántica: usa fondo coloreado para destacar alertas. */
  variant?: "default" | "warning" | "destructive";
  /** Si se pasa, toda la tarjeta es un deep-link a la lista filtrada (T-12). */
  href?: string;
  /** Nombre accesible del enlace (por defecto el título). */
  ariaLabel?: string;
}

export function KpiCard({
  icon: Icon,
  title,
  value,
  hint,
  trend,
  trendLabel,
  variant = "default",
  href,
  ariaLabel,
}: KpiCardProps) {
  const trendValue =
    typeof trend === "number" ? `${trend > 0 ? "+" : ""}${(trend * 100).toFixed(1)} %` : null;
  const trendIsPositive = typeof trend === "number" && trend > 0;
  const trendIsNegative = typeof trend === "number" && trend < 0;

  return (
    <Card
      className={cn(
        "relative",
        href && "group hover:border-border-strong transition-colors",
        variant === "warning" && "border-warning/40 bg-warning-subtle",
        variant === "destructive" &&
          "border-destructive/40 bg-destructive/5 dark:bg-destructive/10",
      )}
    >
      {/* Enlace estirado: toda la tarjeta es el destino, sin envolver el
          contenido (evita anidar interactivos y preserva el layout). */}
      {href ? (
        <Link
          href={href}
          aria-label={ariaLabel ?? title}
          className="focus-visible:ring-ring absolute inset-0 z-10 rounded-lg focus-visible:ring-2 focus-visible:outline-hidden"
        />
      ) : null}
      <CardHeader className="flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-muted-foreground text-sm font-medium">{title}</CardTitle>
        <span className="relative inline-flex size-4 items-center justify-center">
          <Icon
            className={cn(
              "size-4 transition-opacity",
              href && "group-hover:opacity-0",
              variant === "warning" && "text-warning",
              variant === "destructive" && "text-destructive",
              variant === "default" && "text-muted-foreground",
            )}
            aria-hidden
          />
          {href ? (
            <ArrowUpRight
              className="text-muted-foreground absolute size-4 opacity-0 transition-opacity group-hover:opacity-100"
              aria-hidden
            />
          ) : null}
        </span>
      </CardHeader>
      <CardContent>
        <p className="text-2xl font-semibold tabular-nums">{value}</p>
        {hint ? <p className="text-muted-foreground text-xs">{hint}</p> : null}
        {trendValue ? (
          <div
            className={cn(
              "mt-2 flex items-center gap-1 text-xs",
              trendIsPositive && "text-success-text",
              trendIsNegative && "text-warning-text",
              !trendIsPositive && !trendIsNegative && "text-muted-foreground",
            )}
          >
            {trendIsPositive ? (
              <TrendingUp className="size-3" aria-hidden />
            ) : trendIsNegative ? (
              <TrendingDown className="size-3" aria-hidden />
            ) : null}
            <span className="tabular-nums">{trendValue}</span>
            {trendLabel ? <span className="text-muted-foreground">{trendLabel}</span> : null}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
