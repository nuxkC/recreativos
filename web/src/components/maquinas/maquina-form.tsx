"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { useForm, type FieldErrors } from "react-hook-form";
import { toast } from "sonner";

import { Combobox } from "@/components/common/combobox";
import { Button } from "@/components/ui/button";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  opcionesFabricante,
  opcionesModelo,
  type FabricanteOpcion,
  type ModeloOpcion,
} from "@/lib/catalogo/opciones";
import { actualizarMaquina, crearMaquina, type ActionResult } from "@/lib/maquinas/actions";
import { MaquinaInputSchema } from "@/lib/maquinas/schemas";
import { ESTADOS_MAQUINA, type EstadoMaquina, type Maquina } from "@/lib/maquinas/types";

type MaquinaFormValues = {
  numeroSerie: string;
  modelo: string;
  fabricante: string;
  /** El usuario teclea libremente; el schema lo parsea como Decimal. */
  valorCredito: string;
  /** Inputs `type="number"` devuelven string; el schema hace coerce. */
  contadorEntradasInicial: string;
  contadorSalidasInicial: string;
  estado: EstadoMaquina;
  notas: string;
};

interface MaquinaFormProps {
  mode: "create" | "edit";
  maquina?: Maquina;
  fabricantes: FabricanteOpcion[];
  modelos: ModeloOpcion[];
}

function defaultsFromMaquina(maquina?: Maquina): MaquinaFormValues {
  return {
    numeroSerie: maquina?.numeroSerie ?? "",
    modelo: maquina?.modelo ?? "",
    fabricante: maquina?.fabricante ?? "",
    valorCredito: maquina?.valorCredito ?? "",
    contadorEntradasInicial:
      maquina?.contadorEntradasInicial !== undefined
        ? String(maquina.contadorEntradasInicial)
        : "0",
    contadorSalidasInicial:
      maquina?.contadorSalidasInicial !== undefined ? String(maquina.contadorSalidasInicial) : "0",
    estado: maquina?.estado ?? "almacen",
    notas: maquina?.notas ?? "",
  };
}

function buildFormData(values: MaquinaFormValues): FormData {
  const fd = new FormData();
  fd.set("numeroSerie", values.numeroSerie);
  fd.set("modelo", values.modelo);
  fd.set("fabricante", values.fabricante);
  fd.set("valorCredito", values.valorCredito);
  fd.set("contadorEntradasInicial", values.contadorEntradasInicial);
  fd.set("contadorSalidasInicial", values.contadorSalidasInicial);
  fd.set("estado", values.estado);
  fd.set("notas", values.notas);
  return fd;
}

