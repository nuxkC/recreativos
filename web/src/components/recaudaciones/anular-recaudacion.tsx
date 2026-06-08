"use client";

import { Loader2, Ban } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { anularRecaudacion } from "@/lib/recaudaciones/actions";

interface AnularRecaudacionProps {
  recaudacionId: string;
}

export function AnularRecaudacion({ recaudacionId }: AnularRecaudacionProps) {
  const t = useTranslations("recaudaciones");
  const tValidacion = useTranslations("recaudaciones.validacion");
  const tErrores = useTranslations("recaudaciones.errores");
  const router = useRouter();

  const [open, setOpen] = useState(false);
  const [pending, startTransition] = useTransition();
  const [motivo, setMotivo] = useState("");
  const [errors, setErrors] = useState<{ motivo?: string }>({});

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrors({});
    if (motivo.trim().length < 3) {
      setErrors({ motivo: tValidacion("motivoMuyCorto") });
      return;
    }

    const fd = new FormData();
    fd.set("motivo", motivo.trim());

    startTransition(async () => {
      const result = await anularRecaudacion(recaudacionId, null, fd);
      if (!result.ok) {
        const fieldErr = result.error.fieldErrors?.motivo?.[0];
        if (fieldErr) {
          setErrors({
            motivo: tValidacion.has(fieldErr) ? tValidacion(fieldErr) : fieldErr,
          });
          return;
        }
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }
      toast.success(t("anularOk"));
      setOpen(false);
      setMotivo("");
      router.refresh();
    });
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="destructive" size="sm" className="gap-2">
          <Ban className="size-4" aria-hidden />
          {t("accion.anular")}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("anular.titulo")}</DialogTitle>
          <DialogDescription>{t("anular.descripcion")}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="anular-motivo">{t("campos.motivoAnulacion")}</Label>
            <Textarea
              id="anular-motivo"
              rows={3}
              maxLength={500}
              required
              value={motivo}
              onChange={(event) => setMotivo(event.target.value)}
              placeholder={t("placeholders.motivoAnulacion")}
              aria-invalid={errors.motivo ? "true" : undefined}
              aria-describedby={errors.motivo ? "anular-motivo-error" : undefined}
            />
            {errors.motivo ? (
              <p id="anular-motivo-error" className="text-[0.8rem] font-medium text-destructive">
                {errors.motivo}
              </p>
            ) : null}
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={pending}
            >
              {t("accion.cancelar")}
            </Button>
            <Button type="submit" variant="destructive" disabled={pending} className="gap-2">
              {pending ? (
                <Loader2 className="size-4 animate-spin" aria-hidden />
              ) : (
                <Ban className="size-4" aria-hidden />
              )}
              <span>{t("accion.anularConfirmar")}</span>
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
