"use client";

import { Loader2, Merge, Pencil } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useMemo, useState, useTransition } from "react";
import { toast } from "sonner";

import { Combobox, type ComboboxOption } from "@/components/common/combobox";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
import {
  fusionarFabricante,
  fusionarModelo,
  renombrarFabricante,
  renombrarModelo,
} from "@/lib/catalogo/actions";
import type { FabricanteOpcion, ModeloOpcion } from "@/lib/catalogo/opciones";

type Tipo = "fabricante" | "modelo";

interface CatalogoAdminProps {
  fabricantes: FabricanteOpcion[];
  modelos: ModeloOpcion[];
}

/** Entrada mínima común a fabricante y modelo: solo se necesita `id` y `nombre`. */
interface Entrada {
  id: string;
  nombre: string;
}

/**
 * Traductor de códigos de error de las Server Actions a texto i18n. Si el
 * código no tiene mensaje propio cae en `desconocido` (mismo patrón que el
 * resto de CRUDs del back-office).
 */
function useErrorText(): (code: string) => string {
  const tErr = useTranslations("catalogoAdmin.errores");
  return (code: string) => (tErr.has(code) ? tErr(code) : tErr("desconocido"));
}

/**
 * Panel de curación del catálogo GLOBAL: lista fabricantes con sus modelos y
 * ofrece renombrar/fusionar cada entrada. La autorización real la hacen las
 * RPCs; aquí solo se pinta la UI (la página ya exigió `requireAdminCatalogo`).
 */
