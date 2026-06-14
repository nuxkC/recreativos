"use client";

import { Loader2, Plus, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { crearRecambio, eliminarRecambio } from "@/lib/averias/actions";
import type { Recambio } from "@/lib/averias/types";

interface RecambiosAveriaProps {
  averiaId: string;
  maquinaId: string;
  recambios: Recambio[];
  /** Solo se pueden añadir/borrar recambios mientras la avería está abierta. */
  editable: boolean;
}

const eurFormatter = new Intl.NumberFormat("es-ES", { style: "currency", currency: "EUR" });

function formatCoste(coste: string | null): string | null {
  if (coste === null) return null;
  const n = Number(coste);
  return Number.isFinite(n) ? eurFormatter.format(n) : coste;
}

export function RecambiosAveria({
  averiaId,
  maquinaId,
  recambios,
  editable,
}: RecambiosAveriaProps) {
  const t = useTranslations("averias");
  const tValidacion = useTranslations("averias.validacion");
  const tErrores = useTranslations("averias.errores");
  const router = useRouter();

  const [pending, startTransition] = useTransition();
  const [pieza, setPieza] = useState("");
  const [cantidad, setCantidad] = useState("1");
  const [coste, setCoste] = useState("");
  const [errorPieza, setErrorPieza] = useState<string | undefined>();

  function onAdd(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorPieza(undefined);

    const fd = new FormData();
    fd.set("pieza", pieza);
    fd.set("cantidad", cantidad);
    fd.set("coste", coste);

    startTransition(async () => {
      const result = await crearRecambio(averiaId, maquinaId, null, fd);
      if (!result.ok) {
        const fe = result.error.fieldErrors;
        if (fe?.pieza) {
          const k = fe.pieza[0];
          setErrorPieza(tValidacion.has(k) ? tValidacion(k) : k);
          return;
        }
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }
      setPieza("");
      setCantidad("1");
      setCoste("");
      router.refresh();
    });
  }

  function onDelete(recambioId: string) {
    startTransition(async () => {
      const result = await eliminarRecambio(recambioId, maquinaId);
      if (!result.ok) {
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }
      router.refresh();
    });
  }

  return (
    <div className="space-y-2">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {t("recambios.titulo")}
      </p>

      {recambios.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("recambios.vacio")}</p>
      ) : (
        <ul className="divide-y rounded-md border">
          {recambios.map((r) => {
            const costeFmt = formatCoste(r.coste);
            return (
              <li key={r.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <span className="min-w-0">
                  <span className="font-medium">{r.pieza}</span>
                  {r.cantidad > 1 ? (
                    <span className="text-muted-foreground"> × {r.cantidad}</span>
                  ) : null}
                  {costeFmt ? <span className="text-muted-foreground"> · {costeFmt}</span> : null}
                  {r.notas ? (
                    <span className="block text-xs text-muted-foreground">{r.notas}</span>
                  ) : null}
                </span>
                {editable ? (
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    className="size-7 shrink-0 text-muted-foreground"
                    onClick={() => onDelete(r.id)}
                    disabled={pending}
                    aria-label={t("recambios.eliminar")}
                  >
                    <Trash2 className="size-4" aria-hidden />
                  </Button>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}

      {editable ? (
        <form onSubmit={onAdd} className="flex flex-wrap items-start gap-2">
          <div className="min-w-40 flex-1 space-y-1">
            <Input
              aria-label={t("recambios.pieza")}
              placeholder={t("recambios.piezaPlaceholder")}
              value={pieza}
              onChange={(event) => setPieza(event.target.value)}
              aria-invalid={errorPieza ? "true" : undefined}
            />
            {errorPieza ? (
              <p className="text-[0.8rem] font-medium text-destructive">{errorPieza}</p>
            ) : null}
          </div>
          <Input
            aria-label={t("recambios.cantidad")}
            type="number"
            min={1}
            className="w-20"
            value={cantidad}
            onChange={(event) => setCantidad(event.target.value)}
          />
          <Input
            aria-label={t("recambios.coste")}
            inputMode="decimal"
            placeholder={t("recambios.costePlaceholder")}
            className="w-28"
            value={coste}
            onChange={(event) => setCoste(event.target.value)}
          />
          <Button type="submit" size="sm" variant="outline" className="gap-1" disabled={pending}>
            {pending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden />
            ) : (
              <Plus className="size-4" aria-hidden />
            )}
            {t("recambios.anadir")}
          </Button>
        </form>
      ) : null}
    </div>
  );
}
