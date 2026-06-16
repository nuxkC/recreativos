"use client";

import * as React from "react";

import { cn } from "@/lib/utils";

export type SegmentedOption<T extends string> = {
  value: T;
  label: string;
  icon?: React.ReactNode;
};

type SegmentedControlProps<T extends string> = {
  /** 2 o 3 opciones (conmutador, no menú). */
  options: SegmentedOption<T>[];
  value: T;
  onValueChange: (value: T) => void;
  /** aria-label del radiogroup (el control no lleva label visible propio). */
  label: string;
  disabled?: boolean;
  className?: string;
};

/**
 * Conmutador de 2-3 opciones con thumb deslizante (radiogroup + roving tabindex).
 * Carril NEUTRO (surface-2) con borde reforzado en light (≥3:1, WCAG 1.4.11); el
 * thumb activo usa el tonal de marca (secondary), NO el fill primary — un
 * conmutador resalta la opción, no es una acción. Estado por forma (thumb) +
 * aria-checked, nunca solo color. El desliz respeta prefers-reduced-motion.
 */
export function SegmentedControl<T extends string>({
  options,
  value,
  onValueChange,
  label,
  disabled,
  className,
}: SegmentedControlProps<T>) {
  const idx = Math.max(
    0,
    options.findIndex((o) => o.value === value),
  );
  const refs = React.useRef<(HTMLButtonElement | null)[]>([]);

  const move = (next: number) => {
    const n = (next + options.length) % options.length;
    const opt = options[n];
    if (!opt) return;
    onValueChange(opt.value);
    refs.current[n]?.focus();
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowRight" || e.key === "ArrowDown") {
      e.preventDefault();
      move(idx + 1);
    } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
      e.preventDefault();
      move(idx - 1);
    } else if (e.key === "Home") {
      e.preventDefault();
      move(0);
    } else if (e.key === "End") {
      e.preventDefault();
      move(options.length - 1);
    }
  };

  return (
    <div
      role="radiogroup"
      aria-label={label}
      aria-disabled={disabled || undefined}
      onKeyDown={onKeyDown}
      className={cn(
        "bg-surface-2 relative grid h-10 rounded-full p-1 select-none",
        // borde de control ≥3:1 en light (1.4.11); en dark basta el delta de superficie
        "border-border-strong dark:border-border border",
        options.length === 2 ? "grid-cols-2" : "grid-cols-3",
        disabled && "pointer-events-none opacity-50",
        className,
      )}
    >
      {/* Thumb deslizante: translateX por índice; tonal de marca = bg-secondary. */}
      <span
        aria-hidden
        className="bg-secondary ease-standard pointer-events-none absolute inset-y-1 left-1 rounded-full motion-safe:transition-transform motion-safe:duration-150"
        style={{
          width: `calc((100% - 0.5rem) / ${options.length})`,
          transform: `translateX(${idx * 100}%)`,
        }}
      />
      {options.map((o, i) => {
        const active = i === idx;
        return (
          <button
            key={o.value}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="radio"
            aria-checked={active}
            tabIndex={active ? 0 : -1} // roving tabindex
            disabled={disabled}
            onClick={() => !active && onValueChange(o.value)}
            className={cn(
              "relative z-10 inline-flex h-8 items-center justify-center gap-1.5 rounded-full px-3",
              "text-sm leading-none whitespace-nowrap",
              "ease-standard motion-safe:transition-colors motion-safe:duration-150",
              "focus-visible:ring-ring focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-hidden",
              "motion-safe:active:scale-[0.98]",
              // activo: foreground + 600 sobre el thumb tonal; inactivo: muted → foreground en hover
              active
                ? "text-foreground font-semibold"
                : "text-muted-foreground hover:text-foreground font-medium",
            )}
          >
            {o.icon ? (
              <span aria-hidden className="inline-flex shrink-0 items-center [&_svg]:size-4">
                {o.icon}
              </span>
            ) : null}
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
