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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { actualizarLicencia, crearLicencia, type ActionResult } from "@/lib/licencias/actions";
import { LicenciaInputSchema } from "@/lib/licencias/schemas";
import { ESTADOS_LICENCIA, type EstadoLicencia, type Licencia } from "@/lib/licencias/types";

type LicenciaFormValues = {
  numero: string;
  tipo: string;
  fechaExpedicion: string;
  fechaCaducidad: string;
  comunidadAutonoma: string;
  estado: EstadoLicencia;
  notas: string;
};

interface LicenciaFormProps {
  mode: "create" | "edit";
  licencia?: Licencia;
}

function defaultsFromLicencia(licencia?: Licencia): LicenciaFormValues {
  return {
    numero: licencia?.numero ?? "",
    tipo: licencia?.tipo ?? "",
    fechaExpedicion: licencia?.fechaExpedicion ?? "",
    fechaCaducidad: licencia?.fechaCaducidad ?? "",
    comunidadAutonoma: licencia?.comunidadAutonoma ?? "",
    estado: licencia?.estado ?? "activa",
    notas: licencia?.notas ?? "",
  };
}

function buildFormData(values: LicenciaFormValues): FormData {
  const fd = new FormData();
  fd.set("numero", values.numero);
  fd.set("tipo", values.tipo);
  fd.set("fechaExpedicion", values.fechaExpedicion);
  fd.set("fechaCaducidad", values.fechaCaducidad);
  fd.set("comunidadAutonoma", values.comunidadAutonoma);
  fd.set("estado", values.estado);
  fd.set("notas", values.notas);
  return fd;
}

export function LicenciaForm({ mode, licencia }: LicenciaFormProps) {
  const t = useTranslations("licencias");
  const tCampos = useTranslations("licencias.campos");
  const tEstado = useTranslations("licencias.estado");
  const tValidacion = useTranslations("licencias.validacion");
  const tErrores = useTranslations("licencias.errores");
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const form = useForm<LicenciaFormValues>({
    resolver: zodResolver(LicenciaInputSchema, undefined, {
      // Validamos los strings tal cual los introduce el usuario, sin pasar
      // por el `transform` del schema (que devolvería null para los vacíos
      // y rompería los inputs controlados). El parse final con transform
      // corre server-side.
      raw: true,
    }),
    defaultValues: defaultsFromLicencia(licencia),
  });

  function applyServerErrors(fieldErrors: Record<string, string[]> | undefined): boolean {
    if (!fieldErrors) return false;
    let applied = false;
    for (const [field, errors] of Object.entries(fieldErrors)) {
      const code = errors[0];
      if (!code) continue;
      form.setError(field as keyof LicenciaFormValues, {
        type: "server",
        message: tValidacion.has(code) ? tValidacion(code) : code,
      });
      applied = true;
    }
    return applied;
  }

  async function onSubmit(values: LicenciaFormValues) {
    setSubmitting(true);
    try {
      const formData = buildFormData(values);
      let result: ActionResult | ActionResult<{ id: string }>;
      if (mode === "create") {
        result = await crearLicencia(null, formData);
      } else if (licencia) {
        result = await actualizarLicencia(licencia.id, null, formData);
      } else {
        throw new Error("LicenciaForm en modo edit sin licencia");
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
        router.push(`/licencias/${(result.data as { id: string }).id}`);
      } else {
        router.refresh();
      }
    } catch (err) {
      // No interceptamos NEXT_REDIRECT (es un Error con `digest` interno);
      // si llega aquí es un error inesperado.
      if (err && typeof err === "object" && "digest" in err) {
        throw err;
      }
      console.error("licencia_form_unexpected_error", err);
      toast.error(tErrores("desconocido"));
    } finally {
      setSubmitting(false);
    }
  }

  function onInvalid(errors: FieldErrors<LicenciaFormValues>) {
    // Tradúcimos los códigos producidos por Zod (mensajes son claves i18n).
    for (const [field, fieldError] of Object.entries(errors)) {
      const message = fieldError?.message;
      if (typeof message === "string" && tValidacion.has(message)) {
        form.setError(field as keyof LicenciaFormValues, {
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
            name="numero"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("numero")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={80} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="tipo"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("tipo")}</FormLabel>
                <FormControl>
                  <Input autoComplete="off" maxLength={80} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="fechaExpedicion"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("fechaExpedicion")}</FormLabel>
                <FormControl>
                  <Input type="date" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="fechaCaducidad"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("fechaCaducidad")}</FormLabel>
                <FormControl>
                  <Input type="date" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="comunidadAutonoma"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("comunidadAutonoma")}</FormLabel>
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
                    {ESTADOS_LICENCIA.map((estado) => (
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
            onClick={() => router.push("/licencias")}
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
