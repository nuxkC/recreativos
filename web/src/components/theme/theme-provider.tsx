"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";
import type { ComponentProps, ReactNode } from "react";

/**
 * Envoltura de `next-themes` para todo el árbol de la app.
 *
 * Usa la estrategia `class` (coherente con `darkMode: ["class"]` en
 * `tailwind.config.ts`): el tema activo se aplica como clase en `<html>`.
 * La preferencia se persiste en `localStorage` y, con `enableSystem`, el
 * valor por defecto respeta `prefers-color-scheme` del sistema operativo.
 */
type ThemeProviderProps = ComponentProps<typeof NextThemesProvider> & {
  children: ReactNode;
};

export function ThemeProvider({ children, ...props }: ThemeProviderProps) {
  return <NextThemesProvider {...props}>{children}</NextThemesProvider>;
}
