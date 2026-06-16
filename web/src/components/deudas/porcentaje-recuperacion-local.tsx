"use client";

import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { setPorcentajeRecuperacionLocal } from "@/lib/deudas/actions";

interface PorcentajeRecuperacionLocalProps {
  localId: string;
  /** Override actual del local (null = hereda el de la empresa). */
  valorLocal: number | null;
  /** % por defecto de la empresa (lo que se aplica si el local hereda). */
  valorEmpresa: number;
}

export function PorcentajeRecuperacionLocal({
  localId,
  valorLocal,
  valorEmpresa,
}: PorcentajeRecuperacionLocalProps) {
  const t = useTranslations("deudas.porcentaje");
  const tErrores = useTranslations("deudas.errores");
  const router = useRouter();

  const [pending, startTransition] = useTransition();
  const [heredar, setHeredar] = useState(valorLocal === null);
  const [valor, setValor] = useState<number>(valorLocal ?? valorEmpresa);

  function onGuardar() {
    startTransition(async () => {
      const result = await setPorcentajeRecuperacionLocal(localId, heredar ? null : valor);
      if (!result.ok) {
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }
      toast.success(t("ok"));
      router.refresh();
    });
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <input
          id="recuperacion-heredar"
          type="checkbox"
          className="border-input accent-primary size-4 rounded"
          checked={heredar}
          onChange={(event) => setHeredar(event.target.checked)}
        />
        <Label htmlFor="recuperacion-heredar" className="font-normal">
          {t("heredar", { valor: valorEmpresa })}
        </Label>
      </div>
      {!heredar ? (
        <div className="space-y-2">
          <Label htmlFor="recuperacion-valor">{t("valor")}</Label>
          <Input
            id="recuperacion-valor"
            type="number"
            inputMode="numeric"
            min={0}
            max={100}
            className="sm:max-w-40"
            value={valor}
            onChange={(event) =>
              setValor(event.target.value === "" ? 0 : Number(event.target.value))
            }
          />
        </div>
      ) : null}
      <div>
        <Button size="sm" onClick={onGuardar} disabled={pending}>
          {pending ? <Loader2 className="size-4 animate-spin" aria-hidden /> : null}
          <span>{t("guardar")}</span>
        </Button>
      </div>
    </div>
  );
}
