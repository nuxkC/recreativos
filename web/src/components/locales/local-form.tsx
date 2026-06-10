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
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { actualizarLocal, crearLocal, type ActionResult } from "@/lib/locales/actions";
import { LocalInputSchema } from "@/lib/locales/schemas";
import type { Local } from "@/lib/locales/types";

type LocalFormValues = {
  nombre: string;
  direccion: string;
  cifONif: string;
  titularNombre: string;
  telefono: string;
  email: string;
  notas: string;
};

interface LocalFormProps {
  mode: "create" | "edit";
  local?: Local;
}

function defaultsFromLocal(local?: Local): LocalFormValues {
  return {
    nombre: local?.nombre ?? "",
    direccion: local?.direccion ?? "",
    cifONif: local?.cifONif ?? "",
    titularNombre: local?.titularNombre ?? "",
    telefono: local?.telefono ?? "",
    email: local?.email ?? "",
    notas: local?.notas ?? "",
  };
}

function buildFormData(values: LocalFormValues): FormData {
  const fd = new FormData();
  fd.set("nombre", values.nombre);
  fd.set("direccion", values.direccion);
  fd.set("cifONif", values.cifONif);
  fd.set("titularNombre", values.titularNombre);
  fd.set("telefono", values.telefono);
  fd.set("email", values.email);
  fd.set("notas", values.notas);
  return fd;
}

export function LocalForm({ mode, local }: LocalFormProps) {
  const t = useTranslations("locales");
  const tCampos = useTranslations("locales.campos");
  const tValidacion = useTranslations("locales.validacion");
  const tErrores = useTranslations("locales.errores");
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const form = useForm<LocalFormValues>({
    resolver: zodResolver(LocalInputSchema, undefined, {
      // Validamos los strings tal cual los introduce el usuario, sin pasar
      // por el `transform` del schema (que devolvería null para los vacíos
      // y rompería los inputs controlados). El parse final con transform
      // corre server-side.
      raw: true,
    }),
    defaultValues: defaultsFromLocal(local),
  });

  function applyServerErrors(fieldErrors: Record<string, string[]> | undefined): boolean {
    if (!fieldErrors) return false;
    let applied = false;
    for (const [field, errors] of Object.entries(fieldErrors)) {
      const code = errors[0];
      if (!code) continue;
      form.setError(field as keyof LocalFormValues, {
        type: "server",
        message: tValidacion.has(code) ? tValidacion(code) : code,
      });
      applied = true;
    }
    return applied;
  }

  async function onSubmit(values: LocalFormValues) {
    setSubmitting(true);
    try {
      const formData = buildFormData(values);
      let result: ActionResult | ActionResult<{ id: string }>;
      if (mode === "create") {
        result = await crearLocal(null, formData);
      } else if (local) {
        result = await actualizarLocal(local.id, null, formData);
      } else {
        throw new Error("LocalForm en modo edit sin local");
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
        router.push(`/locales/${(result.data as { id: string }).id}`);
      } else {
        router.refresh();
      }
    } catch (err) {
      // No interceptamos NEXT_REDIRECT (es un Error con `digest` interno);
      // si llega aquí es un error inesperado.
      if (err && typeof err === "object" && "digest" in err) {
        throw err;
      }
      console.error("local_form_unexpected_error", err);
      toast.error(tErrores("desconocido"));
    } finally {
      setSubmitting(false);
    }
  }

  function onInvalid(errors: FieldErrors<LocalFormValues>) {
    // Tradúcimos los códigos producidos por Zod (mensajes son claves i18n).
    for (const [field, fieldError] of Object.entries(errors)) {
      const message = fieldError?.message;
      if (typeof message === "string" && tValidacion.has(message)) {
        form.setError(field as keyof LocalFormValues, {
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
            name="nombre"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("nombre")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={200} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="direccion"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("direccion")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={300} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="cifONif"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("cifONif")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={20} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="titularNombre"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("titularNombre")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={150} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="telefono"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("telefono")}</FormLabel>
                <FormControl>
                  <Input type="tel" autoComplete="off" inputMode="tel" maxLength={30} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="email"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("email")}</FormLabel>
                <FormControl>
                  <Input
                    type="email"
                    autoComplete="off"
                    inputMode="email"
                    maxLength={254}
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
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
        <div className="flex items-center justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push("/locales")}
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
