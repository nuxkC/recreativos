"use client";

import { CloudOff } from "lucide-react";

import { cn } from "@/lib/utils";

export interface OfflineBadgeProps {
  /** Horas desde última sincronización. Si está presente, muestra "· Xh" */
  staleHours?: number;
  /** Callback opcional: abre pantalla Sincronizar */
  onClick?: () => void;
  /** Clases Tailwind adicionales */
  className?: string;
}

/**
 * OfflineBadge: indicador discreto y persistente de "sin conexión / trabajando offline".
 *
 * Comunica que la app está operando contra la cola local y los datos pueden no estar
 * sincronizados, SIN alarmar. Es neutro (rol `muted`), no un error.
 *
 * Anatomía:
 * - Dot pulsante (Ø 8px, color muted, opacidad respirando 0.4↔1.0)
 * - Icono CloudOff (16px, color muted)
 * - Label texto "Sin conexión" o "Sin conexión · Xh" (caption 12px/500)
 *
 * Estados:
 * - default (offline): visible con dot+icono+label en muted, dot pulsa lento (2000ms)
 * - online: no se renderiza
 * - reduced-motion: dot estático a opacidad ~0.85, sin pulse
 *
 * A11y:
 * - role="status" aria-live="polite" anuncia el cambio UNA vez
 * - aria-label descriptivo (completo cuando no hay label visible)
 * - Icono y dot marcados aria-hidden (decorativos)
 * - Contraste: text-muted-foreground cumple AA (4.5:1+) sobre TopBar
 * - Si tappable: min-h-11 (44px) táctil, focus-visible:ring-2
 */
export function OfflineBadge({ staleHours, onClick, className }: OfflineBadgeProps) {
  const label = staleHours ? `Sin conexión · ${staleHours}h` : "Sin conexión";

  const ariaLabel = staleHours ? `Sin sincronizar desde hace ${staleHours} horas` : "Sin conexión";

  return (
    <div
      role="status"
      aria-live="polite"
      aria-label={ariaLabel}
      onClick={onClick}
      className={cn(
        // Layout: pill horizontal, altura baja emfasis (h-6 = 24px)
        "inline-flex h-6 items-center gap-1.5 rounded-full px-2",
        // Tipografía y color: rol neutro muted, NUNCA danger/warning
        "text-muted-foreground",
        // Interactividad (si tappable)
        onClick && [
          "cursor-pointer",
          "min-h-11", // ≥44px área táctil para WCAG 2.5.5 (target 44px)
          "focus-visible:outline-none",
          "focus-visible:ring-2",
          "focus-visible:ring-ring", // anillo de foco primary
        ],
        className,
      )}
    >
      {/* Dot pulsante: círculo relleno que respira (opacidad 0.4→1.0 en 2000ms).
          - Marcado aria-hidden porque es decorativo (el icono + texto comunica).
          - Con motion-reduce: opacidad fija ~0.85, sin pulse.
          - Tinte heredado de text-muted-foreground (bg-current).
      */}
      <span
        aria-hidden="true"
        className="size-2 rounded-full bg-current motion-safe:animate-pulse motion-reduce:opacity-85"
      />

      {/* Icono CloudOff (16px, Lucide). Decorativo: aria-hidden.
          Comunica "sin nube/sin sync", NUNCA error/aviso (no triángulo, no rayo). */}
      <CloudOff
        aria-hidden="true"
        className="size-4" // 16px
      />

      {/* Label: "Sin conexión" o "Sin conexión · Xh".
          - caption 12px/500 (text-xs font-medium)
          - Una sola línea, no truncar (regla 2: no solo-color, siempre icono+texto)
      */}
      <span className="whitespace-nowrap text-xs font-medium">{label}</span>
    </div>
  );
}
