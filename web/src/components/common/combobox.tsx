"use client";

import { Check, ChevronsUpDown, Loader2, Plus } from "lucide-react";
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
 * Alta por texto libre (opcional): con `onCreate`, si lo tecleado no coincide
 * con ninguna opción se ofrece un ítem "Crear «X»" al final. El valor emitido
 * sigue siendo el texto (no un id): quien consume decide qué hacer con él (en
 * máquinas, la RPC lo resuelve/crea en el catálogo al guardar). Sin `onCreate`
 * el componente es una lista cerrada, idéntico al comportamiento anterior.
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
  /**
   * Habilita el alta por texto libre: recibe el texto tecleado (trim) cuando el
   * usuario activa "Crear «…»". Si se omite, el combobox es una lista cerrada.
   */
  onCreate?: (label: string) => void;
  /** Texto del ítem de alta a partir de lo tecleado. Default: «Crear «X»». */
  formatCreateLabel?: (value: string) => string;
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

/**
 * ¿Debe ofrecerse "crear «X»" para lo tecleado? Sí cuando hay texto no vacío y
 * ninguna opción tiene ese label (comparación laxa: trim + minúsculas), para no
 * ofrecer un duplicado de algo que ya existe. Función pura → testeable sin render.
 */
export function debeOfrecerCrear(options: ComboboxOption[], busqueda: string): boolean {
  const objetivo = busqueda.trim().toLowerCase();
  if (objetivo.length === 0) return false;
  return !options.some((o) => o.label.trim().toLowerCase() === objetivo);
}

/**
 * Etiqueta a mostrar en el trigger para `value`: el label de la opción si el
 * valor está catalogado; si no (valor tecleado libremente o dato heredado que
 * ya no está en el catálogo), el propio valor. `null` = sin valor → placeholder.
 */
export function etiquetaValor(options: ComboboxOption[], value: string | null): string | null {
  if (!value) return null;
  const encontrada = options.find((o) => o.value === value);
  return encontrada?.label ?? value;
}

export function Combobox({
  options,
  value,
  onChange,
  onCreate,
  formatCreateLabel = (valor) => `Crear «${valor}»`,
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
  // Texto de búsqueda controlado: necesario para saber qué "Crear" ofrecer.
  const [search, setSearch] = React.useState("");

  // Etiqueta visible del valor actual (soporta valores fuera de la lista).
  const etiqueta = etiquetaValor(options, value);
  const textoCrear = search.trim();
  const ofrecerCrear = onCreate !== undefined && debeOfrecerCrear(options, search);

  // El trigger no se abre mientras carga (no hay opciones reales que filtrar).
  const triggerDisabled = disabled || loading;

  function handleOpenChange(next: boolean) {
    if (triggerDisabled) return;
    setOpen(next);
    // Al cerrar se descarta el texto tecleado: cada apertura empieza limpia.
    if (!next) setSearch("");
  }

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
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
          "border-input bg-surface-2 text-foreground flex h-11 w-full items-center justify-between gap-2 rounded-lg border px-3 text-sm font-normal transition-colors",
          "hover:bg-accent/40",
          "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-hidden",
          "disabled:pointer-events-none disabled:opacity-50",
          // Error: borde danger sin depender solo del color (lo refuerza el
          // FormMessage con icono+texto que pone el contenedor de campo).
          error && "border-danger",
          className,
        )}
      >
        {etiqueta ? (
          <span className="truncate">{etiqueta}</span>
        ) : (
          <span className="text-muted-foreground truncate">{placeholder}</span>
        )}
        {loading ? (
          <Loader2
            className="text-muted-strong size-4 shrink-0 animate-spin motion-reduce:animate-none"
            aria-hidden="true"
          />
        ) : (
          <ChevronsUpDown className="text-muted-strong size-4 shrink-0" aria-hidden="true" />
        )}
      </PopoverTrigger>
      <PopoverContent
        // Ancho = ancho del trigger para que la lista alinee con el control.
        className="w-(--radix-popover-trigger-width) p-0"
        align="start"
      >
        <Command>
          {/* El role=combobox real vive en el input (no en el trigger): es quien
              recibe el texto y gestiona aria-activedescendant vía cmdk. */}
          <CommandInput
            value={search}
            onValueChange={setSearch}
            placeholder={searchPlaceholder}
            role="combobox"
            aria-expanded={open}
            aria-controls={id}
            aria-label={searchPlaceholder}
          />
          <CommandList id={id} role="listbox">
            {/* Vacío tras filtrar: solo cuando NO hay opción ni ítem de alta que
                mostrar. Con "Crear «X»" presente, cmdk no lo considera vacío. */}
            {!ofrecerCrear ? (
              <CommandEmpty
                className="text-muted-strong py-4 text-center text-sm"
                role="status"
                aria-live="polite"
              >
                {emptyMessage}
              </CommandEmpty>
            ) : null}
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
                    setSearch("");
                  }}
                >
                  <Check
                    className={cn(
                      "text-primary mr-2 size-4",
                      isSelected ? "opacity-100" : "opacity-0",
                    )}
                    aria-hidden="true"
                  />
                  {option.label}
                </CommandItem>
              );
            })}
            {/* Alta por texto libre: value = texto tecleado para que cmdk no lo
                filtre (coincide con la búsqueda). Emite el texto trim al consumidor. */}
            {ofrecerCrear ? (
              <CommandItem
                key="__crear__"
                role="option"
                value={search}
                onSelect={() => {
                  if (textoCrear.length > 0) onCreate?.(textoCrear);
                  setOpen(false);
                  setSearch("");
                }}
              >
                <Plus className="text-primary mr-2 size-4" aria-hidden="true" />
                {formatCreateLabel(textoCrear)}
              </CommandItem>
            ) : null}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
