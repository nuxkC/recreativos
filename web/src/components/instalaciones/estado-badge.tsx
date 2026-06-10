import { useTranslations } from "next-intl";

import { Badge, type BadgeProps } from "@/components/ui/badge";
import type { EstadoInstalacion } from "@/lib/instalaciones/types";

const VARIANT_BY_ESTADO: Record<EstadoInstalacion, BadgeProps["variant"]> = {
  activa: "success",
  cerrada: "muted",
};

interface EstadoInstalacionBadgeProps {
  estado: EstadoInstalacion;
}

export function EstadoInstalacionBadge({ estado }: EstadoInstalacionBadgeProps) {
  const t = useTranslations("instalaciones.estado");
  return <Badge variant={VARIANT_BY_ESTADO[estado]}>{t(estado)}</Badge>;
}
