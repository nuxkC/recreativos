"use client";

import { HandCoins, Loader2 } from "lucide-react";
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
import { registrarRecuperacionEfectivo } from "@/lib/deudas/actions";
import { formatEur } from "@/lib/recaudaciones/format";

interface RegistrarPagoProps {
  creditoId: string;
  localId: string;
  /** Saldo vivo de la deuda (string numérico) para mostrarlo de referencia. */
  saldo: string;
}

export function RegistrarPago({ creditoId, localId, saldo }: RegistrarPagoProps) {
  const t = useTranslations("deudas");
  const tValidacion = useTranslations("deudas.validacion");
  const tErrores = useTranslations("deudas.errores");
  const router = useRouter();

  const [open, setOpen] = useState(false);
  const [pending, startTransition] = useTransition();
  const [importe, setImporte] = useState("");
  const [notas, setNotas] = useState("");
  const [errors, setErrors] = useState<{ importe?: string }>({});

  function resetForm() {
    setImporte("");
    setNotas("");
    setErrors({});
  }

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrors({});

    const fd = new FormData();
    fd.set("importe", importe);
    fd.set("notas", notas);

    startTransition(async () => {
      const result = await registrarRecuperacionEfectivo(creditoId, localId, null, fd);
      if (!result.ok) {
        const fieldErr = result.error.fieldErrors?.importe?.[0];
        if (fieldErr) {
          setErrors({ importe: tValidacion.has(fieldErr) ? tValidacion(fieldErr) : fieldErr });
          return;
        }
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }
      toast.success(t("pago.ok"));
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
        <Button size="sm" variant="outline" className="gap-2">
          <HandCoins className="size-4" aria-hidden />
          {t("accion.registrarPago")}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("pago.titulo")}</DialogTitle>
          <DialogDescription>{t("pago.descripcion")}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4">
          <p className="text-sm text-muted-foreground">
            {t("pago.saldoActual")}:{" "}
            <span className="font-medium tabular-nums">{formatEur(saldo)}</span>
          </p>
          <div className="space-y-2">
            <Label htmlFor="pago-importe">{t("pago.importe")}</Label>
            <Input
              id="pago-importe"
              type="text"
              inputMode="decimal"
              placeholder="0.00"
              autoComplete="off"
              value={importe}
              onChange={(event) => setImporte(event.target.value)}
              required
              aria-invalid={errors.importe ? "true" : undefined}
              aria-describedby={errors.importe ? "pago-importe-error" : undefined}
            />
            {errors.importe ? (
              <p id="pago-importe-error" className="text-[0.8rem] font-medium text-destructive">
                {errors.importe}
              </p>
            ) : (
              <p className="text-[0.8rem] text-muted-foreground">{t("pago.importeAyuda")}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="pago-notas">{t("pago.notas")}</Label>
            <Textarea
              id="pago-notas"
              rows={3}
              maxLength={2000}
              value={notas}
              onChange={(event) => setNotas(event.target.value)}
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
                <HandCoins className="size-4" aria-hidden />
              )}
              <span>{t("accion.guardar")}</span>
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
