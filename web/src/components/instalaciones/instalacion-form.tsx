"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { useForm, type FieldErrors } from "react-hook-form";
import { toast } from "sonner";

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
  actualizarInstalacion,
  crearInstalacion,
  type ActionResult,
} from "@/lib/instalaciones/actions";
import { InstalacionInputSchema } from "@/lib/instalaciones/schemas";
import type {
  Instalacion,
  LicenciaResumen,
  LocalResumen,
  MaquinaResumen,
} from "@/lib/instalaciones/types";

type InstalacionFormValues = {
  maquinaId: string;
  licenciaId: string;
  localId: string;
  fechaInicio: string;
  tasaSemanal: string;
  porcentajeLocal: string;
  tolva: string;
  notas: string;
};

interface InstalacionFormProps {
  mode: "create" | "edit";
  instalacion?: Instalacion;
  licencias: LicenciaResumen[];
  maquinas: MaquinaResumen[];
  locales: LocalResumen[];
}

function defaults(instalacion?: Instalacion): InstalacionFormValues {
  return {
    maquinaId: instalacion?.maquinaId ?? "",
    licenciaId: instalacion?.licenciaId ?? "",
    localId: instalacion?.localId ?? "",
    fechaInicio: instalacion?.fechaInicio ?? "",
    tasaSemanal: instalacion?.tasaSemanal ?? "",
    porcentajeLocal: instalacion?.porcentajeLocal ?? "50.00",
    // La tolva solo se fija en el alta; en edición el campo no se muestra.
    tolva: "0",
    notas: instalacion?.notas ?? "",
  };
}

function buildFormData(values: InstalacionFormValues): FormData {
  const fd = new FormData();
  Object.entries(values).forEach(([k, v]) => fd.set(k, v));
  return fd;
}

