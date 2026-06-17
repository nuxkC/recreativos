"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { useForm, type FieldErrors } from "react-hook-form";
import { toast } from "sonner";

import { FieldDate } from "@/components/common/date-field";
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
import { actualizarCalendarioLocal } from "@/lib/locales/actions";
import { CalendarioLocalInputSchema } from "@/lib/locales/schemas";
import type { OperarioResumen } from "@/lib/operarios/queries";

// Radix Select reserva "" para el placeholder; "Sin asignar" usa un centinela
// que se traduce a "" (→ null en el schema) antes de enviar.
const SIN_OPERARIO = "__sin_operario__";

type CalendarioFormValues = {
  localId: string;
  cadenciaSemanas: string;
  fechaInicioRecaudacion: string;
  operarioId: string;
};

interface CalendarioLocalFormProps {
  localId: string;
  cadenciaSemanas: number | null;
  fechaInicioRecaudacion: string | null;
  operarioId: string | null;
  operarios: OperarioResumen[];
}

export function CalendarioLocalForm({
  localId,
  cadenciaSemanas,
  fechaInicioRecaudacion,
  operarioId,
  operarios,
}: CalendarioLocalFormProps) {
  const t = useTranslations("locales.calendario");
  const tValidacion = useTranslations("locales.validacion");
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  const [cadenciaLibre, setCadenciaLibre] = useState(
    cadenciaSemanas != null && ![1, 2, 4].includes(cadenciaSemanas),
  );

  const form = useForm<CalendarioFormValues>({
    resolver: zodResolver(CalendarioLocalInputSchema, undefined, { raw: true }),
    defaultValues: {
      localId,
      cadenciaSemanas: cadenciaSemanas != null ? String(cadenciaSemanas) : "",
      fechaInicioRecaudacion: fechaInicioRecaudacion ?? "",
      operarioId: operarioId ?? "",
    },
  });

  function applyServerErrors(fieldErrors: Record<string, string[]> | undefined): boolean {
    if (!fieldErrors) return false;
    let applied = false;
    for (const [field, errors] of Object.entries(fieldErrors)) {
      const code = errors[0];
      if (!code) continue;
      form.setError(field as keyof CalendarioFormValues, {
        type: "server",
        message: tValidacion.has(code) ? tValidacion(code) : code,
      });
      applied = true;
    }
    return applied;
  }

  async function onSubmit(values: CalendarioFormValues) {
    setSubmitting(true);
    try {
      const fd = new FormData();
      Object.entries(values).forEach(([k, v]) => fd.set(k, v));
      const result = await actualizarCalendarioLocal(null, fd);
      if (!result.ok) {
        const applied = applyServerErrors(result.error.fieldErrors);
        if (!applied) toast.error(t("guardarError"));
        return;
      }
      toast.success(t("guardadoOk"));
      router.refresh();
    } catch (err) {
      if (err && typeof err === "object" && "digest" in err) throw err;
      console.error("calendario_local_form_unexpected_error", err);
      toast.error(t("guardarError"));
    } finally {
      setSubmitting(false);
    }
  }

  function onInvalid(errors: FieldErrors<CalendarioFormValues>) {
    for (const [field, fieldError] of Object.entries(errors)) {
      const message = fieldError?.message;
      if (typeof message === "string" && tValidacion.has(message)) {
        form.setError(field as keyof CalendarioFormValues, {
          type: "validate",
          message: tValidacion(message),
        });
      }
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit, onInvalid)} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            control={form.control}
            name="cadenciaSemanas"
            render={({ field }) => {
              const esPreset = ["1", "2", "4"].includes(field.value);
              const enLibre = cadenciaLibre || (field.value !== "" && !esPreset);
              return (
                <FormItem>
                  <FormLabel>{t("cadencia")}</FormLabel>
                  <Select
                    value={enLibre ? "otro" : field.value || undefined}
                    onValueChange={(v) => {
                      if (v === "otro") {
                        setCadenciaLibre(true);
                        field.onChange("");
                      } else {
                        setCadenciaLibre(false);
                        field.onChange(v);
                      }
                    }}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("sinPlanificar")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="1">{t("presetSemanal")}</SelectItem>
                      <SelectItem value="2">{t("presetQuincenal")}</SelectItem>
                      <SelectItem value="4">{t("presetMensual")}</SelectItem>
                      <SelectItem value="otro">{t("presetOtro")}</SelectItem>
                    </SelectContent>
                  </Select>
                  {enLibre ? (
                    <FormControl>
                      <Input
                        type="number"
                        min={1}
                        step={1}
                        inputMode="numeric"
                        placeholder={t("cadenciaLibre")}
                        value={field.value}
                        onChange={(e) => field.onChange(e.target.value)}
                      />
                    </FormControl>
                  ) : null}
                  <FormMessage />
                </FormItem>
              );
            }}
          />
          <FormField
            control={form.control}
            name="fechaInicioRecaudacion"
            render={({ field, fieldState }) => (
              <FieldDate
                label={t("fechaInicio")}
                value={field.value}
                onChange={field.onChange}
                error={fieldState.error?.message}
                density="compact"
              />
            )}
          />
          <FormField
            control={form.control}
            name="operarioId"
            render={({ field }) => (
              <FormItem className="sm:col-span-2">
                <FormLabel>{t("operario")}</FormLabel>
                <Select
                  value={field.value ? field.value : SIN_OPERARIO}
                  onValueChange={(v) => field.onChange(v === SIN_OPERARIO ? "" : v)}
                >
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    <SelectItem value={SIN_OPERARIO}>{t("sinOperario")}</SelectItem>
                    {operarios.map((o) => (
                      <SelectItem key={o.id} value={o.id}>
                        {o.nombre}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
        <div className="flex justify-end">
          <Button type="submit" disabled={submitting}>
            {submitting ? (
              <>
                <Loader2 className="size-4 animate-spin" aria-hidden />
                <span>{t("guardando")}</span>
              </>
            ) : (
              <span>{t("guardar")}</span>
            )}
          </Button>
        </div>
      </form>
    </Form>
  );
}
