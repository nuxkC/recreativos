"use client";

import { Loader2, Pencil, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { actualizarAveria, crearAveria } from "@/lib/averias/actions";
import { CATEGORIAS_AVERIA, type Averia, type CategoriaAveria } from "@/lib/averias/types";

interface AveriaDialogProps {
  maquinaId: string;
  /** En modo edición, la avería a editar. */
  averia?: Averia;
  /** La máquina tiene instalación activa: habilita la merma de tolva (solo alta). */
  maquinaInstalada?: boolean;
}

export function AveriaDialog({ maquinaId, averia, maquinaInstalada = false }: AveriaDialogProps) {
  const isEdit = Boolean(averia);
  const t = useTranslations("averias");
  const tCategoria = useTranslations("averias.categoria");
  const tValidacion = useTranslations("averias.validacion");
  const tErrores = useTranslations("averias.errores");
  const router = useRouter();

  const [open, setOpen] = useState(false);
  const [pending, startTransition] = useTransition();
  const [categoria, setCategoria] = useState<CategoriaAveria | "">(averia?.categoria ?? "");
  const [descripcion, setDescripcion] = useState(averia?.descripcion ?? "");
  const [fueraServicio, setFueraServicio] = useState(averia?.poneMaquinaFueraServicio ?? false);
  const [notas, setNotas] = useState(averia?.notas ?? "");
  const [afectaTolva, setAfectaTolva] = useState(false);
  const [importeTolva, setImporteTolva] = useState("");
  const [errors, setErrors] = useState<{
    categoria?: string;
    descripcion?: string;
    importeTolva?: string;
  }>({});

  // La merma de tolva solo se ofrece al dar de alta y con instalación activa
  // (la propia `crear_averia` la exige). En edición no se toca la tolva.
  const mostrarTolva = !isEdit && maquinaInstalada;

  function resetForm() {
    setCategoria(averia?.categoria ?? "");
    setDescripcion(averia?.descripcion ?? "");
    setFueraServicio(averia?.poneMaquinaFueraServicio ?? false);
    setNotas(averia?.notas ?? "");
    setAfectaTolva(false);
    setImporteTolva("");
    setErrors({});
  }

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrors({});

    const fd = new FormData();
    fd.set("categoria", categoria);
    fd.set("descripcion", descripcion);
    // z.coerce.boolean: "" → false, cualquier no-vacío → true. Nunca enviar "false".
    fd.set("poneMaquinaFueraServicio", fueraServicio ? "true" : "");
    fd.set("notas", notas);
    // Merma de tolva (§5.6): solo en alta con instalación activa.
    fd.set("afectaTolva", mostrarTolva && afectaTolva ? "true" : "");
    fd.set("importeTolva", mostrarTolva && afectaTolva ? importeTolva : "");

    startTransition(async () => {
      const result = isEdit
        ? await actualizarAveria(averia!.id, maquinaId, null, fd)
        : await crearAveria(maquinaId, null, fd);

      if (!result.ok) {
        const fe = result.error.fieldErrors;
        if (fe?.categoria || fe?.descripcion || fe?.importeTolva) {
          const tr = (k?: string) => (k ? (tValidacion.has(k) ? tValidacion(k) : k) : undefined);
          setErrors({
            categoria: tr(fe.categoria?.[0]),
            descripcion: tr(fe.descripcion?.[0]),
            importeTolva: tr(fe.importeTolva?.[0]),
          });
          return;
        }
        const code = result.error.code;
        toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        return;
      }

      toast.success(isEdit ? t("editar.ok") : t("nueva.ok"));
      setOpen(false);
      if (!isEdit) resetForm();
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
        {isEdit ? (
          <Button size="sm" variant="outline" className="gap-2">
            <Pencil className="size-4" aria-hidden />
            {t("accion.editar")}
          </Button>
        ) : (
          <Button size="sm" className="gap-2">
            <Plus className="size-4" aria-hidden />
            {t("accion.nueva")}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? t("editar.titulo") : t("nueva.titulo")}</DialogTitle>
          <DialogDescription>
            {isEdit ? t("editar.descripcion") : t("nueva.descripcion")}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="averia-categoria">{t("campos.categoria")}</Label>
            <Select value={categoria} onValueChange={(v) => setCategoria(v as CategoriaAveria)}>
              <SelectTrigger
                id="averia-categoria"
                aria-invalid={errors.categoria ? "true" : undefined}
              >
                <SelectValue placeholder={t("campos.categoriaPlaceholder")} />
              </SelectTrigger>
              <SelectContent>
                {CATEGORIAS_AVERIA.map((c) => (
                  <SelectItem key={c} value={c}>
                    {tCategoria(c)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.categoria ? (
              <p className="text-[0.8rem] font-medium text-destructive">{errors.categoria}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="averia-descripcion">{t("campos.descripcion")}</Label>
            <Textarea
              id="averia-descripcion"
              rows={3}
              maxLength={2000}
              value={descripcion}
              onChange={(event) => setDescripcion(event.target.value)}
              placeholder={t("campos.descripcionPlaceholder")}
            />
            {errors.descripcion ? (
              <p className="text-[0.8rem] font-medium text-destructive">{errors.descripcion}</p>
            ) : null}
          </div>

          <label className="flex items-start gap-3 rounded-md border border-input p-3">
            <input
              type="checkbox"
              className="mt-0.5 size-4 accent-primary"
              checked={fueraServicio}
              onChange={(event) => setFueraServicio(event.target.checked)}
            />
            <span className="space-y-0.5">
              <span className="block text-sm font-medium">{t("campos.fueraServicio")}</span>
              <span className="block text-[0.8rem] text-muted-foreground">
                {t("campos.fueraServicioAyuda")}
              </span>
            </span>
          </label>

          {mostrarTolva ? (
            <div className="space-y-3 rounded-md border border-input p-3">
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  className="mt-0.5 size-4 accent-primary"
                  checked={afectaTolva}
                  onChange={(event) => setAfectaTolva(event.target.checked)}
                />
                <span className="space-y-0.5">
                  <span className="block text-sm font-medium">{t("campos.afectaTolva")}</span>
                  <span className="block text-[0.8rem] text-muted-foreground">
                    {t("campos.afectaTolvaAyuda")}
                  </span>
                </span>
              </label>
              {afectaTolva ? (
                <div className="space-y-2">
                  <Label htmlFor="averia-importe-tolva">{t("campos.importeTolva")}</Label>
                  <Input
                    id="averia-importe-tolva"
                    inputMode="decimal"
                    value={importeTolva}
                    onChange={(event) => setImporteTolva(event.target.value)}
                    placeholder={t("campos.importeTolvaPlaceholder")}
                    aria-invalid={errors.importeTolva ? "true" : undefined}
                  />
                  {errors.importeTolva ? (
                    <p className="text-[0.8rem] font-medium text-destructive">
                      {errors.importeTolva}
                    </p>
                  ) : null}
                </div>
              ) : null}
            </div>
          ) : null}

          <div className="space-y-2">
            <Label htmlFor="averia-notas">{t("campos.notas")}</Label>
            <Textarea
              id="averia-notas"
              rows={2}
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
