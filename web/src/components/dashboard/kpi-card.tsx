import { TrendingDown, TrendingUp, type LucideIcon } from "lucide-react";

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
}

export function KpiCard({
  icon: Icon,
  title,
  value,
  hint,
  trend,
  trendLabel,
  variant = "default",
}: KpiCardProps) {
  const trendValue =
    typeof trend === "number" ? `${trend > 0 ? "+" : ""}${(trend * 100).toFixed(1)} %` : null;
  const trendIsPositive = typeof trend === "number" && trend > 0;
  const trendIsNegative = typeof trend === "number" && trend < 0;

  return (
    <Card
      className={cn(
        variant === "warning" &&
          "border-amber-300 bg-amber-50 dark:border-amber-900/60 dark:bg-amber-950/30",
        variant === "destructive" &&
          "border-destructive/40 bg-destructive/5 dark:bg-destructive/10",
      )}
    >
      <CardHeader className="flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
        <Icon
          className={cn(
            "size-4",
            variant === "warning" && "text-amber-600",
            variant === "destructive" && "text-destructive",
            variant === "default" && "text-muted-foreground",
          )}
          aria-hidden
        />
      </CardHeader>
      <CardContent>
        <p className="text-2xl font-semibold tabular-nums">{value}</p>
        {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
        {trendValue ? (
          <div
            className={cn(
              "mt-2 flex items-center gap-1 text-xs",
              trendIsPositive && "text-emerald-600 dark:text-emerald-400",
              trendIsNegative && "text-amber-600 dark:text-amber-400",
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
