"use client";

import { CheckCircle2, Loader2, RotateCcw } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { resolverConflicto, type ResolucionConflicto } from "@/lib/conflictos/actions";
import { cn } from "@/lib/utils";

interface ResolverConflictoProps {
  recaudacionId: string;
}

const OPCIONES: ResolucionConflicto[] = ["aceptada", "sustituida", "anulada"];

export function ResolverConflicto({ recaudacionId }: ResolverConflictoProps) {
  const t = useTranslations("conflictos");
  const tValidacion = useTranslations("conflictos.validacion");
  const tErrores = useTranslations("conflictos.errores");
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [resolucion, setResolucion] = useState<ResolucionConflicto | null>(null);
  const [notas, setNotas] = useState("");

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!resolucion) {
      toast.error(t("errores.resolucionRequerida"));
      return;
    }
    const fd = new FormData();
    fd.set("resolucion", resolucion);
    fd.set("notas", notas);

    startTransition(async () => {
      const result = await resolverConflicto(recaudacionId, null, fd);
      if (!result.ok) {
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }
      toast.success(t("resolucionOk"));
      router.refresh();
    });
  }

  return (
    <Card className="border-warning/40 bg-warning-subtle">
      <CardHeader>
        <CardTitle className="text-base">{t("resolver.titulo")}</CardTitle>
        <CardDescription>{t("resolver.descripcion")}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="space-y-4">
          <div role="radiogroup" aria-label={t("resolver.opciones")}>
            <div className="grid gap-2 sm:grid-cols-3">
              {OPCIONES.map((opcion) => {
                const seleccionada = resolucion === opcion;
                return (
                  <button
                    key={opcion}
                    type="button"
                    role="radio"
                    aria-checked={seleccionada}
                    onClick={() => setResolucion(opcion)}
                    disabled={pending}
                    className={cn(
                      "flex flex-col gap-1 rounded-md border p-3 text-left transition-colors",
                      seleccionada
                        ? "bg-primary/5 ring-primary/40 border-primary ring-2"
                        : "hover:bg-accent/50 border-input bg-background",
                      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                    )}
                  >
                    <span className="flex items-center gap-2 text-sm font-medium">
                      <CheckCircle2
                        className={cn("size-4", seleccionada ? "opacity-100" : "opacity-0")}
                        aria-hidden
                      />
                      <span>{t(`resolucion.${opcion}.label`)}</span>
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {t(`resolucion.${opcion}.descripcion`)}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="resolver-notas">{t("campos.notas")}</Label>
            <Textarea
              id="resolver-notas"
              rows={3}
              maxLength={2000}
              value={notas}
              onChange={(event) => setNotas(event.target.value)}
              placeholder={t("placeholders.notas")}
              disabled={pending}
            />
            {notas.length > 2000 ? (
              <p className="text-[0.8rem] font-medium text-destructive">
                {tValidacion("notasMuyLargas")}
              </p>
            ) : null}
          </div>
          <div className="flex items-center justify-end gap-2">
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setResolucion(null);
                setNotas("");
              }}
              disabled={pending}
              className="gap-2"
            >
              <RotateCcw className="size-4" aria-hidden />
              {t("accion.limpiar")}
            </Button>
            <Button type="submit" disabled={pending || !resolucion} className="gap-2">
              {pending ? (
                <Loader2 className="size-4 animate-spin" aria-hidden />
              ) : (
                <CheckCircle2 className="size-4" aria-hidden />
              )}
              <span>{t("accion.aplicar")}</span>
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
