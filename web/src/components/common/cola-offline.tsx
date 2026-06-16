import { CloudUpload } from "lucide-react";

import { cn } from "@/lib/utils";

interface ChipColaOfflineProps {
  /** Etiqueta; por defecto "Se enviará al sincronizar" (pasar `t(...)` si hay i18n). */
  label?: string;
  /** Nº de acciones encoladas; si es >1 añade el sufijo "· N" en mono tabular. */
  count?: number;
  /** Pulso lento del icono mientras la cola sigue sin sincronizar. */
  pending?: boolean;
  className?: string;
}

/**
 * Chip de cola offline (A-SNACKBAR-COLA, T-243): marcador **NEUTRO** —
 * `surface-2` + borde + `muted`, nunca danger/warning. Comunica «se enviará al
 * sincronizar» en acciones offline-first; el éxito real lo da el flash/toast
 * cuando el servidor confirma (refuerza el SSOT). No es interactivo ni proyecta
 * sombra (las sombras se reservan a overlays).
 */
export function ChipColaOffline({
  label = "Se enviará al sincronizar",
  count,
  pending = false,
  className,
}: ChipColaOfflineProps) {
  return (
    <span
      role="status"
      aria-live="polite"
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border border-border bg-surface-2 px-2.5 py-1",
        "text-caption text-muted-foreground",
        className,
      )}
    >
      <CloudUpload
        className={cn("size-3.5 shrink-0", pending && "motion-safe:animate-offline-pulse")}
        aria-hidden="true"
      />
      <span>{label}</span>
      {typeof count === "number" && count > 1 ? (
        <span className="font-mono tabular-nums text-foreground">· {count}</span>
      ) : null}
    </span>
  );
}
