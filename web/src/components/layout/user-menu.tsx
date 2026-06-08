"use client";

import { LogOut, RefreshCcw, User } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTransition } from "react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { limpiarEmpresaActiva } from "@/lib/empresas/actions";

interface UserMenuProps {
  email: string;
  /** Cuántas empresas activas tiene el usuario; si es 1 ocultamos "cambiar". */
  totalMembresias: number;
}

export function UserMenu({ email, totalMembresias }: UserMenuProps) {
  const t = useTranslations();
  const [pending, startTransition] = useTransition();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          aria-label={t("layout.userMenu")}
          className="gap-2"
        >
          <User className="size-4" aria-hidden />
          <span className="max-w-44 truncate text-sm">{email}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-56">
        <DropdownMenuLabel className="font-normal">
          <div className="flex flex-col">
            <span className="text-xs uppercase tracking-wide text-muted-foreground">
              {t("layout.sesion")}
            </span>
            <span className="truncate text-sm">{email}</span>
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {totalMembresias > 1 ? (
          <DropdownMenuItem
            disabled={pending}
            onSelect={(event) => {
              event.preventDefault();
              startTransition(async () => {
                await limpiarEmpresaActiva();
              });
            }}
            className="cursor-pointer"
          >
            <RefreshCcw className="size-4" aria-hidden />
            <span>{t("layout.cambiarEmpresa")}</span>
          </DropdownMenuItem>
        ) : null}
        <form action="/auth/signout" method="post">
          <DropdownMenuItem asChild>
            <button
              type="submit"
              className="w-full cursor-pointer text-destructive focus:text-destructive"
            >
              <LogOut className="size-4" aria-hidden />
              <span>{t("auth.signOut")}</span>
            </button>
          </DropdownMenuItem>
        </form>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
