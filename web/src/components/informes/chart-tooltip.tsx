"use client";

import { formatEuros } from "@/lib/informes/format";

interface TooltipPayloadEntry {
  name?: string;
  value?: number | string;
  color?: string;
}

interface ChartTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadEntry[];
  label?: string;
  /** Transforma la etiqueta del eje (p. ej. ISO mes → "enero de 2026"). */
  labelFormatter?: (label: string) => string;
}

/**
 * Tooltip de Recharts con formato de euros es-ES y estilos del tema.
 * Recharts inyecta `active`, `payload` y `label` en tiempo de ejecución.
 */
export function ChartTooltip({ active, payload, label, labelFormatter }: ChartTooltipProps) {
  if (!active || !payload || payload.length === 0) return null;

  const titulo = label ? (labelFormatter ? labelFormatter(label) : label) : null;

  return (
    <div className="rounded-md border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
      {titulo ? <p className="mb-1 font-medium">{titulo}</p> : null}
      <ul className="space-y-0.5">
        {payload.map((entry, index) => (
          <li key={`${entry.name ?? "serie"}-${index}`} className="flex items-center gap-2">
            <span
              aria-hidden
              className="inline-block size-2 shrink-0 rounded-full"
              style={{ backgroundColor: entry.color }}
            />
            <span className="text-muted-foreground">{entry.name}:</span>
            <span className="font-medium tabular-nums">{formatEuros(Number(entry.value))}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
