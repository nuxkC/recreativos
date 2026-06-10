"use client";

import { Loader2, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { eliminarInstalacion } from "@/lib/instalaciones/actions";

interface EliminarInstalacionProps {
  instalacionId: string;
  /** Texto para que el usuario sepa qué borra. */
  etiqueta: string;
}

export function EliminarInstalacion({ instalacionId, etiqueta }: EliminarInstalacionProps) {
  const t = useTranslations("instalaciones");
  const tErrores = useTranslations("instalaciones.errores");
  const [open, setOpen] = useState(false);
  const [pending, startTransition] = useTransition();
  const router = useRouter();

  function onConfirm() {
    startTransition(async () => {
      const result = await eliminarInstalacion(instalacionId);
      if (!result.ok) {
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        setOpen(false);
        return;
      }
      setOpen(false);
      router.push("/instalaciones");
    });
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger asChild>
        <Button variant="destructive" size="sm" className="gap-2">
          <Trash2 className="size-4" aria-hidden />
          {t("accion.eliminar")}
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("eliminar.titulo")}</AlertDialogTitle>
          <AlertDialogDescription>{t("eliminar.descripcion", { etiqueta })}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>{t("accion.cancelar")}</AlertDialogCancel>
          <AlertDialogAction
            disabled={pending}
            onClick={(event) => {
              event.preventDefault();
              onConfirm();
            }}
            className="hover:bg-destructive/90 bg-destructive text-destructive-foreground"
          >
            {pending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden />
            ) : (
              <Trash2 className="size-4" aria-hidden />
            )}
            <span>{t("accion.eliminar")}</span>
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
