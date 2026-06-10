import { AlertTriangle } from "lucide-react";
import { useTranslations } from "next-intl";

import { Badge } from "@/components/ui/badge";
import type { EstadoRecaudacion } from "@/lib/recaudaciones/types";

interface EstadoRecaudacionBadgesProps {
  estado: EstadoRecaudacion;
  conflicto: boolean;
  /** Si la resolución del conflicto ya se ha aplicado, ocultamos el badge. */
  conflictoResuelto?: boolean;
}

export function EstadoRecaudacionBadges({
  estado,
  conflicto,
  conflictoResuelto,
}: EstadoRecaudacionBadgesProps) {
  const t = useTranslations("recaudaciones.estado");
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <Badge variant={estado === "firme" ? "success" : "muted"}>{t(estado)}</Badge>
      {conflicto && !conflictoResuelto ? (
        <Badge variant="warning" className="gap-1">
          <AlertTriangle className="size-3" aria-hidden />
          {t("conflicto")}
        </Badge>
      ) : null}
    </div>
  );
}
