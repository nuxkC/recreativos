"use client";

import { Building2, Check, ChevronsUpDown } from "lucide-react";
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
import type { Rol } from "@/lib/auth/roles";
import { seleccionarEmpresa } from "@/lib/empresas/actions";
import type { Membresia } from "@/lib/empresas/types";
import { cn } from "@/lib/utils";

interface EmpresaSwitcherProps {
  empresaActivaId: string;
  rolActivo: Rol;
  empresaActivaNombre: string;
  membresias: Membresia[];
}

export function EmpresaSwitcher({
  empresaActivaId,
  rolActivo,
  empresaActivaNombre,
  membresias,
}: EmpresaSwitcherProps) {
  const t = useTranslations();
  const [pending, startTransition] = useTransition();

  if (membresias.length <= 1) {
    return (
      <div className="flex items-center gap-2 text-sm">
        <Building2 className="size-4 text-muted-foreground" aria-hidden />
        <div className="flex flex-col leading-tight">
          <span className="font-medium">{empresaActivaNombre}</span>
          <span className="text-xs text-muted-foreground">{t(`roles.${rolActivo}`)}</span>
        </div>
      </div>
    );
  }

  function onSelect(empresaId: string) {
    if (empresaId === empresaActivaId || pending) return;
    const formData = new FormData();
    formData.set("empresaId", empresaId);
    startTransition(async () => {
      await seleccionarEmpresa(formData);
    });
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className="min-w-48 justify-between"
          disabled={pending}
          aria-label={t("layout.switchEmpresa")}
        >
          <span className="flex items-center gap-2 truncate">
            <Building2 className="size-4 text-muted-foreground" aria-hidden />
            <span className="truncate">{empresaActivaNombre}</span>
          </span>
          <ChevronsUpDown className="size-4 text-muted-foreground" aria-hidden />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-64">
        <DropdownMenuLabel>{t("layout.tusEmpresas")}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {membresias.map((m) => {
          const activa = m.empresa.id === empresaActivaId;
          return (
            <DropdownMenuItem
              key={m.empresa.id}
              onSelect={(event) => {
                event.preventDefault();
                onSelect(m.empresa.id);
              }}
              className="cursor-pointer"
            >
              <Check className={cn("size-4", activa ? "opacity-100" : "opacity-0")} aria-hidden />
              <div className="flex min-w-0 flex-col">
                <span className="truncate">{m.empresa.nombre}</span>
                <span className="truncate text-xs text-muted-foreground">
                  {t(`roles.${m.rol}`)}
                </span>
              </div>
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
