"use client";

// Primitivo Drawer/Sheet (C-DRAWER-SHEET-01) sobre @radix-ui/react-dialog.
// El spec menciona `vaul` para el slide lateral, pero no está instalado: se
// adapta a Radix Dialog (mismo rol/aria-modal y focus trap nativo) y el slide
// se hace con utilidades de tailwindcss-animate por variante `side`.
// Solo es el primitivo: header/body/footer pegajosos, confirmación destructiva,
// dirty-guard y estado de guardado viven en el componente de feature que lo usa.

import * as DialogPrimitive from "@radix-ui/react-dialog";
import { cva, type VariantProps } from "class-variance-authority";
import { X } from "lucide-react";
import * as React from "react";

import { cn } from "@/lib/utils";

const Sheet = DialogPrimitive.Root;

const SheetTrigger = DialogPrimitive.Trigger;

const SheetClose = DialogPrimitive.Close;

const SheetPortal = DialogPrimitive.Portal;

/**
 * Scrim: oscurece la lista de detrás (no se desmonta) a pantalla completa.
 * No existe token `color-scrim` en globals.css; el spec lo define como negro
 * 80%, así que se usa `bg-black/80` (igual que el overlay del dialog actual).
 */
const SheetOverlay = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Overlay>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Overlay
    ref={ref}
    className={cn(
      "fixed inset-0 z-50 bg-black/80",
      "data-[state=open]:animate-in data-[state=closed]:animate-out",
      "data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0",
      className,
    )}
    {...props}
  />
));
SheetOverlay.displayName = DialogPrimitive.Overlay.displayName;

/**
 * Panel anclado a un borde. surface-1 + shadow-modal (la elevación es del
 * overlay; el cuerpo no lleva sombra dura). Borde 1px contra la lista, radio
 * `xl` (16px) solo en las esquinas internas que flotan; las pegadas a 0.
 * Por defecto `right` (drawer de alta-edición); en móvil se ancla abajo.
 */
const sheetVariants = cva(
  cn(
    "fixed z-50 flex flex-col bg-surface-1 shadow-modal",
    "transition ease-standard",
    "data-[state=open]:animate-in data-[state=closed]:animate-out",
    "data-[state=open]:duration-200 data-[state=closed]:duration-150",
    "focus:outline-none",
  ),
  {
    variants: {
      side: {
        right:
          "inset-y-0 right-0 h-full w-full border-l sm:max-w-[480px] sm:rounded-l-xl " +
          "data-[state=open]:slide-in-from-right data-[state=closed]:slide-out-to-right",
        left:
          "inset-y-0 left-0 h-full w-full border-r sm:max-w-[480px] sm:rounded-r-xl " +
          "data-[state=open]:slide-in-from-left data-[state=closed]:slide-out-to-left",
        bottom:
          "inset-x-0 bottom-0 max-h-[85dvh] w-full border-t rounded-t-xl " +
          "data-[state=open]:slide-in-from-bottom data-[state=closed]:slide-out-to-bottom",
        top:
          "inset-x-0 top-0 max-h-[85dvh] w-full border-b rounded-b-xl " +
          "data-[state=open]:slide-in-from-top data-[state=closed]:slide-out-to-top",
      },
    },
    defaultVariants: {
      side: "right",
    },
  },
);

interface SheetContentProps
  extends React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content>,
    VariantProps<typeof sheetVariants> {
  /** Oculta la X por defecto cuando la feature aporta su propio botón de cierre. */
  hideClose?: boolean;
}

const SheetContent = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Content>,
  SheetContentProps
>(({ side = "right", className, children, hideClose = false, ...props }, ref) => (
  <SheetPortal>
    <SheetOverlay />
    <DialogPrimitive.Content
      ref={ref}
      className={cn(sheetVariants({ side }), className)}
      {...props}
    >
      {children}
      {!hideClose ? (
        <DialogPrimitive.Close
          // Touch ≥40px (esquina superior derecha); icono 16px centrado.
          className={cn(
            "absolute right-4 top-4 inline-flex h-10 w-10 items-center justify-center rounded-md",
            "text-muted-foreground opacity-70 transition-opacity hover:opacity-100",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
            "disabled:pointer-events-none",
          )}
        >
          <X className="h-4 w-4" aria-hidden="true" />
          <span className="sr-only">Cerrar</span>
        </DialogPrimitive.Close>
      ) : null}
    </DialogPrimitive.Content>
  </SheetPortal>
));
SheetContent.displayName = DialogPrimitive.Content.displayName;

/**
 * Header pegajoso (56px) con borde inferior 1px. Reserva hueco a la derecha
 * para no solapar el botón de cierre (pr-12).
 */
const SheetHeader = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div
    className={cn(
      "sticky top-0 z-10 flex min-h-14 shrink-0 flex-col justify-center gap-1",
      "border-b border-border bg-surface-1 px-6 py-4 pr-12",
      className,
    )}
    {...props}
  />
);
SheetHeader.displayName = "SheetHeader";

/**
 * Cuerpo con scroll independiente (flex-1). Padding 24px; los hijos se separan
 * con su propio gap a nivel de feature.
 */
const SheetBody = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn("flex-1 overflow-y-auto px-6 py-6", className)} {...props} />
);
SheetBody.displayName = "SheetBody";

/**
 * Footer pegajoso (64px) con borde superior 1px. Por defecto agrupa CTAs a la
 * derecha; una acción destructiva (Eliminar ghost) se ancla con `justify-between`.
 */
const SheetFooter = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div
    className={cn(
      "sticky bottom-0 z-10 flex min-h-16 shrink-0 items-center justify-end gap-2",
      "border-t border-border bg-surface-1 px-6 py-4",
      className,
    )}
    {...props}
  />
);
SheetFooter.displayName = "SheetFooter";

const SheetTitle = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Title>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Title
    ref={ref}
    className={cn("text-h2 text-foreground", className)}
    {...props}
  />
));
SheetTitle.displayName = DialogPrimitive.Title.displayName;

const SheetDescription = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Description>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Description
    ref={ref}
    className={cn("text-caption text-muted-foreground", className)}
    {...props}
  />
));
SheetDescription.displayName = DialogPrimitive.Description.displayName;

export {
  Sheet,
  SheetPortal,
  SheetOverlay,
  SheetTrigger,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetBody,
  SheetFooter,
  SheetTitle,
  SheetDescription,
};
