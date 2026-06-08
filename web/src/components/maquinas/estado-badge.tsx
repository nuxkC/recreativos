import { useTranslations } from "next-intl";

import { Badge, type BadgeProps } from "@/components/ui/badge";
import type { EstadoMaquina } from "@/lib/maquinas/types";

const VARIANT_BY_ESTADO: Record<EstadoMaquina, BadgeProps["variant"]> = {
  instalada: "success",
  almacen: "muted",
  averiada: "warning",
  baja: "destructive",
};

interface EstadoMaquinaBadgeProps {
  estado: EstadoMaquina;
}

export function EstadoMaquinaBadge({ estado }: EstadoMaquinaBadgeProps) {
  const t = useTranslations("maquinas.estado");
  return <Badge variant={VARIANT_BY_ESTADO[estado]}>{t(estado)}</Badge>;
}
