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
import { actualizarAjustesEmpresa } from "@/lib/ajustes/actions";
import { EmpresaAjustesSchema } from "@/lib/ajustes/schemas";
import {
  REDONDEO_RECAUDACION_OPCIONES,
  ZONAS_HORARIAS,
  type EmpresaAjustes,
  type ZonaHoraria,
} from "@/lib/ajustes/types";

type AjustesFormValues = {
  nombre: string;
  cif: string;
  direccion: string;
  telefono: string;
  email: string;
  zonaHoraria: ZonaHoraria;
  ticketCabecera: string;
  ticketPie: string;
  redondeoRecaudacion: number;
};

interface AjustesFormProps {
  empresa: EmpresaAjustes;
}

function defaults(empresa: EmpresaAjustes): AjustesFormValues {
  const tz = (ZONAS_HORARIAS as readonly string[]).includes(empresa.zonaHoraria)
    ? (empresa.zonaHoraria as ZonaHoraria)
    : "Europe/Madrid";
  return {
    nombre: empresa.nombre,
    cif: empresa.cif ?? "",
    direccion: empresa.direccion ?? "",
    telefono: empresa.telefono ?? "",
    email: empresa.email ?? "",
    zonaHoraria: tz,
    ticketCabecera: empresa.ticketCabecera ?? "",
    ticketPie: empresa.ticketPie ?? "",
    redondeoRecaudacion: empresa.redondeoRecaudacion,
  };
}

function buildFormData(values: AjustesFormValues): FormData {
  const fd = new FormData();
  // String(v): los valores no-texto (p.ej. redondeoRecaudacion: number) deben
  // ir como string en el FormData; el schema los vuelve a coercer.
  Object.entries(values).forEach(([k, v]) => fd.set(k, String(v)));
  return fd;
}

export function AjustesForm({ empresa }: AjustesFormProps) {
  const t = useTranslations("ajustes");
  const tCampos = useTranslations("ajustes.campos");
  const tValidacion = useTranslations("ajustes.validacion");
  const tErrores = useTranslations("ajustes.errores");
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const form = useForm<AjustesFormValues>({
    resolver: zodResolver(EmpresaAjustesSchema, undefined, { raw: true }),
    defaultValues: defaults(empresa),
  });

  function applyServerErrors(fieldErrors: Record<string, string[]> | undefined): boolean {
    if (!fieldErrors) return false;
    let applied = false;
    for (const [field, codes] of Object.entries(fieldErrors)) {
      const code = codes[0];
      if (!code) continue;
      form.setError(field as keyof AjustesFormValues, {
        type: "server",
        message: tValidacion.has(code) ? tValidacion(code) : code,
      });
      applied = true;
    }
    return applied;
  }

  async function onSubmit(values: AjustesFormValues) {
    setSubmitting(true);
    try {
      const result = await actualizarAjustesEmpresa(null, buildFormData(values));
      if (!result.ok) {
        const applied = applyServerErrors(result.error.fieldErrors);
        if (!applied) {
          const code = result.error.code;
          toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
        }
        return;
      }
      toast.success(t("guardadoOk"));
      router.refresh();
    } catch (err) {
      if (err && typeof err === "object" && "digest" in err) throw err;
      console.error("ajustes_form_unexpected_error", err);
      toast.error(tErrores("desconocido"));
    } finally {
      setSubmitting(false);
    }
  }

  function onInvalid(errors: FieldErrors<AjustesFormValues>) {
    for (const [field, fieldError] of Object.entries(errors)) {
      const message = fieldError?.message;
      if (typeof message === "string" && tValidacion.has(message)) {
        form.setError(field as keyof AjustesFormValues, {
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
              <FormItem className="sm:col-span-2">
                <FormLabel>{tCampos("nombre")}</FormLabel>
                <FormControl>
                  <Input maxLength={150} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="cif"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("cif")}</FormLabel>
                <FormControl>
                  <Input maxLength={20} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="zonaHoraria"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("zonaHoraria")}</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {ZONAS_HORARIAS.map((tz) => (
                      <SelectItem key={tz} value={tz}>
                        {tz}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormDescription>{tCampos("zonaHorariaAyuda")}</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="direccion"
            render={({ field }) => (
              <FormItem className="sm:col-span-2">
                <FormLabel>{tCampos("direccion")}</FormLabel>
                <FormControl>
                  <Input maxLength={300} {...field} />
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
                  <Input type="tel" maxLength={30} {...field} />
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
                  <Input type="email" maxLength={254} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <div className="space-y-4 rounded-md border p-4">
          <div>
            <h2 className="text-base font-medium">{t("seccion.ticket")}</h2>
            <p className="text-sm text-muted-foreground">{t("seccion.ticketDescripcion")}</p>
          </div>
          <FormField
            control={form.control}
            name="ticketCabecera"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("ticketCabecera")}</FormLabel>
                <FormControl>
                  <Textarea rows={3} maxLength={500} {...field} />
                </FormControl>
                <FormDescription>{tCampos("ticketCabeceraAyuda")}</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="ticketPie"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{tCampos("ticketPie")}</FormLabel>
                <FormControl>
                  <Textarea rows={3} maxLength={500} {...field} />
                </FormControl>
                <FormDescription>{tCampos("ticketPieAyuda")}</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <div className="space-y-4 rounded-md border p-4">
          <div>
            <h2 className="text-base font-medium">{t("seccion.recaudacion")}</h2>
            <p className="text-sm text-muted-foreground">
              {t("seccion.recaudacionDescripcion")}
            </p>
          </div>
          <FormField
            control={form.control}
            name="redondeoRecaudacion"
            render={({ field }) => (
              <FormItem className="sm:max-w-xs">
                <FormLabel>{tCampos("redondeoRecaudacion")}</FormLabel>
                <Select
                  value={String(field.value)}
                  onValueChange={(v) => field.onChange(Number(v))}
                >
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {REDONDEO_RECAUDACION_OPCIONES.map((opt) => (
                      <SelectItem key={opt} value={String(opt)}>
                        {opt === 0
                          ? tCampos("redondeoDesactivado")
                          : tCampos("redondeoUnidad", { unidad: opt })}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormDescription>{tCampos("redondeoRecaudacionAyuda")}</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <div className="flex items-center justify-end gap-2">
          <Button type="submit" disabled={submitting}>
            {submitting ? (
              <>
                <Loader2 className="size-4 animate-spin" aria-hidden />
                <span>{t("accion.guardando")}</span>
              </>
            ) : (
              <span>{t("accion.guardar")}</span>
            )}
          </Button>
        </div>
      </form>
    </Form>
  );
}
