"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useTransition } from "react";

import { FieldDate } from "@/components/common/date-field";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ACCIONES_AUDITORIA, ENTIDADES_AUDITORIA } from "@/lib/auditoria/types";

const TODAS = "todas";

interface AuditoriaFiltersProps {
  accionInicial: string | null;
  entidadInicial: string | null;
  desdeInicial: string | null;
  hastaInicial: string | null;
}

export function AuditoriaFilters({
  accionInicial,
  entidadInicial,
  desdeInicial,
  hastaInicial,
}: AuditoriaFiltersProps) {
  const t = useTranslations("auditoria");
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
      router.replace(`/auditoria?${params.toString()}`);
    });
  }

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end">
      <div className="space-y-1">
        <Label htmlFor="filtro-audit-accion">{t("filtros.accion")}</Label>
        <Select
          value={accionInicial ?? TODAS}
          onValueChange={(value) => setParam("accion", value === TODAS ? null : value)}
        >
          <SelectTrigger id="filtro-audit-accion" className="sm:w-64">
            <SelectValue placeholder={t("filtros.accion")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={TODAS}>{t("filtros.todasAcciones")}</SelectItem>
            {ACCIONES_AUDITORIA.map((accion) => (
              <SelectItem key={accion} value={accion}>
                {t(`acciones.${accion}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1">
        <Label htmlFor="filtro-audit-entidad">{t("filtros.entidad")}</Label>
        <Select
          value={entidadInicial ?? TODAS}
          onValueChange={(value) => setParam("entidad", value === TODAS ? null : value)}
        >
          <SelectTrigger id="filtro-audit-entidad" className="sm:w-52">
            <SelectValue placeholder={t("filtros.entidad")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={TODAS}>{t("filtros.todasEntidades")}</SelectItem>
            {ENTIDADES_AUDITORIA.map((entidad) => (
              <SelectItem key={entidad} value={entidad}>
                {t(`entidades.${entidad}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1">
        <Label htmlFor="filtro-audit-desde">{t("filtros.desde")}</Label>
        <FieldDate
          id="filtro-audit-desde"
          value={desdeInicial ?? ""}
          onChange={(value) => setParam("desde", value || null)}
          className="sm:w-44"
          density="compact"
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="filtro-audit-hasta">{t("filtros.hasta")}</Label>
        <FieldDate
          id="filtro-audit-hasta"
          value={hastaInicial ?? ""}
          onChange={(value) => setParam("hasta", value || null)}
          className="sm:w-44"
          density="compact"
        />
      </div>
    </div>
  );
}
