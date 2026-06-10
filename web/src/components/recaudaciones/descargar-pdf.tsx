"use client";

import { Download, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { obtenerSignedUrlPdf } from "@/lib/recaudaciones/actions";

interface DescargarPdfProps {
  recaudacionId: string;
  /** Si la recaudación no tiene `pdf_url` en BBDD, deshabilitamos el botón. */
  disponible: boolean;
}

export function DescargarPdf({ recaudacionId, disponible }: DescargarPdfProps) {
  const t = useTranslations("recaudaciones");
  const tErrores = useTranslations("recaudaciones.errores");
  const [pending, startTransition] = useTransition();

  function onClick() {
    startTransition(async () => {
      const result = await obtenerSignedUrlPdf(recaudacionId);
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
      disabled={pending || !disponible}
      aria-label={t("accion.descargarPdf")}
    >
      {pending ? (
        <Loader2 className="size-4 animate-spin" aria-hidden />
      ) : (
        <Download className="size-4" aria-hidden />
      )}
      {t("accion.descargarPdf")}
    </Button>
  );
}
