"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useTransition } from "react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const LOCAL_TODOS = "todos";

interface CambiosPlacaFiltersProps {
  localInicial: string | null;
  desdeInicial: string | null;
  hastaInicial: string | null;
  locales: Array<{ id: string; nombre: string }>;
  /** Si la URL trae ?instalacion=, ocultamos el filtro de local. */
  instalacionFijaId: string | null;
}

export function CambiosPlacaFilters({
  localInicial,
  desdeInicial,
  hastaInicial,
  locales,
  instalacionFijaId,
}: CambiosPlacaFiltersProps) {
  const t = useTranslations("cambiosPlaca");
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
      router.replace(`/cambios-placa?${params.toString()}`);
    });
  }

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end">
      {instalacionFijaId ? null : (
        <div className="space-y-1">
          <Label htmlFor="filtro-cp-local">{t("filtros.local")}</Label>
          <Select
            value={localInicial ?? LOCAL_TODOS}
            onValueChange={(value) => setParam("local", value === LOCAL_TODOS ? null : value)}
          >
            <SelectTrigger id="filtro-cp-local" className="sm:w-64">
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
      )}
      <div className="space-y-1">
        <Label htmlFor="filtro-cp-desde">{t("filtros.desde")}</Label>
        <Input
          id="filtro-cp-desde"
          type="date"
          value={desdeInicial ?? ""}
          onChange={(event) => setParam("desde", event.target.value || null)}
          className="sm:w-44"
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="filtro-cp-hasta">{t("filtros.hasta")}</Label>
        <Input
          id="filtro-cp-hasta"
          type="date"
          value={hastaInicial ?? ""}
          onChange={(event) => setParam("hasta", event.target.value || null)}
          className="sm:w-44"
        />
      </div>
    </div>
  );
}
