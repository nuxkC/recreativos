"use client";

// Primitivo Calendar: envoltorio sobre react-day-picker (v10) con classNames
// mapeados a NUESTROS tokens. Patron shadcn oficial adaptado a la API v9+/v10
// (enum UI como claves de classNames, componente Chevron con `orientation`),
// porque este primitivo no esta en el spec de componentes.
import * as React from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { DayPicker } from "react-day-picker";
// El spec pide localizar con date-fns/locale; usamos `es` (espanol).
import { es } from "date-fns/locale";

import { cn } from "@/lib/utils";
import { buttonVariants } from "@/components/ui/button";

export type CalendarProps = React.ComponentProps<typeof DayPicker>;

/**
 * Calendario accesible (teclado/lectores) con estilos del sistema.
 * - Dia seleccionado: superficie de marca (`primary`).
 * - Dia de hoy: superficie de acento (`accent`).
 * - Navegacion: boton fantasma (`ghost`) reutilizando `buttonVariants`.
 */
function Calendar({ className, classNames, showOutsideDays = true, ...props }: CalendarProps) {
  return (
    <DayPicker
      // `es`: dias/meses/aria en espanol (idioma de UI del proyecto).
      locale={es}
      showOutsideDays={showOutsideDays}
      className={cn("p-3", className)}
      classNames={{
        months: "flex flex-col sm:flex-row gap-2",
        month: "flex flex-col gap-4",
        month_caption: "flex justify-center pt-1 relative items-center",
        caption_label: "text-label",
        nav: "flex items-center gap-1 absolute inset-x-0 top-0 justify-between px-1",
        button_previous: cn(
          buttonVariants({ variant: "ghost", size: "icon" }),
          "h-7 w-7 p-0 opacity-70 hover:opacity-100",
        ),
        button_next: cn(
          buttonVariants({ variant: "ghost", size: "icon" }),
          "h-7 w-7 p-0 opacity-70 hover:opacity-100",
        ),
        month_grid: "w-full border-collapse space-y-1",
        weekdays: "flex",
        weekday: "text-muted-foreground rounded-md w-9 font-normal text-caption",
        week: "flex w-full mt-2",
        // El dia (celda) absorbe los estados de rango para superficies continuas.
        day: cn(
          "relative p-0 text-center text-sm focus-within:relative focus-within:z-20",
          "has-aria-[selected]:bg-accent",
          "[&:has([aria-selected].day-range-end)]:rounded-r-md",
          "[&:has([aria-selected].day-outside)]:bg-accent/50",
          "first:has-aria-[selected]:rounded-l-md last:has-aria-[selected]:rounded-r-md",
        ),
        day_button: cn(
          buttonVariants({ variant: "ghost", size: "icon" }),
          "h-9 w-9 p-0 font-normal aria-selected:opacity-100",
        ),
        range_start: "day-range-start",
        range_end: "day-range-end",
        // Seleccion individual: superficie de marca, contraste con su foreground.
        selected: cn(
          "bg-primary text-primary-foreground",
          "hover:bg-primary hover:text-primary-foreground",
          "focus:bg-primary focus:text-primary-foreground",
        ),
        // Hoy: superficie de acento (sin depender solo del color; queda el numero).
        today: "bg-accent text-accent-foreground",
        outside: cn(
          "day-outside text-muted-foreground",
          "aria-selected:bg-accent/50 aria-selected:text-muted-foreground",
        ),
        disabled: "text-muted-foreground opacity-50",
        range_middle: "aria-selected:bg-accent aria-selected:text-accent-foreground",
        hidden: "invisible",
        ...classNames,
      }}
      components={{
        // En v9+ el icono se centraliza en `Chevron` (orientacion por nav/dropdown).
        Chevron: ({ orientation, className: chevronClassName, ...chevronProps }) => {
          const Icon = orientation === "left" ? ChevronLeft : ChevronRight;
          return <Icon className={cn("h-4 w-4", chevronClassName)} {...chevronProps} />;
        },
      }}
      {...props}
    />
  );
}
Calendar.displayName = "Calendar";

export { Calendar };
