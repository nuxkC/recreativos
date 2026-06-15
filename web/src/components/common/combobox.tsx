"use client";

import { Check, ChevronsUpDown, Loader2 } from "lucide-react";
import * as React from "react";

import {
  Command,
  CommandEmpty,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

/**
 * Átomo Combobox de selección única buscable (caso canónico: las 19 CCAA).
 *
 * Por qué un Combobox y no un Select: con 19+ opciones un Select corto obliga a
 * scroll ciego; aquí el filtro por texto (cmdk) reduce la lista mientras se
 * teclea. Implementa el contrato ARIA de combobox/listbox/option:
 *   - trigger: role="combobox" + aria-expanded + aria-controls (al listbox).
 *   - popover: <Command> de cmdk gestiona aria-activedescendant y el resaltado
 *     del ítem activo por teclado (flechas) sin que tengamos que cablearlo.
 *   - lista: role="listbox"; cada ítem role="option" + aria-selected.
 * Teclado: flechas mueven el activedescendant, Enter selecciona, Escape cierra
 * y devuelve el foco al trigger (lo hace Radix Popover), click-fuera cierra.
 *
 * Token nota: la spec FieldNum pide `muted-strong` para chevron/placeholder/
 * vacío (objetivo ~7:1 Android). Ese token AÚN no existe en el tema (pendiente
 * de T-227); para no inventar una clase que Tailwind ignoraría en silencio se
 * usa el rol existente más cercano: `text-muted-foreground`. Migrar a
 * `muted-strong` cuando se añada.
 */

export type ComboboxOption = {
  /** Valor estable que se emite en onChange (p. ej. el código ISO de la CCAA). */
  value: string;
  /** Texto visible y por el que filtra cmdk. */
  label: string;
};

export type ComboboxProps = {
  /** Opciones a elegir. Para CCAA, las 19 + Ceuta/Melilla. */
  options: ComboboxOption[];
  /** Valor seleccionado (controlado). `null`/"" = sin selección. */
  value: string | null;
  /** Notifica el nuevo `value` al seleccionar un ítem. */
  onChange: (value: string) => void;
  /** Texto del trigger cuando no hay valor. */
  placeholder?: string;
  /** Placeholder del input de búsqueda dentro del popover. */
  searchPlaceholder?: string;
  /** Mensaje cuando el filtro no deja coincidencias (se anuncia aria-live). */
  emptyMessage?: string;
  /** id del listbox (aria-controls del trigger). Único si hay varios en página. */
  id?: string;
  /** Deshabilitado: sin foco, sin teclado, opacidad reducida. */
  disabled?: boolean;
  /**
   * Cargando opciones: spinner en el trigger + aria-busy. NO es read-only (la
   * spec lo distingue: el combobox que aún carga opciones está LOADING, no
   * bloqueado). Mientras carga no se abre el popover.
   */
  loading?: boolean;
  /** Estado de error: borde danger + aria-invalid (el mensaje lo pone el Form). */
  error?: boolean;
  /** Etiqueta accesible del trigger si no hay <label> asociado por id. */
  "aria-label"?: string;
  /** id del/los nodos que describen el control (FormDescription/FormMessage). */
  "aria-describedby"?: string;
  className?: string;
};

export function Combobox({
  options,
  value,
  onChange,
  placeholder = "Elegir…",
  searchPlaceholder = "Buscar…",
  emptyMessage = "Sin coincidencias",
  id = "combobox-listbox",
  disabled = false,
  loading = false,
  error = false,
  className,
  "aria-label": ariaLabel,
  "aria-describedby": ariaDescribedBy,
}: ComboboxProps) {
  const [open, setOpen] = React.useState(false);

  // Etiqueta visible del valor actual. noUncheckedIndexedAccess: find() puede
  // devolver undefined, así que se cae al placeholder si no se encuentra.
  const selected = value ? options.find((o) => o.value === value) : undefined;

  // El trigger no se abre mientras carga (no hay opciones reales que filtrar).
  const triggerDisabled = disabled || loading;

  return (
    <Popover open={open} onOpenChange={triggerDisabled ? undefined : setOpen}>
      <PopoverTrigger
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={id}
        aria-label={ariaLabel}
        aria-describedby={ariaDescribedBy}
        aria-invalid={error || undefined}
        aria-busy={loading || undefined}
        disabled={triggerDisabled}
        className={cn(
          // Mismo lenguaje que Button variant="outline" pero el trigger es un
          // control de formulario: superficie surface-2, no background.
          "flex h-11 w-full items-center justify-between gap-2 rounded-lg border border-input bg-surface-2 px-3 text-sm font-normal text-foreground transition-colors",
          "hover:bg-accent/40",
          "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring",
          "disabled:pointer-events-none disabled:opacity-50",
          // Error: borde danger sin depender solo del color (lo refuerza el
          // FormMessage con icono+texto que pone el contenedor de campo).
          error && "border-danger",
          className,
        )}
      >
        {selected ? (
          <span className="truncate">{selected.label}</span>
        ) : (
          <span className="truncate text-muted-foreground">{placeholder}</span>
        )}
        {loading ? (
          <Loader2
            className="size-4 shrink-0 animate-spin text-muted-strong motion-reduce:animate-none"
            aria-hidden="true"
          />
        ) : (
          <ChevronsUpDown className="size-4 shrink-0 text-muted-strong" aria-hidden="true" />
        )}
      </PopoverTrigger>
      <PopoverContent
        // Ancho = ancho del trigger para que la lista alinee con el control.
        className="w-[--radix-popover-trigger-width] p-0"
        align="start"
      >
        <Command>
          {/* El role=combobox real vive en el input (no en el trigger): es quien
              recibe el texto y gestiona aria-activedescendant vía cmdk. */}
          <CommandInput
            placeholder={searchPlaceholder}
            role="combobox"
            aria-expanded={open}
            aria-controls={id}
            aria-label={searchPlaceholder}
          />
          <CommandList id={id} role="listbox">
            {/* Vacío tras filtrar: texto centrado + aria-live para que el lector
                anuncie que la lista quedó sin coincidencias. */}
            <CommandEmpty
              className="py-4 text-center text-sm text-muted-strong"
              role="status"
              aria-live="polite"
            >
              {emptyMessage}
            </CommandEmpty>
            {options.map((option) => {
              const isSelected = option.value === value;
              return (
                <CommandItem
                  key={option.value}
                  role="option"
                  aria-selected={isSelected}
                  // El valor ELEGIDO se marca con tonal de marca (secondary) + check;
                  // el resaltado de teclado (data-[selected] de cmdk) es surface-2.
                  className={cn(isSelected && "bg-secondary text-secondary-foreground")}
                  // value = label: cmdk filtra por el texto visible, no por el código.
                  value={option.label}
                  onSelect={() => {
                    onChange(option.value);
                    setOpen(false);
                  }}
                >
                  <Check
                    className={cn(
                      "mr-2 size-4 text-primary",
                      isSelected ? "opacity-100" : "opacity-0",
                    )}
                    aria-hidden="true"
                  />
                  {option.label}
                </CommandItem>
              );
            })}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
