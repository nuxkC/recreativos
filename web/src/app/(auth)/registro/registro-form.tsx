"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

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
import { registrarEmpresa } from "@/lib/registro/actions";
import { PASSWORD_MIN_LENGTH } from "@/lib/registro/schemas";
import { esEmailValido } from "@/lib/shared/validators";

function buildSchema(t: ReturnType<typeof useTranslations<"registro.validacion">>) {
  return z.object({
    nombreEmpresa: z
      .string()
      .trim()
      .min(1, { message: t("nombreEmpresaRequerido") })
      .max(150, { message: t("nombreEmpresaMuyLargo") }),
    nombreCompleto: z
      .string()
      .trim()
      .min(1, { message: t("nombreCompletoRequerido") })
      .max(150, { message: t("nombreCompletoMuyLargo") }),
    email: z
      .string()
      .min(1, { message: t("emailRequerido") })
      .refine((v) => esEmailValido(v), { message: t("emailInvalido") }),
    password: z
      .string()
      .min(1, { message: t("passwordRequerida") })
      .min(PASSWORD_MIN_LENGTH, { message: t("passwordMin", { min: PASSWORD_MIN_LENGTH }) }),
  });
}

type RegistroValues = z.infer<ReturnType<typeof buildSchema>>;

// Mapea los códigos de error de campo de la Server Action a claves i18n.
const CAMPOS: Record<string, keyof RegistroValues> = {
  email: "email",
  nombreEmpresa: "nombreEmpresa",
  nombreCompleto: "nombreCompleto",
  password: "password",
};

export function RegistroForm() {
  const t = useTranslations("registro");
  const tValidacion = useTranslations("registro.validacion");
  const tErrores = useTranslations("registro.errores");
  const [submitting, setSubmitting] = useState(false);

  const form = useForm<RegistroValues>({
    resolver: zodResolver(buildSchema(tValidacion)),
    defaultValues: { nombreEmpresa: "", nombreCompleto: "", email: "", password: "" },
  });

  async function onSubmit(values: RegistroValues) {
    setSubmitting(true);
    try {
      const fd = new FormData();
      fd.set("nombreEmpresa", values.nombreEmpresa.trim());
      fd.set("nombreCompleto", values.nombreCompleto.trim());
      fd.set("email", values.email.trim());
      fd.set("password", values.password);

      // En el camino feliz la Server Action redirige y no retorna.
      const result = await registrarEmpresa(null, fd);

      // Si hubo redirección, `result` es undefined: la navegación ya está en curso.
      if (!result?.error) {
        return;
      }

      const fieldErrors = result.error.fieldErrors;
      if (fieldErrors) {
        for (const [field, codes] of Object.entries(fieldErrors)) {
          const code = codes[0];
          const campo = CAMPOS[field];
          if (!code || !campo) continue;
          form.setError(campo, {
            message: tValidacion.has(code) ? tValidacion(code) : code,
          });
        }
        if (Object.keys(fieldErrors).length === 0) {
          toast.error(tErrores("registroFallido"));
        }
        return;
      }

      const code = result.error.code;
      toast.error(tErrores.has(code) ? tErrores(code) : tErrores("desconocido"));
    } catch (err) {
      // redirect() lanza una excepción de control de Next: re-lanzar.
      if (err && typeof err === "object" && "digest" in err) {
        throw err;
      }
      console.error("registro_unexpected_error", err);
      toast.error(tErrores("desconocido"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
        <FormField
          control={form.control}
          name="nombreEmpresa"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t("campos.nombreEmpresa")}</FormLabel>
              <FormControl>
                <Input
                  type="text"
                  autoComplete="organization"
                  placeholder={t("placeholders.nombreEmpresa")}
                  {...field}
                />
              </FormControl>
              <FormDescription>{t("ayuda.trial")}</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="nombreCompleto"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t("campos.nombreCompleto")}</FormLabel>
              <FormControl>
                <Input
                  type="text"
                  autoComplete="name"
                  placeholder={t("placeholders.nombreCompleto")}
                  {...field}
                />
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
              <FormLabel>{t("campos.email")}</FormLabel>
              <FormControl>
                <Input
                  type="email"
                  autoComplete="email"
                  placeholder={t("placeholders.email")}
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="password"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t("campos.password")}</FormLabel>
              <FormControl>
                <Input type="password" autoComplete="new-password" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? t("accion.enviando") : t("accion.crear")}
        </Button>
        <p className="text-center text-sm text-muted-foreground">
          {t("tengoCuenta")}{" "}
          <Link href="/login" className="font-medium text-foreground underline underline-offset-4">
            {t("accion.iniciarSesion")}
          </Link>
        </p>
      </form>
    </Form>
  );
}
