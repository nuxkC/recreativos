"use client";

import { ChevronDown } from "lucide-react";
import { type ReactNode, useCallback, useId, useState } from "react";

import { cn } from "@/lib/utils";

export type CollapsibleVariant = "sticky" | "card";

export interface CollapsibleProps {
  /** Título de la cabecera (h2). Nombra la región accesible (aria-labelledby). */
  title: string;
  /** Subtítulo opcional en muted (caption). */
  subtitle?: string;
  /** Slot leading: icono 16px en muted o un <StatusChip/>. Decorativo o estado de entidad. */
  leading?: ReactNode;
  /** Slot trailing: p. ej. <MoneyText/> de contexto. Va antes del chevron. */
  trailing?: ReactNode;
  /** Estado inicial (modo no controlado). */
  defaultOpen?: boolean;
  /**
   * Estado controlado. Si se pasa, el componente no gestiona su propio estado
   * y `onOpenChange` es responsable de actualizarlo.
   */
  open?: boolean;
  /** Notifica el cambio de estado (controlado o no). */
  onOpenChange?: (open: boolean) => void;
  /**
   * Variante visual:
   * - "sticky": cabecera fija (top-0) sobre surface-2, separador inferior. Keypad/LocalDetalle.
   * - "card" (default): cabecera+contenido sobre surface-1, elevación por borde. Auditoría F.2.
   */
  variant?: CollapsibleVariant;
  /**
   * Densidad alta (solo web): rebaja la altura mínima de la cabecera a 44px.
   * Excepción permitida por el spec para la auditoría densa; el baseline es 48px.
   */
  dense?: boolean;
  /** Contenido del panel desplegable. */
  children: ReactNode;
  className?: string;
}

/**
 * Acordeón / disclosure del Design System Recre.
 *
 * Mecanismo de divulgación progresiva: una cabecera pulsable (toda la fila es el
 * control, NO solo el chevron) que expande/colapsa una región de contenido. No
 * captura datos ni recalcula cifras: el importe de contexto se delega a
 * <MoneyText/> (trailing) y el estado de entidad a <StatusChip/> (leading); este
 * átomo solo aporta el mecanismo de expandir/colapsar y su semántica aria.
 *
 * No usa @radix-ui/react-collapsible (no instalado): se implementa con un
 * <button aria-expanded aria-controls> que apunta a una <section role="region"
 * aria-labelledby> — cableando el contrato ARIA completo que el spec exige
 * (aria-controls + region + aria-labelledby), no solo el aria-expanded.
 *
 * Motion: el chevron rota 0°→180° y el panel crece en altura+opacidad con la
 * curva ease.standard (0.2,0,0,1) a 150ms. La altura se anima con el truco de
 * CSS grid-rows (0fr→1fr) para soportar `height:auto` sin medir en JS ni
 * keyframes a medida. En `prefers-reduced-motion` chevron y panel saltan a su
 * estado final (motion-reduce:transition-none), conservando estado y semántica.
 */
export function Collapsible({
  title,
  subtitle,
  leading,
  trailing,
  defaultOpen = false,
  open: controlledOpen,
  onOpenChange,
  variant = "card",
  dense = false,
  children,
  className,
}: CollapsibleProps) {
  const titleId = useId();
  const panelId = useId();

  // Estado no controlado interno; si llega `open` el componente es controlado.
  const [uncontrolledOpen, setUncontrolledOpen] = useState(defaultOpen);
  const isControlled = controlledOpen !== undefined;
  const open = isControlled ? controlledOpen : uncontrolledOpen;

  const toggle = useCallback(() => {
    const next = !open;
    if (!isControlled) setUncontrolledOpen(next);
    onOpenChange?.(next);
  }, [open, isControlled, onOpenChange]);

  const sticky = variant === "sticky";

  return (
    <div className={cn("w-full", sticky ? "bg-surface-2" : "rounded-lg bg-card", className)}>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={toggle}
        className={cn(
          // Toda la fila es el control. min-h-12 (48px) baseline; dense -> 44px.
          "group flex w-full items-center gap-3 px-4 py-3 text-left",
          dense ? "min-h-11" : "min-h-12",
          // Foco visible (mismo patrón que FieldNum/Botones): ring=primary + 1px offset.
          "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring focus-visible:ring-offset-1",
          "transition-colors duration-150 ease-standard motion-reduce:transition-none",
          sticky
            ? "hover:bg-surface-2/90 sticky top-0 z-10 border-b border-border bg-surface-2"
            : "rounded-lg hover:bg-surface-2",
          // En variante card, con el panel abierto la cabecera no redondea abajo.
          !sticky && open && "rounded-b-none",
        )}
      >
        {leading ? <span className="flex shrink-0 items-center">{leading}</span> : null}
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span id={titleId} className="truncate text-h2 text-foreground">
            {title}
          </span>
          {subtitle ? (
            <span className="truncate text-caption text-muted-foreground">{subtitle}</span>
          ) : null}
        </span>
        {trailing ? <span className="ml-auto flex shrink-0 items-center">{trailing}</span> : null}
        {/* El estado se comunica por aria-expanded Y por la rotación, nunca solo por movimiento. */}
        <ChevronDown
          aria-hidden
          className={cn(
            "size-4 shrink-0 text-muted-foreground",
            "transition-transform duration-150 ease-standard motion-reduce:transition-none",
            open && "rotate-180",
          )}
        />
      </button>

      {/*
        Animación de altura sin keyframes ni medición JS: el wrapper grid pasa de
        grid-rows-[0fr] (colapsado) a grid-rows-[1fr] (expandido) y el hijo interno
        (min-h-0 overflow-hidden) se recorta a esa altura. Soporta height:auto.
        reduced-motion -> sin transición (snap al estado final).
      */}
      <div
        className={cn(
          "grid transition-[grid-template-rows] duration-150 ease-standard motion-reduce:transition-none",
          open ? "grid-rows-[1fr]" : "grid-rows-[0fr]",
        )}
      >
        <section
          id={panelId}
          role="region"
          aria-labelledby={titleId}
          // Fuera del orden de tabulación y del árbol AX cuando está colapsado.
          inert={open ? undefined : true}
          className={cn(
            "min-h-0 overflow-hidden",
            "transition-opacity duration-150 ease-standard motion-reduce:transition-none",
            open ? "opacity-100" : "opacity-0",
          )}
        >
          {/* Separador cabecera↔contenido (1px border). pt menor: evita doble aire. */}
          <div className="border-t border-border px-4 pb-4 pt-3">{children}</div>
        </section>
      </div>
    </div>
  );
}
