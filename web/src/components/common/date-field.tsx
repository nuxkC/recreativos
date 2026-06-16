"use client";

// Átomo DatePicker del design system Recre (web). Selector de fecha vía calendario:
// el trigger es un Button tipo input (mismo alto/borde/foco que los campos de
// field.tsx) que abre un Popover con @/components/ui/calendar (react-day-picker).
//
// Por qué un componente propio y no <input type="date">: la spec FieldNum
// (sección DatePicker, fase3-component-specs.md) exige un overlay de calendario
// con formato es-ES dd/MM/aaaa, trigger read-only (foco navegable, copiable,
// NO disabled — la fecha se elige en el calendario, no se teclea) y contrato a11y
// propio (aria-expanded/-haspopup, Escape/outside-click, foco gestionado). El
// <input type="date"> nativo no garantiza ni el formato es-ES ni ese contrato en
// todos los navegadores; este átomo sí.
//
// Reutiliza el wrapper de campo de ./field (FormItem/FormLabel/FormDescription/
// FormMessage y los tipos FieldBaseProps/FieldDensity) para compartir label,
// descripción, mensaje de error inline, pie offline y los gaps/estados a11y.
//
// Money-safe no aplica (no hay cifras), pero el VALOR DE MODELO viaja como string
// ISO yyyy-MM-dd (estable, sin zona horaria ambigua); el Date solo vive dentro del
// componente para alimentar el calendario y formatear con date-fns. El SSOT
// económico/temporal definitivo está en el servidor; esto solo captura y muestra.
//
// Notas de tokens (verdad en globals.css / tailwind.config.ts):
//  - Control: bg-surface-2, rounded-md (8px), foco focus-visible:ring-2 ring-ring,
//    sin sombra (elevación por borde 1px); en error el borde pasa a danger vía
//    aria-[invalid]. Idéntico a controlClasses() de field.tsx.
//  - El icono calendario y el placeholder informativo usan text-muted-foreground:
//    el rol `muted-strong` que pide la spec (~7:1) NO existe como clase en
//    globals.css; muted-foreground es el neutro de texto más cercano expuesto. Se
//    anota la deuda en uncertainties (idéntico criterio que field.tsx).
//  - Popover (ui/popover) ya aporta surface-1 + borde + shadow-overlay + fade.

import * as React from "react";
import { format } from "date-fns";
import { es } from "date-fns/locale";
import { Calendar as CalendarIcon, CloudOff } from "lucide-react";

import { cn } from "@/lib/utils";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  type FieldBaseProps,
  type FieldDensity,
  FormDescription,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/common/field";

