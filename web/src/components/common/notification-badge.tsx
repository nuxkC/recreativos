import { cn } from "@/lib/utils";

export type NotificationBadgeRol = "primary" | "danger" | "neutral";

/**
 * Pares texto/fondo verificados (WCAG). danger-foreground es blanco en light y
 * oscuro (#3A0A0A) en dark (blanco sobre danger-dark falla 2.77:1); por eso se
 * consume por token, no con un hex suelto. primary-foreground = on-primary
 * (oscuro en dark). neutral usa el rol state-neutral (conteo informativo sin
 * urgencia): NUNCA rojo si el conteo no es una alerta crítica real.
 */
const ROL: Record<NotificationBadgeRol, string> = {
  danger: "bg-danger text-danger-foreground", // 4.83:1 light / ~6.2:1 dark
  primary: "bg-primary text-primary-foreground", // 5.36:1 light / 8.9:1 dark
  neutral: "bg-state-neutral text-state-neutral-foreground border border-state-neutral-border",
};

type NotificationBadgeProps = {
  /** Conteo a mostrar. <=0 sin dot ⇒ no renderiza nada. */
  count?: number;
  /** Rol del badge. `danger` SOLO para alertas críticas reales (averías/descuadres). */
  rol?: NotificationBadgeRol;
  /** Punto sólido sin número (novedad sin contar). */
  dot?: boolean;
  /** Tope del número antes de "+N". */
  max?: number;
  className?: string;
};

/**
 * Badge numérico de overlay sobre un icono/botón. Decorativo (aria-hidden): el
 * conteo REAL debe ir en el aria-label del botón anfitrión, y el cambio
 * anunciarse con aria-live (patrón LiveRegion) para no leer dígitos sueltos.
 * Colócalo dentro de un contenedor `relative`; el halo (ring-background) lo
 * recorta del glifo de fondo del TopBar.
 */
export function NotificationBadge({
  count = 0,
  rol = "primary",
  dot = false,
  max = 99,
  className,
}: NotificationBadgeProps) {
  const show = dot || count > 0;
  if (!show) return null;
  const glyph = count > max ? `+${max}` : String(count);

  return (
    <span
      aria-hidden
      className={cn(
        "pointer-events-none absolute -right-1.5 -top-1.5 flex items-center justify-center",
        "rounded-full ring-[1.5px] ring-background", // halo de recorte = fondo del anfitrión
        dot
          ? "size-2"
          : "h-[18px] min-w-[18px] px-1 text-[11px] font-semibold tabular-nums [font-family:var(--font-mono)]",
        ROL[rol],
        className,
      )}
    >
      {dot ? null : glyph}
    </span>
  );
}
