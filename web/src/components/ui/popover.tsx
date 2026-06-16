"use client";

import * as React from "react";
import * as PopoverPrimitive from "@radix-ui/react-popover";

import { cn } from "@/lib/utils";

const Popover = PopoverPrimitive.Root;

const PopoverTrigger = PopoverPrimitive.Trigger;

// Anchor: ancla opcional para posicionar el contenido respecto a otro elemento
// (no el trigger). Útil cuando el disparador y el punto de anclaje difieren.
const PopoverAnchor = PopoverPrimitive.Anchor;

const PopoverContent = React.forwardRef<
  React.ElementRef<typeof PopoverPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof PopoverPrimitive.Content>
>(({ className, align = "center", sideOffset = 4, ...props }, ref) => (
  // Portal: el contenido se monta fuera del flujo para evitar recortes por
  // overflow y mantener el orden de apilamiento sobre el resto de la UI.
  <PopoverPrimitive.Portal>
    <PopoverPrimitive.Content
      ref={ref}
      align={align}
      sideOffset={sideOffset}
      className={cn(
        // Superficie de overlay: surface-1 + borde 1px + sombra de overlay
        // (la elevación vive en la sombra, no en un borde reforzado).
        "border-border bg-surface-1 p-grid-4 text-popover-foreground shadow-overlay z-50 w-72 rounded-md border outline-hidden",
        // Entrada/salida con tailwindcss-animate, guiadas por data-state/side.
        "data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2",
        className,
      )}
      {...props}
    />
  </PopoverPrimitive.Portal>
));
PopoverContent.displayName = PopoverPrimitive.Content.displayName;

export { Popover, PopoverTrigger, PopoverContent, PopoverAnchor };
