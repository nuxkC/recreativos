import { useTranslations } from "next-intl";

import { Badge, type BadgeProps } from "@/components/ui/badge";
import type { EstadoAveria } from "@/lib/averias/types";

const VARIANT_BY_ESTADO: Record<EstadoAveria, BadgeProps["variant"]> = {
  abierta: "warning",
  en_reparacion: "secondary",
  resuelta: "success",
};

interface EstadoAveriaBadgeProps {
  estado: EstadoAveria;
}

export function EstadoAveriaBadge({ estado }: EstadoAveriaBadgeProps) {
  const t = useTranslations("averias.estado");
  return <Badge variant={VARIANT_BY_ESTADO[estado]}>{t(estado)}</Badge>;
}
