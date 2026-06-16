"use client";

import { Check } from "lucide-react";

import { cn } from "@/lib/utils";

export type FilterChipProps = {
  /** Etiqueta del criterio de filtro (texto corto, no se trunca). */
  label: string;
  /** Estado del toggle: true = seleccionado. */
  selected: boolean;
  /** Callback al toggle (click/tap). */
  onToggle: (next: boolean) => void;
  /** Icono opcional a la izquierda (16px, tintado según estado). */
  leadingIcon?: React.ReactNode;
  /** Contador opcional (sufijo con Geist Mono tabular, color muted). */
  count?: number;
  /** Deshabilitado: opacidad 0.5 sin cambiar rol. */
  disabled?: boolean;
  /** Clase adicional. */
  className?: string;
};

export type FilterChipRowProps = {
  /** Array de chips a mostrar. */
  chips: Array<{
    id: string;
    label: string;
    leadingIcon?: React.ReactNode;
    count?: number;
  }>;
  /** Set de IDs seleccionados. */
  selected: Set<string>;
  /** Callback al cambiar selección: (id, next) => void. */
  onToggle: (id: string, next: boolean) => void;
  /** Deshabilitar individuales por ID. */
  disabled?: Record<string, boolean>;
  /** Clase adicional para el wrapper. */
  className?: string;
};

/**
 * FilterChip — chip de filtro toggle CONTROL (no pasivo como StatusChip).
 *
 * Reposo: NEUTRO (border-token 1px, surface-1 plano, foreground/muted).
 * Seleccionado: PRIMARY (border primary 1.5px, fondo primary@10%/16%, Check + primary texto).
 * El Check entra con expand horizontal (0->16px) + label desliza.
 * Touch target >=44px; role=switch; aria-pressed.
 *
 * NO usa colores de rol de estado (success/warning/danger/info). Distinto de StatusChip.
 */
export function FilterChip({
  label,
  selected,
  onToggle,
  leadingIcon,
  count,
  disabled = false,
  className,
}: FilterChipProps) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onToggle(!selected)}
      role="switch"
      aria-checked={selected}
      aria-label={`${label}${count !== undefined ? ` (${count})` : ""}, ${selected ? "seleccionado" : "no seleccionado"}`}
      className={cn(
        // Base: pill full redondeada (50%), altura visual 32px
        "inline-flex h-8 shrink-0 items-center rounded-full transition-all duration-150",
        // Touch target >=44px (invisible extra padding)
        "before:pointer-events-none before:absolute before:inset-y-0 before:-left-1.5 before:w-3",
        "after:pointer-events-none after:absolute after:inset-y-0 after:-right-1.5 after:w-3",
        "relative",

        // Tipografía del chip (token label: 14/20/600)
        "text-label gap-1.5 px-3",

        // Reposo: NEUTRO — borde 1px sobre surface-1, texto foreground; hover sube un paso a surface-2
        !selected &&
          !disabled &&
          "border-border bg-surface-1 text-foreground hover:bg-surface-2 border",

        // Seleccionado: PRIMARY fill + texto blanco (corrección T-227: "FilterChip seleccionado = primary+blanco")
        selected &&
          !disabled &&
          "hover:bg-primary/90 border-primary bg-primary text-primary-foreground border",

        // Disabled: opacidad 0.5 sin cambiar rol
        disabled && "cursor-not-allowed opacity-50",
        !disabled && "cursor-pointer",

        // Foco visible: ring primary 2px + offset
        "focus-visible:ring-primary focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-hidden",

        // Feedback de pulsación (tap)
        !selected && "active:bg-primary/10",
        selected && !disabled && "active:bg-primary/80",

        // Respeta reduce-motion
        "motion-safe:transition-all motion-reduce:transition-none",

        className,
      )}
    >
      {/* Check: visible si selected, entra con expand horizontal (0->16) */}
      <span
        aria-hidden
        className={cn(
          "text-primary-foreground flex shrink-0 items-center justify-center transition-all duration-150 motion-reduce:transition-none",
          selected ? "w-4 opacity-100" : "w-0 opacity-0",
        )}
      >
        <Check size={16} strokeWidth={2.5} />
      </span>

      {/* Icono líder (opcional) */}
      {leadingIcon && (
        <span
          aria-hidden
          className={cn(
            "flex shrink-0 items-center justify-center transition-colors duration-150",
            selected ? "text-primary-foreground" : "text-muted-foreground",
          )}
        >
          {leadingIcon}
        </span>
      )}

      {/* Label: criterio de filtro */}
      <span className="truncate">{label}</span>

      {/* Contador (sufijo): Geist Mono tabular, color muted, 12px */}
      {count !== undefined && (
        <span
          aria-hidden
          className={cn(
            "shrink-0 text-xs tabular-nums transition-colors duration-150",
            selected ? "text-primary-foreground" : "text-muted-foreground",
          )}
        >
          · {count}
        </span>
      )}
    </button>
  );
}

/**
 * FilterChipRow — barra de chips filtro con scroll horizontal.
 *
 * - role=group aria-label='Filtros' (multi-selección)
 * - Scroll horizontal sin wrapping
 * - Fade-mask lateral derecho (gradiente a transparent)
 * - Botón "Limpiar filtros" al final (solo si >=1 activo)
 * - Sin sombra; border 1px para separar de contenido
 */
export function FilterChipRow({
  chips,
  selected,
  onToggle,
  disabled,
  className,
}: FilterChipRowProps) {
  const hasActive = selected.size > 0;

  return (
    <div
      className={cn(
        "relative flex items-center gap-2 overflow-x-auto px-4 pb-2",
        "no-scrollbar", // Asume clase global .no-scrollbar que oculta scrollbar
        className,
      )}
      role="group"
      aria-label="Filtros"
    >
      {/* Chips */}
      <div className="flex shrink-0 items-center gap-2">
        {chips.map((chip) => (
          <FilterChip
            key={chip.id}
            label={chip.label}
            leadingIcon={chip.leadingIcon}
            count={chip.count}
            selected={selected.has(chip.id)}
            onToggle={(next) => onToggle(chip.id, next)}
            disabled={disabled?.[chip.id] ?? false}
          />
        ))}
      </div>

      {/* Botón "Limpiar filtros" (solo si >=1 activo) */}
      {hasActive && (
        <button
          type="button"
          onClick={() => {
            // Deseleccionar todos
            selected.forEach((id) => onToggle(id, false));
          }}
          className={cn(
            "text-label text-primary shrink-0 px-2 py-1 whitespace-nowrap transition-colors",
            "hover:text-primary/80 focus-visible:ring-primary focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-hidden",
            "active:text-primary/60",
          )}
        >
          Limpiar filtros
        </button>
      )}

      {/* Fade-mask lateral derecho (gradiente) */}
      <div
        aria-hidden
        className={cn(
          "pointer-events-none absolute inset-y-0 right-0 w-6",
          "from-background bg-linear-to-l to-transparent",
        )}
      />
    </div>
  );
}