export function MaquinaForm({ mode, maquina, fabricantes, modelos }: MaquinaFormProps) {
  const t = useTranslations("maquinas");
  const tCampos = useTranslations("maquinas.campos");
  const tCatalogo = useTranslations("maquinas.catalogo");
  const tEstado = useTranslations("maquinas.estado");
  const tValidacion = useTranslations("maquinas.validacion");
  const tErrores = useTranslations("maquinas.errores");
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const form = useForm<MaquinaFormValues>({
    resolver: zodResolver(MaquinaInputSchema, undefined, {
      // Validamos los strings tal cual los introduce el usuario, sin pasar
      // por los `transform` del schema (que devolverían `null`/`number` y
      // romperían los inputs controlados). El parse final con transform
      // corre server-side.
      raw: true,
    }),
    defaultValues: defaultsFromMaquina(maquina),
  });

  // El modelo depende del fabricante seleccionado; observamos su valor para
  // recalcular las opciones de modelo y para deshabilitar el combo si no hay
  // fabricante todavía.
  const fabricanteSel = form.watch("fabricante");
  const fabricanteOpts = opcionesFabricante(fabricantes);
  const modeloOpts = opcionesModelo(modelos, fabricantes, fabricanteSel || null);

  // Cambiar de fabricante invalida el modelo elegido (un modelo pertenece a un
  // fabricante concreto). Al elegir/crear otro fabricante se limpia el modelo
  // para no dejar un par incoherente; el usuario vuelve a elegir/crear modelo.
  function handleFabricanteChange(nuevo: string) {
    const actual = form.getValues("fabricante");
    form.setValue("fabricante", nuevo, { shouldDirty: true, shouldValidate: true });
    if (nuevo !== actual) {
      form.setValue("modelo", "", { shouldDirty: true, shouldValidate: true });
    }
  }

  function applyServerErrors(fieldErrors: Record<string, string[]> | undefined): boolean {
    if (!fieldErrors) return false;
    let applied = false;
    for (const [field, errors] of Object.entries(fieldErrors)) {
      const code = errors[0];
      if (!code) continue;
      form.setError(field as keyof MaquinaFormValues, {
        type: "server",
        message: tValidacion.has(code) ? tValidacion(code) : code,
      });
      applied = true;
    }
    return applied;
  }

  async function onSubmit(values: MaquinaFormValues) {
    setSubmitting(true);
    try {
      const formData = buildFormData(values);
      let result: ActionResult | ActionResult<{ id: string }>;
      if (mode === "create") {
        result = await crearMaquina(null, formData);
      } else if (maquina) {
        result = await actualizarMaquina(maquina.id, null, formData);
      } else {
        throw new Error("MaquinaForm en modo edit sin maquina");
      }

      // crear y actualizar devuelven siempre un ActionResult serializable;
      // tratamos aquí tanto el error como el éxito de ambos modos.
      if (!result.ok) {
        const applied = applyServerErrors(result.error.fieldErrors);
        if (!applied) {
          const code = result.error.code;
          toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        }
        return;
      }

      // Éxito: en "create" navegamos al detalle recién creado (el id viene en
      // result.data); en "update" basta con refrescar la ruta actual.
      toast.success(t("guardadoOk"));
      if (mode === "create") {
        router.push(`/maquinas/${(result.data as { id: string }).id}`);
      } else {
        router.refresh();
      }
    } catch (err) {
      // No interceptamos NEXT_REDIRECT (es un Error con `digest` interno);
      // si llega aquí es un error inesperado.
      if (err && typeof err === "object" && "digest" in err) {
        throw err;
      }
      console.error("maquina_form_unexpected_error", err);
      toast.error(tErrores("desconocido"));
    } finally {
      setSubmitting(false);
    }
  }

  function onInvalid(errors: FieldErrors<MaquinaFormValues>) {
    // Traducimos los códigos producidos por Zod (mensajes son claves i18n).
    for (const [field, fieldError] of Object.entries(errors)) {
      const message = fieldError?.message;
      if (typeof message === "string" && tValidacion.has(message)) {
        form.setError(field as keyof MaquinaFormValues, {
          type: "validate",
          message: tValidacion(message),
        });
      }
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit, onInvalid)} className="space-y-6">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            control={form.control}
            name="numeroSerie"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("numeroSerie")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={80} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="estado"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("estado")}</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {ESTADOS_MAQUINA.map((estado) => (
                      <SelectItem key={estado} value={estado}>
                        {tEstado(estado)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="fabricante"
            render={({ field, fieldState }) => (
              <FormItem>
                <FormLabel>{tCampos("fabricante")}</FormLabel>
                <FormControl>
                  <Combobox
                    options={fabricanteOpts}
                    value={field.value || null}
                    onChange={handleFabricanteChange}
                    onCreate={handleFabricanteChange}
                    placeholder={tCatalogo("elegirFabricante")}
                    searchPlaceholder={tCatalogo("buscarFabricante")}
                    emptyMessage={tCatalogo("sinFabricantes")}
                    formatCreateLabel={(valor) => tCatalogo("crear", { valor })}
                    error={!!fieldState.error}
                    aria-label={tCampos("fabricante")}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="modelo"
            render={({ field, fieldState }) => (
              <FormItem>
                <FormLabel>{tCampos("modelo")}</FormLabel>
                <FormControl>
                  <Combobox
                    options={modeloOpts}
                    value={field.value || null}
                    onChange={field.onChange}
                    onCreate={field.onChange}
                    placeholder={tCatalogo("elegirModelo")}
                    searchPlaceholder={tCatalogo("buscarModelo")}
                    emptyMessage={tCatalogo("sinModelos")}
                    formatCreateLabel={(valor) => tCatalogo("crear", { valor })}
                    disabled={!fabricanteSel}
                    error={!!fieldState.error}
                    aria-label={tCampos("modelo")}
                  />
                </FormControl>
                {!fabricanteSel ? (
                  <FormDescription>{tCatalogo("modeloSinFabricante")}</FormDescription>
                ) : null}
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="valorCredito"
            render={({ field }) => (
              <FormItem className="sm:col-span-2">
                <FormLabel>{tCampos("valorCredito")}</FormLabel>
                <FormControl>
                  <Input
                    type="text"
                    inputMode="decimal"
                    autoComplete="off"
                    placeholder="0.20"
                    {...field}
                  />
                </FormControl>
                <FormDescription>{t("formulario.descripcionValorCredito")}</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="contadorEntradasInicial"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("contadorEntradasInicial")}</FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    inputMode="numeric"
                    min="0"
                    step="1"
                    autoComplete="off"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="contadorSalidasInicial"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("contadorSalidasInicial")}</FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    inputMode="numeric"
                    min="0"
                    step="1"
                    autoComplete="off"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
        <FormField
          control={form.control}
          name="notas"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{tCampos("notas")}</FormLabel>
              <FormControl>
                <Textarea rows={4} maxLength={2000} {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <div className="flex items-center justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push("/maquinas")}
            disabled={submitting}
          >
            {t("accion.cancelar")}
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? (
              <>
                <Loader2 className="size-4 animate-spin" aria-hidden />
                <span>{t("accion.guardando")}</span>
              </>
            ) : (
              <span>{mode === "create" ? t("accion.crear") : t("accion.guardar")}</span>
            )}
          </Button>
        </div>
      </form>
    </Form>
  );
}
