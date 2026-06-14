"use client";

import { Monitor, Moon, Sun } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

/**
 * Conmutador de tema (claro / oscuro / sistema).
 *
 * - Iconos con `aria-label` traducido (sin texto hardcodeado).
 * - Antes de la hidratación renderizamos un botón neutro para evitar el
 *   desajuste de iconos entre servidor y cliente (el tema real solo se
 *   conoce en el navegador, donde vive `localStorage`).
 */
export function ThemeToggle() {
  const t = useTranslations("tema");
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={t("toggleLabel")}>
          {mounted && theme === "dark" ? (
            <Moon className="size-4" aria-hidden />
          ) : mounted && theme === "light" ? (
            <Sun className="size-4" aria-hidden />
          ) : (
            <Monitor className="size-4" aria-hidden />
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-44">
        <DropdownMenuLabel>{t("titulo")}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuRadioGroup value={mounted ? theme : undefined} onValueChange={setTheme}>
          <DropdownMenuRadioItem value="light" className="cursor-pointer gap-2">
            <Sun className="size-4" aria-hidden />
            <span>{t("claro")}</span>
          </DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="dark" className="cursor-pointer gap-2">
            <Moon className="size-4" aria-hidden />
            <span>{t("oscuro")}</span>
          </DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="system" className="cursor-pointer gap-2">
            <Monitor className="size-4" aria-hidden />
            <span>{t("sistema")}</span>
          </DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
