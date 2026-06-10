"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useTransition } from "react";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ESTADOS_INSTALACION, type EstadoInstalacion } from "@/lib/instalaciones/types";
import type { LocalResumen } from "@/lib/instalaciones/types";

const ESTADO_TODOS = "todos";
const LOCAL_TODOS = "todos";

interface InstalacionesFiltersProps {
  estadoInicial: EstadoInstalacion | null;
  localInicial: string | null;
  locales: LocalResumen[];
}

export function InstalacionesFilters({
  estadoInicial,
  localInicial,
  locales,
}: InstalacionesFiltersProps) {
  const t = useTranslations("instalaciones");
  const tEstado = useTranslations("instalaciones.estado");
  const router = useRouter();
  const searchParams = useSearchParams();
  const [, startTransition] = useTransition();

  function setParam(key: string, value: string | null) {
    const params = new URLSearchParams(searchParams.toString());
    if (value === null || value === "") {
      params.delete(key);
    } else {
      params.set(key, value);
    }
    startTransition(() => {
      router.replace(`/instalaciones?${params.toString()}`);
    });
  }

  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
      <Select
        value={estadoInicial ?? ESTADO_TODOS}
        onValueChange={(value) => setParam("estado", value === ESTADO_TODOS ? null : value)}
      >
        <SelectTrigger className="sm:w-48" aria-label={t("filtros.estado")}>
          <SelectValue placeholder={t("filtros.estado")} />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ESTADO_TODOS}>{t("filtros.todosEstados")}</SelectItem>
          {ESTADOS_INSTALACION.map((estado) => (
            <SelectItem key={estado} value={estado}>
              {tEstado(estado)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select
        value={localInicial ?? LOCAL_TODOS}
        onValueChange={(value) => setParam("local", value === LOCAL_TODOS ? null : value)}
      >
        <SelectTrigger className="sm:w-64" aria-label={t("filtros.local")}>
          <SelectValue placeholder={t("filtros.local")} />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={LOCAL_TODOS}>{t("filtros.todosLocales")}</SelectItem>
          {locales.map((local) => (
            <SelectItem key={local.id} value={local.id}>
              {local.nombre}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
