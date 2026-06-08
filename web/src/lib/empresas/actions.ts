"use server";

import { cookies } from "next/headers";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";

import { createClient } from "@/lib/supabase/server";

import { EMPRESA_COOKIE_NAME, empresaCookieOptions } from "./cookie";

const SeleccionInputSchema = z.object({
  empresaId: z.string().uuid(),
  next: z
    .string()
    .startsWith("/")
    .regex(/^[^\\\s]+$/, { message: "ruta no permitida" })
    .optional(),
});

/**
 * Marca una empresa como activa para el usuario actual.
 *
 * Verifica server-side que el usuario tenga una membresía activa con esa
 * empresa antes de persistir la cookie. RLS por sí sola es suficiente para
 * filtrar datos, pero rechazar aquí da un error claro y evita estados raros.
 */
export async function seleccionarEmpresa(formData: FormData): Promise<void> {
  const parsed = SeleccionInputSchema.safeParse({
    empresaId: formData.get("empresaId"),
    next: formData.get("next") ?? undefined,
  });
  if (!parsed.success) {
    throw new Error("Solicitud inválida al seleccionar empresa");
  }
  const { empresaId, next } = parsed.data;

  const supabase = createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) {
    redirect("/login");
  }

  const { count, error } = await supabase
    .from("empresa_usuario")
    .select("empresa_id", { head: true, count: "exact" })
    .eq("empresa_id", empresaId)
    .eq("usuario_id", user.id)
    .eq("activo", true);

  if (error) {
    throw new Error(`No se pudo verificar la membresía: ${error.message}`);
  }
  if (!count || count === 0) {
    throw new Error("El usuario no pertenece a esta empresa");
  }

  cookies().set(EMPRESA_COOKIE_NAME, empresaId, empresaCookieOptions);

  revalidatePath("/", "layout");
  redirect(next ?? "/dashboard");
}

/**
 * Borra la selección y manda al usuario al selector. Útil desde el
 * desplegable del header o desde un futuro "Cambiar empresa" en Ajustes.
 */
export async function limpiarEmpresaActiva(): Promise<void> {
  cookies().delete(EMPRESA_COOKIE_NAME);
  revalidatePath("/", "layout");
  redirect("/seleccionar-empresa");
}
