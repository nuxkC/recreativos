import { useTranslations } from "next-intl";

import { Badge, type BadgeProps } from "@/components/ui/badge";
import type { EstadoLicencia } from "@/lib/licencias/types";

const VARIANT_BY_ESTADO: Record<EstadoLicencia, BadgeProps["variant"]> = {
  activa: "success",
  suspendida: "warning",
  caducada: "destructive",
  baja: "muted",
};

interface EstadoLicenciaBadgeProps {
  estado: EstadoLicencia;
}

export function EstadoLicenciaBadge({ estado }: EstadoLicenciaBadgeProps) {
  const t = useTranslations("licencias.estado");
  return <Badge variant={VARIANT_BY_ESTADO[estado]}>{t(estado)}</Badge>;
}
