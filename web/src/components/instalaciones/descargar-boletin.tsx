"use client";

import { FileText, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { obtenerSignedUrlBoletin } from "@/lib/instalaciones/actions";

interface DescargarBoletinProps {
  instalacionId: string;
}

/**
 * Botón para generar/descargar el boletín digital de instalación.
 *
 * La generación es idempotente en servidor: la primera vez crea el PDF, las
 * siguientes reutilizan el archivo ya archivado. El boletín se abre en una
 * pestaña nueva mediante una signed URL de corta duración.
 */
export function DescargarBoletin({ instalacionId }: DescargarBoletinProps) {
  const t = useTranslations("instalaciones");
  const tErrores = useTranslations("instalaciones.errores");
  const [pending, startTransition] = useTransition();

  function onClick() {
    startTransition(async () => {
      const result = await obtenerSignedUrlBoletin(instalacionId);
      if (!result.ok) {
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }
      window.open(result.data.url, "_blank", "noopener,noreferrer");
    });
  }

  return (
    <Button
      variant="outline"
      size="sm"
      className="gap-2"
      onClick={onClick}
      disabled={pending}
      aria-label={t("accion.boletin")}
    >
      {pending ? (
        <Loader2 className="size-4 animate-spin" aria-hidden />
      ) : (
        <FileText className="size-4" aria-hidden />
      )}
      {t("accion.boletin")}
    </Button>
  );
}
