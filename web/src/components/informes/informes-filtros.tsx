"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useTransition } from "react";

import { Button } from "@/components/ui/button";
import { FieldDate } from "@/components/common/date-field";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { LocalOpcion } from "@/lib/informes/types";

const LOCAL_TODOS = "todos";

interface InformesFiltrosProps {
  localInicial: string | null;
  desdeInicial: string;
  hastaInicial: string;
  locales: LocalOpcion[];
}

export function InformesFiltros({
  localInicial,
  desdeInicial,
  hastaInicial,
  locales,
}: InformesFiltrosProps) {
  const t = useTranslations("informes.filtros");
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
      const query = params.toString();
      router.replace(query ? `/informes?${query}` : "/informes");
    });
  }

  function limpiar() {
    startTransition(() => {
      router.replace("/informes");
    });
  }

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end">
      <div className="space-y-1">
        <Label htmlFor="filtro-local">{t("local")}</Label>
        <Select
          value={localInicial ?? LOCAL_TODOS}
          onValueChange={(value) => setParam("local", value === LOCAL_TODOS ? null : value)}
        >
          <SelectTrigger id="filtro-local" className="sm:w-64">
            <SelectValue placeholder={t("local")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={LOCAL_TODOS}>{t("todosLocales")}</SelectItem>
            {locales.map((local) => (
              <SelectItem key={local.id} value={local.id}>
                {local.nombre}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1">
        <Label htmlFor="filtro-desde">{t("desde")}</Label>
        <FieldDate
          id="filtro-desde"
          value={desdeInicial}
          max={hastaInicial}
          onChange={(value) => setParam("desde", value || null)}
          className="sm:w-44"
          density="compact"
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="filtro-hasta">{t("hasta")}</Label>
        <FieldDate
          id="filtro-hasta"
          value={hastaInicial}
          min={desdeInicial}
          onChange={(value) => setParam("hasta", value || null)}
          className="sm:w-44"
          density="compact"
        />
      </div>
      <Button type="button" variant="ghost" onClick={limpiar} className="sm:ml-auto">
        {t("limpiar")}
      </Button>
    </div>
  );
}
