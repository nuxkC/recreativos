import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

export type StatusChipRole = "success" | "warning" | "danger" | "neutral" | "info";
export type StatusChipSize = "sm" | "md" | "lg";

/**
 * Pares OPACOS precomputados por rol (NO alpha): el contraste no depende de la
 * superficie sobre la que se monte el chip. `chip-fg` es el rol OSCURECIDO/
 * aclarado a AA ≥4.5:1 light / ~7:1 dark — nunca el fill pleno ni el hex del
 * fondo. neutral (offline/borrador) usa muted, NUNCA rojo.
 */
const roleClass: Record<StatusChipRole, string> = {
  success: "bg-success-chip-bg text-success-chip-fg",
  warning: "bg-warning-chip-bg text-warning-chip-fg",
  danger: "bg-danger-chip-bg text-danger-chip-fg",
  info: "bg-info-chip-bg text-info-chip-fg",
  neutral: "bg-neutral-chip-bg text-neutral-chip-fg",
};

const sizeClass: Record<StatusChipSize, string> = {
  sm: "h-5 gap-1 px-1.5 text-[11px] [&_svg]:size-3", // 20px — tablas densas, sin dot
  md: "h-6 gap-1 px-2 text-xs [&_svg]:size-3.5", // 24px — default
  lg: "h-7 gap-1 px-2.5 text-[13px] [&_svg]:size-4", // 28px — cabeceras de detalle
};

type StatusChipProps = {
  role: StatusChipRole;
  /** Etiqueta del estado. SIEMPRE presente: nunca solo icono ni solo color. */
  label: string;
  /** Icono del rol (Lucide), distinguible por FORMA (a11y daltonismo). Obligatorio. */
  icon: ReactNode;
  size?: StatusChipSize;
  /** Punto del color de contenido (refuerzo redundante). Solo md/lg. */
  dot?: boolean;
  className?: string;
};

/**
 * Indicador de estado compacto «soft» (fondo del rol opaco, contenido en el
 * color de rol con contraste validado) con icono + texto SIEMPRE juntos. Hace
 * cumplir «estado nunca solo-color». No es acción: el acento de marca (primary)
 * no se usa aquí. Es de solo lectura (no es touch target).
 */
export function StatusChip({
  role,
  label,
  icon,
  size = "md",
  dot = false,
  className,
}: StatusChipProps) {
  const showDot = dot && size !== "sm";
  return (
    <span
      className={cn(
        "inline-flex w-fit items-center whitespace-nowrap rounded-full font-medium",
        roleClass[role],
        sizeClass[size],
        className,
      )}
    >
      {showDot ? <span aria-hidden className="size-1.5 shrink-0 rounded-full bg-current" /> : null}
      <span aria-hidden className="inline-flex shrink-0 items-center">
        {icon}
      </span>
      <span>{label}</span>
    </span>
  );
}
