"use client";

import { AlertTriangle, Ban, Bell, CalendarX, Check, Loader2, Store } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { marcarAlertaComoLeida } from "@/lib/dashboard/actions";
import type { AlertaPendiente } from "@/lib/dashboard/queries";
import { formatDateTime } from "@/lib/recaudaciones/format";
import { cn } from "@/lib/utils";

const ICON_BY_TIPO: Record<string, typeof AlertTriangle> = {
  recaudacion_conflicto: AlertTriangle,
  licencia_caducidad: CalendarX,
  local_sin_recaudar: Store,
  recaudacion_anulada: Ban,
};

interface AlertasListProps {
  alertas: AlertaPendiente[];
}

export function AlertasList({ alertas }: AlertasListProps) {
  const t = useTranslations("dashboard.alertas");
  const tErrores = useTranslations("dashboard.errores");
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [, startTransition] = useTransition();

  function onMarcar(id: string) {
    setPendingId(id);
    startTransition(async () => {
      try {
        const result = await marcarAlertaComoLeida(id);
        if (!result.ok) {
          toast.error(tErrores("actualizarFallido"));
        }
      } finally {
        setPendingId(null);
      }
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <Bell className="size-4" aria-hidden />
          {t("titulo")}
        </CardTitle>
        <CardDescription>{t("descripcion")}</CardDescription>
      </CardHeader>
      <CardContent>
        {alertas.length === 0 ? (
          <p className="rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground">
            {t("vacio")}
          </p>
        ) : (
          <ul className="divide-y rounded-md border">
            {alertas.map((alerta) => {
              const Icon = ICON_BY_TIPO[alerta.tipo] ?? Bell;
              const procesando = pendingId === alerta.id;
              return (
                <li key={alerta.id} className="flex items-start gap-3 p-3 text-sm">
                  <Icon
                    className={cn(
                      "mt-0.5 size-4 shrink-0",
                      alerta.tipo === "recaudacion_conflicto" && "text-warning-text",
                      alerta.tipo === "recaudacion_anulada" && "text-destructive",
                      alerta.tipo === "licencia_caducidad" && "text-warning-text",
                      alerta.tipo === "local_sin_recaudar" && "text-muted-foreground",
                    )}
                    aria-hidden
                  />
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <p className="break-words">{alerta.mensaje}</p>
                    <p className="text-xs text-muted-foreground">
                      {formatDateTime(alerta.creadaEn)}
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onMarcar(alerta.id)}
                    disabled={procesando}
                    aria-label={t("marcarLeida")}
                    className="shrink-0"
                  >
                    {procesando ? (
                      <Loader2 className="size-4 animate-spin" aria-hidden />
                    ) : (
                      <Check className="size-4" aria-hidden />
                    )}
                  </Button>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
