"use client";

import { Loader2, Plus } from "lucide-react";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { crearPrestamo } from "@/lib/deudas/actions";

interface NuevoPrestamoProps {
  localId: string;
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export function NuevoPrestamo({ localId }: NuevoPrestamoProps) {
  const t = useTranslations("deudas");
  const tValidacion = useTranslations("deudas.validacion");
  const tErrores = useTranslations("deudas.errores");
  const router = useRouter();

  const [open, setOpen] = useState(false);
  const [pending, startTransition] = useTransition();
  const [principal, setPrincipal] = useState("");
  const [fecha, setFecha] = useState(todayIso());
  const [notas, setNotas] = useState("");
  const [errors, setErrors] = useState<{ principal?: string; notas?: string }>({});

  function resetForm() {
    setPrincipal("");
    setFecha(todayIso());
    setNotas("");
    setErrors({});
  }

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrors({});

    const fd = new FormData();
    fd.set("principal", principal);
    fd.set("fecha", fecha);
    fd.set("notas", notas);

    startTransition(async () => {
      const result = await crearPrestamo(localId, null, fd);
      if (!result.ok) {
        const fe = result.error.fieldErrors;
        if (fe?.principal || fe?.notas) {
          const tr = (k?: string) => (k ? (tValidacion.has(k) ? tValidacion(k) : k) : undefined);
          setErrors({ principal: tr(fe.principal?.[0]), notas: tr(fe.notas?.[0]) });
          return;
        }
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }
      toast.success(t("prestamo.ok"));
      setOpen(false);
      resetForm();
      router.refresh();
    });
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) resetForm();
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" className="gap-2">
          <Plus className="size-4" aria-hidden />
          {t("accion.nuevoPrestamo")}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("prestamo.titulo")}</DialogTitle>
          <DialogDescription>{t("prestamo.descripcion")}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="prestamo-principal">{t("prestamo.principal")}</Label>
            <Input
              id="prestamo-principal"
              type="text"
              inputMode="decimal"
              placeholder="0.00"
              autoComplete="off"
              value={principal}
              onChange={(event) => setPrincipal(event.target.value)}
              required
              aria-invalid={errors.principal ? "true" : undefined}
              aria-describedby={errors.principal ? "prestamo-principal-error" : undefined}
            />
            {errors.principal ? (
              <p
                id="prestamo-principal-error"
                className="text-[0.8rem] font-medium text-destructive"
              >
                {errors.principal}
              </p>
            ) : (
              <p className="text-[0.8rem] text-muted-foreground">{t("prestamo.principalAyuda")}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="prestamo-fecha">{t("prestamo.fecha")}</Label>
            <Input
              id="prestamo-fecha"
              type="date"
              value={fecha}
              onChange={(event) => setFecha(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="prestamo-notas">{t("prestamo.notas")}</Label>
            <Textarea
              id="prestamo-notas"
              rows={3}
              maxLength={2000}
              value={notas}
              onChange={(event) => setNotas(event.target.value)}
              required
              aria-invalid={errors.notas ? "true" : undefined}
              aria-describedby={errors.notas ? "prestamo-notas-error" : "prestamo-notas-help"}
            />
            {errors.notas ? (
              <p id="prestamo-notas-error" className="text-[0.8rem] font-medium text-destructive">
                {errors.notas}
              </p>
            ) : (
              <p id="prestamo-notas-help" className="text-[0.8rem] text-muted-foreground">
                {t("prestamo.notasAyuda")}
              </p>
            )}
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
                <Plus className="size-4" aria-hidden />
              )}
              <span>{t("accion.guardar")}</span>
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
