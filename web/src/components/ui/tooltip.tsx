"use client";

// Primitivo shadcn/Radix sobre @radix-ui/react-tooltip.
// Overlay NEUTRO de alto contraste (inverse-surface): en light la burbuja es
// oscura, en dark es surface-2. Es PROGRESIVO y redundante — aclara algo ya
// codificado de otra forma (aria-label) y NUNCA encierra info crítica única.
// Rol semánticamente neutro: jamás success/danger/warning/info/primary/secondary
// para "comunicar estado" (eso es StatusChip/MoneyText).

import * as React from "react";
import * as TooltipPrimitive from "@radix-ui/react-tooltip";

import { cn } from "@/lib/utils";

// Envuelve la app (o el subárbol) una vez. delayDuration={400} evita parpadeo al
// barrer iconos; skipDelayDuration={300} abre sin delay tooltips vecinos.
const TooltipProvider = TooltipPrimitive.Provider;

const Tooltip = TooltipPrimitive.Root;

const TooltipTrigger = TooltipPrimitive.Trigger;

const TooltipContent = React.forwardRef<
  React.ElementRef<typeof TooltipPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof TooltipPrimitive.Content>
>(({ className, sideOffset = 4, ...props }, ref) => (
  <TooltipPrimitive.Portal>
    <TooltipPrimitive.Content
      ref={ref}
      sideOffset={sideOffset}
      className={cn(
        // Burbuja compacta: caption en <=2 líneas, no es una card.
        "z-50 max-w-[240px] overflow-hidden rounded-md px-2 py-1",
        "text-caption", // tipografía caption del sistema (12/500)
        // Overlay neutro (inverse-surface): el repo no define --inverse-surface
        // / --on-inverse, así que usamos el PAR inverso real de roles:
        //   light: bg-foreground (#11161B) + text-background (#FAFBFC) = 16.1:1
        //   dark : bg-surface-2 (#1B1E24) + text-foreground (#E7EAEE) = 11.9:1
        "bg-foreground text-background dark:bg-surface-2 dark:text-foreground",
        // Sombra SOLO de overlay (token real); el resto de superficies no la lleva.
        "shadow-overlay",
        // fade + 4px, 120ms, ease.standard, sin rebote. El flip por colisión no
        // se anima (snap). Radix emite data-state=delayed-open en apertura por hover.
        "data-[state=delayed-open]:animate-in data-[state=instant-open]:animate-in data-[state=closed]:animate-out",
        "data-[state=closed]:fade-out-0 data-[state=delayed-open]:fade-in-0 data-[state=instant-open]:fade-in-0",
        "data-[side=bottom]:slide-in-from-top-1 data-[side=top]:slide-in-from-bottom-1",
        "data-[side=left]:slide-in-from-right-1 data-[side=right]:slide-in-from-left-1",
        "duration-100 ease-standard",
        // reduced-motion: snap, sin animación.
        "motion-reduce:animate-none motion-reduce:transition-none",
        className,
      )}
      {...props}
    />
  </TooltipPrimitive.Portal>
));
TooltipContent.displayName = TooltipPrimitive.Content.displayName;

// Flecha/cola decorativa OPCIONAL (8x4). aria-hidden lo gestiona Radix; hereda el
// fill del overlay para no romper el contraste de la burbuja.
const TooltipArrow = React.forwardRef<
  React.ElementRef<typeof TooltipPrimitive.Arrow>,
  React.ComponentPropsWithoutRef<typeof TooltipPrimitive.Arrow>
>(({ className, width = 8, height = 4, ...props }, ref) => (
  <TooltipPrimitive.Arrow
    ref={ref}
    width={width}
    height={height}
    className={cn("fill-foreground dark:fill-surface-2", className)}
    {...props}
  />
));
TooltipArrow.displayName = TooltipPrimitive.Arrow.displayName;

export { Tooltip, TooltipTrigger, TooltipContent, TooltipProvider, TooltipArrow };