// ===========================================================================
// Helpers ISO <-> Date. El modelo es string ISO "yyyy-MM-dd" (sin hora ni TZ);
// el Date interno se ancla a mediodía LOCAL para que ningún desfase de zona
// horaria empuje la fecha al día anterior/siguiente al formatear o seleccionar.
// ===========================================================================

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** "yyyy-MM-dd" → Date local a mediodía, o undefined si el string no es ISO válido. */
function isoToDate(value: string | undefined): Date | undefined {
  if (value == null || !ISO_DATE.test(value)) return undefined;
  const [y, m, d] = value.split("-").map(Number);
  // noUncheckedIndexedAccess: split de un string que casa ISO_DATE siempre da 3
  // tramos numéricos, pero destructuramos con guardas por si el regex cambiara.
  if (y == null || m == null || d == null) return undefined;
  const date = new Date(y, m - 1, d, 12, 0, 0, 0);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

/** Date → "yyyy-MM-dd" en hora local (date-fns format, sin tocar la zona). */
function dateToIso(date: Date): string {
  return format(date, "yyyy-MM-dd");
}

/** Etiqueta visible es-ES: dd/MM/aaaa (ej. "14/06/2026"). */
function formatDisplay(date: Date): string {
  return format(date, "dd/MM/yyyy", { locale: es });
}

// ===========================================================================
// FieldDate — DatePicker (Popover + Calendar)
// ===========================================================================

interface FieldDateProps extends FieldBaseProps {
  /** Fecha del modelo en ISO "yyyy-MM-dd" (vacío = sin fecha). NUNCA un Date crudo. */
  value: string;
  /** Emite la nueva fecha en ISO "yyyy-MM-dd", o "" al limpiar (no implementado aquí: se selecciona). */
  onChange: (value: string) => void;
  /** Texto fantasma cuando no hay fecha elegida (placeholder del trigger). */
  placeholder?: string;
  /** disabled real: atenúa, quita foco y teclado. NO usar para "bloqueado de negocio" (eso es readOnly). */
  disabled?: boolean;
  /**
   * read-only: el trigger NO abre el calendario y queda en solo-lectura, pero
   * SIGUE siendo enfocable/copiable y visualmente = default (no atenuado).
   * Distinto de disabled (atenúa y sale del foco). aria-readonly lo anuncia.
   */
  readOnly?: boolean;
  /** Límite inferior ISO "yyyy-MM-dd": días anteriores quedan deshabilitados en el calendario. */
  min?: string;
  /** Límite superior ISO "yyyy-MM-dd": días posteriores quedan deshabilitados en el calendario. */
  max?: string;
  /** touch=h-11 (44px, táctil) por defecto; compact=h-9 (36px, back-office con ratón). */
  density?: FieldDensity;
}

/**
 * DatePicker. El trigger muestra la fecha formateada es-ES (date-fns, locale es)
 * o el placeholder; abre un Popover con Calendar (react-day-picker, navegable por
 * teclado y lectores). Al elegir un día se cierra el popover, se emite el ISO y el
 * foco vuelve al trigger (Radix Popover lo gestiona). Escape y click fuera cierran
 * el overlay (Radix). El trigger es read-only por naturaleza —no se teclea la
 * fecha— pero accionable: por eso, salvo `disabled` real, NUNCA se deshabilita el
 * botón; el modo `readOnly` lo deja en solo-lectura sin abrir el calendario.
 */
const FieldDate = React.forwardRef<HTMLButtonElement, FieldDateProps>(
  (
    {
      value,
      onChange,
      label,
      optional,
      description,
      error,
      offline,
      offlineText = "Sin sincronizar",
      id,
      className,
      placeholder = "Seleccionar fecha",
      disabled,
      readOnly,
      min,
      max,
      density = "touch",
    },
    ref,
  ) => {
    const [open, setOpen] = React.useState(false);
    const reactId = React.useId();
    const fieldId = id ?? reactId;
    const hasError = error != null && error !== false;
    const descriptionId = description != null ? `${fieldId}-description` : undefined;
    const errorId = hasError ? `${fieldId}-error` : undefined;
    // aria-describedby une descripción + error (ambos opcionales).
    const describedBy = [descriptionId, errorId].filter(Boolean).join(" ") || undefined;

    const selected = isoToDate(value);
    const minDate = isoToDate(min);
    const maxDate = isoToDate(max);

    // Días fuera de [min, max] se deshabilitan en el calendario (no se navegan).
    const disabledMatcher = React.useMemo(() => {
      const before = minDate ? { before: minDate } : undefined;
      const after = maxDate ? { after: maxDate } : undefined;
      if (before && after) return [before, after];
      return before ?? after ?? undefined;
    }, [minDate, maxDate]);

    function handleSelect(day: Date | undefined): void {
      if (day == null) return;
      onChange(dateToIso(day));
      // Confirmada la elección, se cierra el overlay; Radix devuelve el foco al trigger.
      setOpen(false);
    }

    /** Alto del control: touch=h-11 (target táctil ≥44px); compact=h-9 (back-office ratón). */
    const controlHeight = density === "compact" ? "h-9" : "h-11";
    const hasValue = selected != null;

    return (
      <FormItem className={className}>
        {label != null ? (
          <FormLabel id={`${fieldId}-label`} htmlFor={fieldId} error={hasError} optional={optional}>
            {label}
          </FormLabel>
        ) : null}

        <Popover open={open} onOpenChange={readOnly ? undefined : setOpen}>
          <PopoverTrigger asChild>
            {/*
              jsx-a11y avisa de aria-readonly/aria-invalid sobre role=button, pero
              ambos son estados globales válidos por ARIA 1.2 y la spec FieldNum los
              EXIGE en el trigger: aria-readonly anuncia "solo lectura" manteniendo
              el foco (read-only ≠ disabled) y aria-invalid liga el estado error con
              FormMessage. Es el mismo contrato que el SelectTrigger de field.tsx;
              se silencia el falso positivo, no se incumple el patrón.
            */}
            {/* eslint-disable-next-line jsx-a11y/role-supports-aria-props */}
            <button
              ref={ref}
              id={fieldId}
              type="button"
              // read-only ≠ disabled: el trigger sigue enfocable y copiable, NO se
              // atenúa; solo bloquea la apertura del calendario (aria-readonly lo
              // anuncia). disabled real sí atenúa y sale del foco/teclado.
              disabled={disabled}
              aria-haspopup="dialog"
              aria-expanded={open}
              aria-readonly={readOnly || undefined}
              aria-invalid={hasError}
              aria-describedby={describedBy}
              aria-labelledby={label != null ? `${fieldId}-label` : undefined}
              onClick={(e) => {
                // read-only: el control existe y es enfocable, pero no abre el overlay.
                if (readOnly) e.preventDefault();
              }}
              className={cn(
                controlHeight,
                // Mismo lenguaje que controlClasses() de field.tsx: surface-2,
                // radio 8px, sin sombra (elevación por borde 1px), foco ring=primary.
                "border-input bg-surface-2 flex w-full items-center justify-between gap-2 rounded-md border px-3",
                "text-body text-left",
                "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-hidden",
                "ease-standard transition-colors",
                // error: borde danger (nace del aria-invalid, no de una clase suelta).
                "aria-invalid:border-danger",
                // disabled: atenúa y desactiva (read-only NO entra aquí).
                "disabled:cursor-not-allowed disabled:opacity-50",
                // read-only: cursor de lectura, visual = default (sin atenuar).
                readOnly && "cursor-default",
              )}
            >
              {/* El valor en foreground; el placeholder fantasma en muted (no porta
                  información única). num-tabular: dígitos de fecha alineados. */}
              <span
                className={cn(
                  "num-tabular truncate",
                  hasValue ? "text-foreground" : "text-muted-foreground",
                )}
              >
                {hasValue ? formatDisplay(selected) : placeholder}
              </span>
              {/* Icono calendario informativo (adorno derecho); muted-strong
                  (≥7:1, el rol del spec para iconos), sin opacity. */}
              <CalendarIcon aria-hidden className="text-muted-strong size-4 shrink-0" />
            </button>
          </PopoverTrigger>

          {/* w-auto: el ancho lo fija el calendario, no el w-72 por defecto del popover. */}
          <PopoverContent
            align="start"
            className="w-auto p-0"
            // Foco inicial al contenido del calendario al abrir; al cerrar Radix
            // devuelve el foco al trigger automáticamente.
            role="dialog"
            aria-label="Elegir fecha"
          >
            <Calendar
              // react-day-picker v10: selección única controlada.
              mode="single"
              selected={selected}
              onSelect={handleSelect}
              defaultMonth={selected ?? maxDate ?? undefined}
              disabled={disabledMatcher}
              // autoFocus: el teclado entra directo en la rejilla del calendario
              // (flechas mueven el día, Enter selecciona, Escape cierra el popover).
              autoFocus
            />
          </PopoverContent>
        </Popover>

        {description != null && !hasError ? (
          <FormDescription id={descriptionId}>{description}</FormDescription>
        ) : null}
        {hasError ? <FormMessage id={errorId}>{error}</FormMessage> : null}
        {/* Pie offline "sin sincronizar": warning ámbar + CloudOff + texto (NUNCA
            danger; offline no es error). Estado nunca solo-color. */}
        {offline ? (
          <p className="text-caption text-warning-text flex items-center gap-1.5">
            <CloudOff aria-hidden className="size-4 shrink-0" />
            <span>{offlineText}</span>
          </p>
        ) : null}
      </FormItem>
    );
  },
);
FieldDate.displayName = "FieldDate";

export { FieldDate, type FieldDateProps };
