"use client";

import * as React from "react";
import { Search, X, Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * SearchField — barra de búsqueda controlada con icono lupa (leading) y botón
 * limpiar (trailing). Átomo F3-A-SearchField.
 *
 * Filtro reactivo sobre un conjunto visible (locales, titulares, máquinas…).
 * NO es entrada numérica/dinero (eso es FieldNum) ni un formulario con submit.
 *
 * Controlado y puro: solo emite `value`/`onValueChange`. El debounce (250ms),
 * el fetch y la lógica de `?q=` viven en el contenedor de lista (p.ej.
 * locales-filters.tsx), nunca dentro del átomo.
 *
 * Tokens: bg-surface-2 (barra propia), border-input (1px, elevación por borde,
 * sin sombra), ring=primary solo en foco/caret, lupa y placeholder en
 * muted-foreground. El acento (primary) se reserva al presupuesto ≤10%: la lupa
 * NUNCA va en primary, solo el spinner de carga, el caret y el ring de foco.
 *
 * A11y: aria-label obligatorio (el placeholder NO sirve como única etiqueta);
 * role=searchbox (implícito en type="search", lo declaramos explícito);
 * aria-busy durante la carga; foco siempre visible; limpiar mantiene el foco en
 * el campo. El estado nunca se codifica solo por color (lupa/X/spinner son
 * iconos con significado + aria).
 */
interface SearchFieldProps {
  /** Valor controlado del campo. */
  value: string;
  /** Se emite en cada tecleo y al limpiar (con cadena vacía). */
  onValueChange: (value: string) => void;
  /**
   * Etiqueta accesible obligatoria: el placeholder NO es etiqueta accesible.
   * Ej.: "Buscar local o titular".
   */
  ariaLabel: string;
  /** Texto guía visible; no sustituye a `ariaLabel`. */
  placeholder?: string;
  /**
   * Búsqueda server-side en vuelo: sustituye la lupa por un spinner y expone
   * aria-busy. El error/empty viven en la LISTA, no aquí (no pinta de rojo).
   */
  isLoading?: boolean;
  /** Bloquea el campo (raro: solo si la lista entera está inactiva). */
  disabled?: boolean;
  /** aria-label del botón limpiar. */
  clearAriaLabel?: string;
  className?: string;
}

export function SearchField({
  value,
  onValueChange,
  ariaLabel,
  placeholder = "Buscar local o titular…",
  isLoading = false,
  disabled = false,
  clearAriaLabel = "Limpiar búsqueda",
  className,
}: SearchFieldProps) {
  // Ref al input para devolverle el foco tras limpiar: el foco no se pierde.
  const inputRef = React.useRef<HTMLInputElement>(null);

  // El botón limpiar SOLO existe con texto y nunca con el campo bloqueado.
  const showClear = value.length > 0 && !disabled;

  const handleClear = React.useCallback(() => {
    onValueChange("");
    inputRef.current?.focus();
  }, [onValueChange]);

  return (
    <div className={cn("relative flex-1", className)}>
      {/* Leading: lupa (idle, muted) o spinner (carga, primary). Decorativos:
          aria-hidden + pointer-events-none para no robar foco ni clic. */}
      {isLoading ? (
        <Loader2
          aria-hidden
          className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 animate-spin text-primary motion-reduce:animate-none"
        />
      ) : (
        <Search
          aria-hidden
          className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
        />
      )}

      {/* type="search" activa el teclado de búsqueda del sistema e implica
          role=searchbox (lo declaramos explícito por robustez). pl-9 deja sitio
          a la lupa; pr-9 al botón limpiar. bg-surface-2 + border-input 1px;
          ring=primary en foco; shadow-none (elevación por borde). */}
      <input
        ref={inputRef}
        type="search"
        inputMode="search"
        autoComplete="off"
        role="searchbox"
        aria-label={ariaLabel}
        aria-busy={isLoading}
        disabled={disabled}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
        className={cn(
          "flex h-9 w-full rounded-md border border-input bg-surface-2 pl-9 pr-9 text-sm shadow-none transition-colors",
          "caret-primary placeholder:text-muted-foreground",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
          "disabled:cursor-not-allowed disabled:opacity-50",
        )}
      />

      {/* Trailing: limpiar (X) — solo con texto. muted idle → foreground/accent
          en hover; foco visible con ring=primary. fade+scale 120ms sin rebote
          (animate-in del plugin tailwindcss-animate), desactivado en
          motion-reduce. NUNCA danger: vaciar no es destructivo. */}
      {showClear && (
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={clearAriaLabel}
          onClick={handleClear}
          className={cn(
            "absolute right-1 top-1/2 size-7 -translate-y-1/2",
            // Target táctil ≥44px sin agrandar el glifo (pseudo-elemento invisible)
            "before:absolute before:-inset-2 before:content-['']",
            "text-muted-foreground hover:bg-accent hover:text-foreground",
            "transition-colors duration-150 focus-visible:ring-2 focus-visible:ring-ring",
            "animate-in fade-in zoom-in-95 motion-reduce:animate-none",
          )}
        >
          <X aria-hidden className="size-4" />
        </Button>
      )}
    </div>
  );
}
