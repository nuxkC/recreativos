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
import { ESTADOS_RECAUDACION } from "@/lib/recaudaciones/types";

const ESTADO_TODOS = "todos";
const LOCAL_TODOS = "todos";
const ESTADO_FILTROS = [...ESTADOS_RECAUDACION, "conflicto"] as const;

interface RecaudacionesFiltersProps {
  estadoInicial: (typeof ESTADO_FILTROS)[number] | null;
  localInicial: string | null;
  desdeInicial: string | null;
  hastaInicial: string | null;
  locales: Array<{ id: string; nombre: string }>;
  /** Si viene del query (ej. desde /instalaciones/[id]), se mantiene fijo. */
  instalacionFijaId: string | null;
}

export function RecaudacionesFilters({
  estadoInicial,
  localInicial,
  desdeInicial,
  hastaInicial,
  locales,
  instalacionFijaId,
}: RecaudacionesFiltersProps) {
  const t = useTranslations("recaudaciones");
  const tEstado = useTranslations("recaudaciones.estado");
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
      router.replace(`/recaudaciones?${params.toString()}`);
    });
  }

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end">
      <div className="space-y-1">
        <Label htmlFor="filtro-estado">{t("filtros.estado")}</Label>
        <Select
          value={estadoInicial ?? ESTADO_TODOS}
          onValueChange={(value) => setParam("estado", value === ESTADO_TODOS ? null : value)}
        >
          <SelectTrigger id="filtro-estado" className="sm:w-48">
            <SelectValue placeholder={t("filtros.estado")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ESTADO_TODOS}>{t("filtros.todos")}</SelectItem>
            {ESTADO_FILTROS.map((estado) => (
              <SelectItem key={estado} value={estado}>
                {tEstado(estado)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {instalacionFijaId ? null : (
        <div className="space-y-1">
          <Label htmlFor="filtro-local">{t("filtros.local")}</Label>
          <Select
            value={localInicial ?? LOCAL_TODOS}
            onValueChange={(value) => setParam("local", value === LOCAL_TODOS ? null : value)}
          >
            <SelectTrigger id="filtro-local" className="sm:w-64">
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
        <Label htmlFor="filtro-desde">{t("filtros.desde")}</Label>
        <Input
          id="filtro-desde"
          type="date"
          value={desdeInicial ?? ""}
          onChange={(event) => setParam("desde", event.target.value || null)}
          className="sm:w-44"
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="filtro-hasta">{t("filtros.hasta")}</Label>
        <Input
          id="filtro-hasta"
          type="date"
          value={hastaInicial ?? ""}
          onChange={(event) => setParam("hasta", event.target.value || null)}
          className="sm:w-44"
        />
      </div>
    </div>
  );
}