export function CatalogoAdmin({ fabricantes, modelos }: CatalogoAdminProps) {
  const t = useTranslations("catalogoAdmin");

  const modelosPorFabricante = useMemo(() => {
    const mapa = new Map<string, ModeloOpcion[]>();
    for (const modelo of modelos) {
      const lista = mapa.get(modelo.fabricanteId) ?? [];
      lista.push(modelo);
      mapa.set(modelo.fabricanteId, lista);
    }
    return mapa;
  }, [modelos]);

  if (fabricantes.length === 0) {
    return <p className="text-muted-foreground text-sm">{t("vacio")}</p>;
  }

  return (
    <div className="space-y-4">
      {fabricantes.map((fabricante) => {
        const susModelos = modelosPorFabricante.get(fabricante.id) ?? [];
        const otrosFabricantes = fabricantes.filter((f) => f.id !== fabricante.id);
        return (
          <Card key={fabricante.id}>
            <CardHeader className="flex flex-row items-center justify-between gap-2 space-y-0">
              <CardTitle className="text-base">{fabricante.nombre}</CardTitle>
              <div className="flex shrink-0 gap-2">
                <RenombrarDialog tipo="fabricante" entrada={fabricante} />
                <FusionarDialog
                  tipo="fabricante"
                  entrada={fabricante}
                  destinos={otrosFabricantes}
                />
              </div>
            </CardHeader>
            <CardContent>
              {susModelos.length === 0 ? (
                <p className="text-muted-foreground text-sm">{t("sinModelos")}</p>
              ) : (
                <ul className="divide-border divide-y">
                  {susModelos.map((modelo) => {
                    const otrosModelos = susModelos.filter((m) => m.id !== modelo.id);
                    return (
                      <li key={modelo.id} className="flex items-center justify-between gap-2 py-2">
                        <span className="text-sm">{modelo.nombre}</span>
                        <div className="flex shrink-0 gap-2">
                          <RenombrarDialog tipo="modelo" entrada={modelo} />
                          <FusionarDialog tipo="modelo" entrada={modelo} destinos={otrosModelos} />
                        </div>
                      </li>
                    );
                  })}
                </ul>
              )}
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}

// -----------------------------------------------------------------------------
// Renombrar
// -----------------------------------------------------------------------------

function RenombrarDialog({ tipo, entrada }: { tipo: Tipo; entrada: Entrada }) {
  const t = useTranslations("catalogoAdmin");
  const errorText = useErrorText();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [valor, setValor] = useState(entrada.nombre);
  const [pending, startTransition] = useTransition();

  function onOpenChange(next: boolean) {
    setOpen(next);
    // Cada apertura parte del nombre actual; al cerrar se descarta lo tecleado.
    if (next) setValor(entrada.nombre);
  }

  function onConfirm() {
    startTransition(async () => {
      const result =
        tipo === "fabricante"
          ? await renombrarFabricante(entrada.id, valor)
          : await renombrarModelo(entrada.id, valor);
      if (!result.ok) {
        toast.error(errorText(result.error.code));
        return;
      }
      toast.success(t("toast.renombrado"));
      setOpen(false);
      router.refresh();
    });
  }

  const titulo =
    tipo === "fabricante" ? t("renombrar.tituloFabricante") : t("renombrar.tituloModelo");
  const inputId = `renombrar-${tipo}-${entrada.id}`;
  const nombreLimpio = valor.trim();
  const sinCambios = nombreLimpio.length === 0 || nombreLimpio === entrada.nombre.trim();

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2">
          <Pencil className="size-4" aria-hidden />
          {t("accion.renombrar")}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{titulo}</DialogTitle>
          <DialogDescription>
            {t("renombrar.descripcion", { nombre: entrada.nombre })}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor={inputId}>{t("renombrar.etiqueta")}</Label>
          <Input
            id={inputId}
            value={valor}
            onChange={(event) => setValor(event.target.value)}
            placeholder={t("renombrar.placeholder")}
            autoFocus
          />
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)} disabled={pending}>
            {t("accion.cancelar")}
          </Button>
          <Button onClick={onConfirm} disabled={pending || sinCambios} className="gap-2">
            {pending ? <Loader2 className="size-4 animate-spin" aria-hidden /> : null}
            {t("accion.confirmar")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// -----------------------------------------------------------------------------
// Fusionar (destructiva: absorbe la entrada en el destino elegido)
// -----------------------------------------------------------------------------

function FusionarDialog({
  tipo,
  entrada,
  destinos,
}: {
  tipo: Tipo;
  entrada: Entrada;
  destinos: Entrada[];
}) {
  const t = useTranslations("catalogoAdmin");
  const errorText = useErrorText();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [destinoId, setDestinoId] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  // El value del combobox es el id del destino; el label, su nombre.
  const opciones: ComboboxOption[] = useMemo(
    () => destinos.map((d) => ({ value: d.id, label: d.nombre })),
    [destinos],
  );

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (!next) setDestinoId(null);
  }

  function onConfirm() {
    if (!destinoId) {
      toast.error(t("fusionar.destinoRequerido"));
      return;
    }
    startTransition(async () => {
      const result =
        tipo === "fabricante"
          ? await fusionarFabricante(entrada.id, destinoId)
          : await fusionarModelo(entrada.id, destinoId);
      if (!result.ok) {
        toast.error(errorText(result.error.code));
        return;
      }
      toast.success(t("toast.fusionado"));
      setOpen(false);
      router.refresh();
    });
  }

  const titulo =
    tipo === "fabricante" ? t("fusionar.tituloFabricante") : t("fusionar.tituloModelo");
  const comboId = `fusionar-${tipo}-${entrada.id}`;
  const sinDestinos = destinos.length === 0;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2" disabled={sinDestinos}>
          <Merge className="size-4" aria-hidden />
          {t("accion.fusionar")}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{titulo}</DialogTitle>
          <DialogDescription>
            {t("fusionar.descripcion", { nombre: entrada.nombre })}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor={comboId}>{t("fusionar.etiqueta")}</Label>
          <Combobox
            id={comboId}
            options={opciones}
            value={destinoId}
            onChange={setDestinoId}
            placeholder={t("fusionar.placeholder")}
            searchPlaceholder={t("fusionar.buscar")}
            emptyMessage={t("fusionar.sinDestinos")}
          />
          <p className="text-destructive text-sm" role="alert">
            {t("fusionar.aviso")}
          </p>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)} disabled={pending}>
            {t("accion.cancelar")}
          </Button>
          <Button
            variant="destructive"
            onClick={onConfirm}
            disabled={pending || !destinoId}
            className="gap-2"
          >
            {pending ? <Loader2 className="size-4 animate-spin" aria-hidden /> : null}
            {t("accion.fusionar")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