export function InstalacionForm({
  mode,
  instalacion,
  licencias,
  maquinas,
  locales,
}: InstalacionFormProps) {
  const t = useTranslations("instalaciones");
  const tCampos = useTranslations("instalaciones.campos");
  const tValidacion = useTranslations("instalaciones.validacion");
  const tErrores = useTranslations("instalaciones.errores");
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const isEdit = mode === "edit";
  const isClosed = isEdit && instalacion?.estado === "cerrada";
  const fkDisabled = isEdit; // FKs no se cambian en edición.

  const form = useForm<InstalacionFormValues>({
    resolver: zodResolver(InstalacionInputSchema, undefined, { raw: true }),
    defaultValues: defaults(instalacion),
  });

  function applyServerErrors(fieldErrors: Record<string, string[]> | undefined): boolean {
    if (!fieldErrors) return false;
    let applied = false;
    for (const [field, errors] of Object.entries(fieldErrors)) {
      const code = errors[0];
      if (!code) continue;
      form.setError(field as keyof InstalacionFormValues, {
        type: "server",
        message: tValidacion.has(code) ? tValidacion(code) : code,
      });
      applied = true;
    }
    return applied;
  }

  async function onSubmit(values: InstalacionFormValues) {
    setSubmitting(true);
    try {
      const fd = buildFormData(values);
      let result: ActionResult | ActionResult<{ id: string }>;
      if (mode === "create") {
        result = await crearInstalacion(null, fd);
      } else if (instalacion) {
        result = await actualizarInstalacion(instalacion.id, null, fd);
      } else {
        throw new Error("InstalacionForm en modo edit sin instalacion");
      }

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
        router.push(`/instalaciones/${(result.data as { id: string }).id}`);
      } else {
        router.refresh();
      }
    } catch (err) {
      if (err && typeof err === "object" && "digest" in err) throw err;
      console.error("instalacion_form_unexpected_error", err);
      toast.error(tErrores("desconocido"));
    } finally {
      setSubmitting(false);
    }
  }

  function onInvalid(errors: FieldErrors<InstalacionFormValues>) {
    for (const [field, fieldError] of Object.entries(errors)) {
      const message = fieldError?.message;
      if (typeof message === "string" && tValidacion.has(message)) {
        form.setError(field as keyof InstalacionFormValues, {
          type: "validate",
          message: tValidacion(message),
        });
      }
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit, onInvalid)} className="space-y-6">
        <fieldset disabled={isClosed} className="space-y-6 disabled:opacity-70">
          <div className="grid gap-4 sm:grid-cols-2">
            {/* FKs (no editables en edit) ------------------------------------ */}
            <FormField
              control={form.control}
              name="maquinaId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{tCampos("maquina")}</FormLabel>
                  <Select
                    value={field.value || undefined}
                    onValueChange={field.onChange}
                    disabled={fkDisabled}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("placeholders.maquina")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {maquinas.map((m) => (
                        <SelectItem key={m.id} value={m.id}>
                          {m.numeroSerie}
                          {m.modelo ? ` — ${m.modelo}` : ""}
                        </SelectItem>
                      ))}
                      {fkDisabled &&
                      instalacion?.maquina &&
                      !maquinas.some((m) => m.id === instalacion.maquina!.id) ? (
                        <SelectItem value={instalacion.maquina.id}>
                          {instalacion.maquina.numeroSerie}
                          {instalacion.maquina.modelo ? ` — ${instalacion.maquina.modelo}` : ""}
                        </SelectItem>
                      ) : null}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="licenciaId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{tCampos("licencia")}</FormLabel>
                  <Select
                    value={field.value || undefined}
                    onValueChange={field.onChange}
                    disabled={fkDisabled}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("placeholders.licencia")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {licencias.map((l) => (
                        <SelectItem key={l.id} value={l.id}>
                          {l.numero}
                        </SelectItem>
                      ))}
                      {fkDisabled &&
                      instalacion?.licencia &&
                      !licencias.some((l) => l.id === instalacion.licencia!.id) ? (
                        <SelectItem value={instalacion.licencia.id}>
                          {instalacion.licencia.numero}
                        </SelectItem>
                      ) : null}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="localId"
              render={({ field }) => (
                <FormItem className="sm:col-span-2">
                  <FormLabel>{tCampos("local")}</FormLabel>
                  <Select
                    value={field.value || undefined}
                    onValueChange={field.onChange}
                    disabled={fkDisabled}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("placeholders.local")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {locales.map((l) => (
                        <SelectItem key={l.id} value={l.id}>
                          {l.nombre}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* Condiciones económicas y baseline ----------------------------- */}
            <FormField
              control={form.control}
              name="fechaInicio"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{tCampos("fechaInicio")}</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="tasaSemanal"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{tCampos("tasaSemanal")}</FormLabel>
                  <FormControl>
                    <Input
                      type="text"
                      inputMode="decimal"
                      placeholder="0.00"
                      autoComplete="off"
                      {...field}
                    />
                  </FormControl>
                  <FormDescription>{tCampos("tasaSemanalAyuda")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="porcentajeLocal"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{tCampos("porcentajeLocal")}</FormLabel>
                  <FormControl>
                    <Input
                      type="text"
                      inputMode="decimal"
                      placeholder="50.00"
                      autoComplete="off"
                      {...field}
                    />
                  </FormControl>
                  <FormDescription>{tCampos("porcentajeLocalAyuda")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            {/* Tolva: solo en el alta. En edición no se muestra porque la deuda de
                tolva ya está creada y su ajuste va por el flujo de traslado. */}
            {mode === "create" ? (
              <FormField
                control={form.control}
                name="tolva"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{tCampos("tolva")}</FormLabel>
                    <FormControl>
                      <Input
                        type="text"
                        inputMode="decimal"
                        placeholder="0.00"
                        autoComplete="off"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>{tCampos("tolvaAyuda")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}
            <FormField
              control={form.control}
              name="notas"
              render={({ field }) => (
                <FormItem className="sm:col-span-2">
                  <FormLabel>{tCampos("notas")}</FormLabel>
                  <FormControl>
                    <Textarea rows={4} maxLength={2000} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </fieldset>
        <div className="flex items-center justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push("/instalaciones")}
            disabled={submitting}
          >
            {t("accion.cancelar")}
          </Button>
          <Button type="submit" disabled={submitting || isClosed}>
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
