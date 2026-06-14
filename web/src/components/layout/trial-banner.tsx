import { AlertTriangle, Clock } from "lucide-react";
import { getTranslations } from "next-intl/server";

import { calcularInfoTrial, type EstadoSuscripcion } from "@/lib/suscripcion/trial";
import { cn } from "@/lib/utils";

interface TrialBannerProps {
  estadoSuscripcion: EstadoSuscripcion;
  trialFin: string | null;
}

/**
 * Aviso informativo del periodo de prueba (T-200).
 *
 * Solo se muestra cuando la empresa está en `trial`. Indica los días restantes
 * y cambia de tono cuando el trial está por expirar o ya expiró. NO bloquea
 * ninguna acción: el bloqueo por facturación es T-201.
 */
export async function TrialBanner({ estadoSuscripcion, trialFin }: TrialBannerProps) {
  if (estadoSuscripcion !== "trial" || !trialFin) {
    return null;
  }

  const t = await getTranslations("trial");
  const info = calcularInfoTrial(trialFin);

  const esExpirado = info.estado === "expirado";
  const esAviso = info.estado !== "vigente";

  const mensaje = esExpirado ? t("expirado") : t("diasRestantes", { dias: info.diasRestantes });

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "flex items-center gap-2 border-b px-4 py-2 text-sm md:px-6",
        esExpirado
          ? "border-destructive/30 bg-destructive/10 text-destructive"
          : esAviso
            ? "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400"
            : "border-primary/20 bg-primary/5 text-foreground",
      )}
    >
      {esAviso ? (
        <AlertTriangle className="size-4 shrink-0" aria-hidden />
      ) : (
        <Clock className="size-4 shrink-0" aria-hidden />
      )}
      <span>
        <span className="font-medium">{t("etiqueta")}</span> {mensaje}
      </span>
    </div>
  );
}
