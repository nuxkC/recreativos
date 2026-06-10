"use client";

import { Search } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useEffect, useState, useTransition } from "react";

import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ESTADOS_MAQUINA, type EstadoMaquina } from "@/lib/maquinas/types";

const ESTADO_TODOS = "todos";

interface MaquinasFiltersProps {
  busquedaInicial: string;
  estadoInicial: EstadoMaquina | null;
}

export function MaquinasFilters({ busquedaInicial, estadoInicial }: MaquinasFiltersProps) {
  const t = useTranslations("maquinas");
  const tEstado = useTranslations("maquinas.estado");
  const router = useRouter();
  const searchParams = useSearchParams();
  const [, startTransition] = useTransition();

  const [busqueda, setBusqueda] = useState(busquedaInicial);

  // Debounce de 250 ms para no martirizar al server con cada tecla.
  useEffect(() => {
    if (busqueda === busquedaInicial) return;
    const handle = setTimeout(() => {
      const params = new URLSearchParams(searchParams.toString());
      if (busqueda.trim().length === 0) {
        params.delete("q");
      } else {
        params.set("q", busqueda.trim());
      }
      startTransition(() => {
        router.replace(`/maquinas?${params.toString()}`);
      });
    }, 250);
    return () => clearTimeout(handle);
    // searchParams se omite a propósito: lo leemos sólo al disparar.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [busqueda, busquedaInicial]);

  function onEstadoChange(value: string) {
    const params = new URLSearchParams(searchParams.toString());
    if (value === ESTADO_TODOS) {
      params.delete("estado");
    } else {
      params.set("estado", value);
    }
    startTransition(() => {
      router.replace(`/maquinas?${params.toString()}`);
    });
  }

  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
      <div className="relative flex-1">
        <Search
          className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden
        />
        <Input
          type="search"
          inputMode="search"
          autoComplete="off"
          placeholder={t("filtros.buscarPlaceholder")}
          value={busqueda}
          onChange={(event) => setBusqueda(event.target.value)}
          className="pl-9"
          aria-label={t("filtros.buscar")}
        />
      </div>
      <Select value={estadoInicial ?? ESTADO_TODOS} onValueChange={onEstadoChange}>
        <SelectTrigger className="sm:w-48" aria-label={t("filtros.estado")}>
          <SelectValue placeholder={t("filtros.estado")} />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ESTADO_TODOS}>{t("filtros.todos")}</SelectItem>
          {ESTADOS_MAQUINA.map((estado) => (
            <SelectItem key={estado} value={estado}>
              {tEstado(estado)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
