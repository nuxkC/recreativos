"use server";

import { FunctionsHttpError } from "@supabase/supabase-js";
import { cookies } from "next/headers";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import type { ZodError } from "zod";

import { EMPRESA_COOKIE_NAME, empresaCookieOptions } from "@/lib/empresas/cookie";
import { createClient } from "@/lib/supabase/server";

import { RegistroInputSchema } from "./schemas";

export type RegistroResult = {
  ok: false;
  error: {
    code: string;
    fieldErrors?: Record<string, string[]>;
  };
};

interface RegistrarEmpresaResponse {
  empresa_id: string;
  estado_suscripcion: string;
  trial_fin: string;
}

function fieldErrorsFromZod(err: ZodError): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  for (const issue of err.issues) {
    if (issue.path.length === 0) continue;
    const key = String(issue.path[0]);
    (out[key] ??= []).push(issue.message);
  }
  return out;
}

async function codigoDeError(error: unknown): Promise<string | null> {
  if (error instanceof FunctionsHttpError) {
    try {
      const body = await error.context.json();
      const code = body?.error?.code;
      return typeof code === "string" ? code : null;
    } catch {
      return null;
    }
  }
  return null;
}

/**
 * Registra una empresa nueva (trial) y a su primer usuario como `owner`.
 *
 * Flujo: valida → invoca la Edge Function `registrar-empresa` (alta atómica de
 * empresa + perfil + membresía) → inicia sesión con las credenciales → fija la
 * empresa recién creada como activa → entra al dashboard.
 *
 * Devuelve un `RegistroResult` SOLO en caso de error (en el camino feliz hace
 * `redirect`, que interrumpe la ejecución).
 */
export async function registrarEmpresa(
  _prevState: RegistroResult | null,
  formData: FormData,
): Promise<RegistroResult> {
  const parsed = RegistroInputSchema.safeParse({
    nombreEmpresa: formData.get("nombreEmpresa") ?? "",
    nombreCompleto: formData.get("nombreCompleto") ?? "",
    email: formData.get("email") ?? "",
    password: formData.get("password") ?? "",
  });
  if (!parsed.success) {
    return {
      ok: false,
      error: { code: "validacion", fieldErrors: fieldErrorsFromZod(parsed.error) },
    };
  }

  const { nombreEmpresa, nombreCompleto, email, password } = parsed.data;
  const supabase = createClient();

  const { data, error } = await supabase.functions.invoke<RegistrarEmpresaResponse>(
    "registrar-empresa",
    {
      body: {
        nombre_empresa: nombreEmpresa,
        nombre_completo: nombreCompleto,
        email,
        password,
      },
    },
  );

  if (error) {
    const code = await codigoDeError(error);
    if (code === "conflict") {
      return { ok: false, error: { code: "emailExiste", fieldErrors: { email: ["emailExiste"] } } };
    }
    if (code === "validation_error") {
      return { ok: false, error: { code: "validacion" } };
    }
    return { ok: false, error: { code: "registroFallido" } };
  }

  const empresaId = data?.empresa_id ?? null;

  // Iniciar sesión: deja la cookie de sesión lista para el dashboard.
  const { error: signInError } = await supabase.auth.signInWithPassword({ email, password });
  if (signInError) {
    // La cuenta se creó pero el login automático falló: que entre desde login.
    redirect("/login?registrado=1");
  }

  if (empresaId) {
    cookies().set(EMPRESA_COOKIE_NAME, empresaId, empresaCookieOptions);
  }

  revalidatePath("/", "layout");
  redirect("/dashboard");
}
