"use client";

import { Ban, Loader2 } from "lucide-react";
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
import { condonarCredito } from "@/lib/deudas/actions";

interface CondonarCreditoProps {
  creditoId: string;
  localId: string;
}

export function CondonarCredito({ creditoId, localId }: CondonarCreditoProps) {
  const t = useTranslations("deudas");
  const tErrores = useTranslations("deudas.errores");
  const [open, setOpen] = useState(false);
  const [pending, startTransition] = useTransition();
  const router = useRouter();

  function onConfirm() {
    startTransition(async () => {
      const result = await condonarCredito(creditoId, localId);
      if (!result.ok) {
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        setOpen(false);
        return;
      }
      toast.success(t("condonar.ok"));
      setOpen(false);
      router.refresh();
    });
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-2 text-muted-foreground">
          <Ban className="size-4" aria-hidden />
          {t("accion.condonar")}
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("condonar.titulo")}</AlertDialogTitle>
          <AlertDialogDescription>{t("condonar.descripcion")}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>{t("accion.cancelar")}</AlertDialogCancel>
          <AlertDialogAction
            disabled={pending}
            onClick={(event) => {
              event.preventDefault();
              onConfirm();
            }}
          >
            {pending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden />
            ) : (
              <Ban className="size-4" aria-hidden />
            )}
            <span>{t("accion.condonar")}</span>
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
