"use client";

import { Loader2, PowerOff } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { FieldDate } from "@/components/common/date-field";
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
import { cerrarInstalacion } from "@/lib/instalaciones/actions";

interface CerrarInstalacionProps {
  instalacionId: string;
  fechaInicio: string;
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export function CerrarInstalacion({ instalacionId, fechaInicio }: CerrarInstalacionProps) {
  const t = useTranslations("instalaciones");
  const tValidacion = useTranslations("instalaciones.validacion");
  const tErrores = useTranslations("instalaciones.errores");
  const router = useRouter();

  const [open, setOpen] = useState(false);
  const [pending, startTransition] = useTransition();
  const [fechaFin, setFechaFin] = useState(todayIso());
  const [notas, setNotas] = useState("");
  const [errors, setErrors] = useState<{ fechaFin?: string }>({});

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrors({});
    if (fechaFin < fechaInicio) {
      setErrors({ fechaFin: tValidacion("fechaFinAntesDeInicio") });
      return;
    }

    const fd = new FormData();
    fd.set("fechaFin", fechaFin);
    fd.set("notas", notas);

    startTransition(async () => {
      const result = await cerrarInstalacion(instalacionId, null, fd);
      if (!result.ok) {
        const fieldErr = result.error.fieldErrors?.fechaFin?.[0];
        if (fieldErr) {
          setErrors({
            fechaFin: tValidacion.has(fieldErr) ? tValidacion(fieldErr) : fieldErr,
          });
          return;
        }
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }
      toast.success(t("cerrarOk"));
      setOpen(false);
      router.refresh();
    });
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2">
          <PowerOff className="size-4" aria-hidden />
          {t("accion.cerrar")}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("cerrar.titulo")}</DialogTitle>
          <DialogDescription>{t("cerrar.descripcion")}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="cerrar-fecha-fin">{t("campos.fechaFin")}</Label>
            <FieldDate
              id="cerrar-fecha-fin"
              value={fechaFin}
              onChange={setFechaFin}
              min={fechaInicio}
              error={errors.fechaFin}
              density="compact"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="cerrar-notas">{t("campos.notasCierre")}</Label>
            <Textarea
              id="cerrar-notas"
              rows={3}
              maxLength={2000}
              value={notas}
              onChange={(event) => setNotas(event.target.value)}
              placeholder={t("placeholders.notasCierre")}
            />
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
            <Button type="submit" disabled={pending}>
              {pending ? (
                <Loader2 className="size-4 animate-spin" aria-hidden />
              ) : (
                <PowerOff className="size-4" aria-hidden />
              )}
              <span>{t("accion.cerrarConfirmar")}</span>
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
