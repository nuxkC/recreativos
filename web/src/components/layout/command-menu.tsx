"use client";

import { Search } from "lucide-react";
import * as React from "react";

import { CommandPalette } from "@/components/common/command-palette";
import type { Rol } from "@/lib/auth/roles";

/**
 * Disparador visible de la paleta de comandos (⌘K) en el TopBar · T-237.
 *
 * El TopBar es Server Component; este pequeño cliente posee el estado `open` y
 * lo comparte con la `CommandPalette` en modo controlado. El atajo de teclado lo
 * sigue gestionando la propia paleta (escucha ⌘K/Ctrl+K en el documento), así
 * que este botón es solo un *affordance* descubrible: la mayoría de usuarios no
 * conoce el atajo, y la IA (T-11) exige una vía visible a la búsqueda global.
 */
export function CommandMenu({ rol }: { rol: Rol }) {
  const [open, setOpen] = React.useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-keyshortcuts="Meta+K Control+K"
        aria-label="Buscar o ejecutar una acción"
        className="bg-muted/40 border-input text-muted-foreground hover:bg-muted focus-visible:ring-ring flex h-9 items-center gap-2 rounded-md border px-2.5 text-sm transition-colors focus-visible:ring-2 focus-visible:outline-hidden sm:min-w-44"
      >
        <Search className="size-4 shrink-0" aria-hidden="true" />
        <span className="hidden sm:inline">Buscar o ejecutar…</span>
        <kbd className="bg-background text-muted-foreground ml-auto hidden rounded border px-1.5 font-mono text-[10px] sm:inline-block">
          ⌘K
        </kbd>
      </button>
      <CommandPalette rol={rol} open={open} onOpenChange={setOpen} />
    </>
  );
}
