"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

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
import { createClient } from "@/lib/supabase/client";

const PASSWORD_MIN_LENGTH = 6;

function buildSchema(t: ReturnType<typeof useTranslations<"validation">>) {
  return z.object({
    email: z
      .string()
      .min(1, { message: t("emailRequired") })
      .email({ message: t("emailInvalid") }),
    password: z
      .string()
      .min(1, { message: t("passwordRequired") })
      .min(PASSWORD_MIN_LENGTH, {
        message: t("passwordMin", { min: PASSWORD_MIN_LENGTH }),
      }),
  });
}

type LoginValues = z.infer<ReturnType<typeof buildSchema>>;

export function LoginForm() {
  const t = useTranslations("auth.login");
  const tValidation = useTranslations("validation");
  const router = useRouter();
  const searchParams = useSearchParams();
  const [submitting, setSubmitting] = useState(false);

  const form = useForm<LoginValues>({
    resolver: zodResolver(buildSchema(tValidation)),
    defaultValues: { email: "", password: "" },
  });

  async function onSubmit(values: LoginValues) {
    setSubmitting(true);
    try {
      const supabase = createClient();
      const { error } = await supabase.auth.signInWithPassword(values);

      if (error) {
        const message =
          error.status === 400 ? t("errorInvalid") : t("errorGeneric");
        toast.error(message);
        return;
      }

      const next = searchParams.get("next") ?? "/";
      router.replace(next);
      router.refresh();
    } catch (err) {
      console.error("login_unexpected_error", err);
      toast.error(t("errorGeneric"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
        <FormField
          control={form.control}
          name="email"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t("email")}</FormLabel>
              <FormControl>
                <Input
                  type="email"
                  autoComplete="email"
                  placeholder={t("emailPlaceholder")}
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
              <FormLabel>{t("password")}</FormLabel>
              <FormControl>
                <Input type="password" autoComplete="current-password" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? t("submitting") : t("submit")}
        </Button>
        <p className="text-center text-sm text-muted-foreground">
          {t("noTengoCuenta")}{" "}
          <Link
            href="/registro"
            className="font-medium text-foreground underline underline-offset-4"
          >
            {t("crearCuenta")}
          </Link>
        </p>
      </form>
    </Form>
  );
}
